package com.hhst.youtubelite.tvlink

import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal object Hex {
    private const val DIGITS = "0123456789abcdef"

    fun encode(bytes: ByteArray): String {
        val out = StringBuilder(bytes.size * 2)
        for (byte in bytes) {
            val value = byte.toInt() and 0xff
            out.append(DIGITS[value ushr 4]).append(DIGITS[value and 0x0f])
        }
        return out.toString()
    }

    /** Null for odd length or a non-hex character. */
    fun decodeOrNull(text: String): ByteArray? {
        if (text.length % 2 != 0) return null
        val out = ByteArray(text.length / 2)
        for (index in out.indices) {
            val high = Character.digit(text[index * 2], 16)
            val low = Character.digit(text[index * 2 + 1], 16)
            if (high < 0 || low < 0) return null
            out[index] = ((high shl 4) or low).toByte()
        }
        return out
    }

    fun decode(text: String): ByteArray = requireNotNull(decodeOrNull(text)) { "Not hex" }
}

internal object LinkCrypto {
    private val random = SecureRandom()

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also(random::nextBytes)

    fun hmac(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(data)

    /** HKDF-Expand (RFC 5869) with SHA-256; [prk] is already a uniform key. */
    fun expand(prk: ByteArray, info: String, length: Int): ByteArray {
        val out = ByteArray(length)
        var previous = ByteArray(0)
        var written = 0
        var counter = 1
        while (written < length) {
            previous = hmac(prk, previous + info.toByteArray() + byteArrayOf(counter.toByte()))
            val take = minOf(previous.size, length - written)
            previous.copyInto(out, written, 0, take)
            written += take
            counter++
        }
        return out
    }

    fun constantTimeEquals(left: ByteArray, right: ByteArray): Boolean = MessageDigest.isEqual(left, right)
}

/**
 * Encrypts one connection with keys made from the pairing secret and both sides' fresh
 * nonces, so an old connection can't be replayed. Each direction has its own key and a
 * counter nonce; TCP keeps order, so any dropped, reordered or altered frame fails to open
 * and the connection closes.
 */
internal class SessionCipher(secret: ByteArray, phoneNonce: ByteArray, tvNonce: ByteArray, isPhone: Boolean) {
    private val sendKey: ByteArray
    private val receiveKey: ByteArray
    private var sendCounter = 0L
    private var receiveCounter = 0L

    init {
        val prk = LinkCrypto.hmac(secret, phoneNonce + tvNonce)
        val phoneToTv = LinkCrypto.expand(prk, "puretube phone to tv", 32)
        val tvToPhone = LinkCrypto.expand(prk, "puretube tv to phone", 32)
        sendKey = if (isPhone) phoneToTv else tvToPhone
        receiveKey = if (isPhone) tvToPhone else phoneToTv
    }

    @Synchronized fun seal(plain: ByteArray): ByteArray = cipher(Cipher.ENCRYPT_MODE, sendKey, sendCounter++).doFinal(plain)

    /** Throws [GeneralSecurityException] when the frame was not sealed by the peer's next counter. */
    @Synchronized fun open(sealed: ByteArray): ByteArray = cipher(Cipher.DECRYPT_MODE, receiveKey, receiveCounter++).doFinal(sealed)

    private fun cipher(mode: Int, key: ByteArray, counter: Long): Cipher {
        if (counter == Long.MAX_VALUE) throw GeneralSecurityException("Counter exhausted")
        val nonce = ByteArray(12)
        for (index in 0 until 8) nonce[4 + index] = (counter ushr (8 * index)).toByte()
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        }
    }
}
