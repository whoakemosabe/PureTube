package com.hhst.youtubelite.tvlink

import java.io.BufferedReader
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.security.GeneralSecurityException

/** Keys this device shares with the devices it paired with. */
internal interface TvLinkStore {
    data class Peer(val id: String, val name: String)

    fun selfId(): String
    fun secret(peerId: String): ByteArray?
    fun save(peerId: String, name: String, secret: ByteArray)
    fun remove(peerId: String)
    fun peers(): List<Peer>
}

internal class MemoryTvLinkStore(private val id: String) : TvLinkStore {
    private val secrets = linkedMapOf<String, Pair<String, ByteArray>>()
    override fun selfId() = id
    @Synchronized override fun secret(peerId: String) = secrets[peerId]?.second
    @Synchronized override fun save(peerId: String, name: String, secret: ByteArray) { secrets[peerId] = name to secret }
    @Synchronized override fun remove(peerId: String) { secrets.remove(peerId) }
    @Synchronized override fun peers() = secrets.map { (id, value) -> TvLinkStore.Peer(id, value.first) }
}

/**
 * Newline-framed text over a socket's streams. Lines are capped so a peer can't make the
 * reader buffer without limit.
 */
internal class LineChannel(input: InputStream, private val output: OutputStream, private val onClose: () -> Unit = {}) : Closeable {
    private val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8))

    /** Null at end of stream. */
    fun readLine(): String? {
        val out = StringBuilder()
        while (true) {
            val char = reader.read()
            if (char < 0) return if (out.isEmpty()) null else throw IOException("Truncated line")
            if (char == '\n'.code) return out.toString()
            if (out.length >= MAX_LINE) throw IOException("Line too long")
            out.append(char.toChar())
        }
    }

    @Synchronized fun writeLine(line: String) {
        output.write((line + "\n").toByteArray(Charsets.UTF_8))
        output.flush()
    }

    override fun close() {
        runCatching { onClose() }
    }

    companion object {
        const val MAX_LINE = 64 * 1024
    }
}

/** A connection after the handshake: every message is sealed with the session keys. */
internal class SecureChannel(private val lines: LineChannel, private val cipher: SessionCipher) : Closeable {
    fun send(message: TvLinkMessage) {
        lines.writeLine(Hex.encode(cipher.seal(TvLinkMessage.encode(message).toByteArray(Charsets.UTF_8))))
    }

    /** Null at end of stream; throws when a frame fails to open (the caller closes). */
    fun receive(): TvLinkMessage? {
        val line = lines.readLine() ?: return null
        val sealed = Hex.decodeOrNull(line) ?: throw IOException("Frame is not hex")
        val plain = try {
            cipher.open(sealed)
        } catch (error: GeneralSecurityException) {
            throw IOException("Frame failed to open", error)
        }
        return TvLinkMessage.decodeOrNull(String(plain, Charsets.UTF_8)) ?: throw IOException("Unknown message")
    }

    override fun close() = lines.close()
}

/**
 * The code the TV shows, and how many wrong guesses it has taken. Three wrong codes
 * replace it, so guessing stays a live, slow, visible attempt.
 */
internal class PairingCode(private val generate: () -> String = ::randomCode) {
    @Volatile var current: String = generate()
        private set
    private var wrongAttempts = 0
    var onChanged: ((String) -> Unit)? = null

    @Synchronized fun wrongGuess() {
        wrongAttempts++
        if (wrongAttempts >= MAX_WRONG) rotate()
    }

    @Synchronized fun rotate() {
        wrongAttempts = 0
        current = generate()
        onChanged?.invoke(current)
    }

    companion object {
        const val MAX_WRONG = 3

        fun randomCode(): String {
            val bytes = LinkCrypto.randomBytes(4)
            val value = ((bytes[0].toLong() and 0xff) shl 24) or ((bytes[1].toLong() and 0xff) shl 16) or
                ((bytes[2].toLong() and 0xff) shl 8) or (bytes[3].toLong() and 0xff)
            return (value % 1_000_000).toString().padStart(6, '0')
        }
    }
}

/** TV side of a new connection. */
internal class TvHandshake(
    private val store: TvLinkStore,
    private val tvName: () -> String,
    private val code: PairingCode,
) {
    class Accepted(val channel: SecureChannel, val phoneId: String, val phoneName: String, val newlyPaired: Boolean)

    /** Null when the phone was refused or isn't paired; [lines] is then closed. */
    fun run(lines: LineChannel): Accepted? {
        val accepted = try {
            negotiate(lines)
        } catch (error: IOException) {
            null
        } catch (error: GeneralSecurityException) {
            null
        }
        if (accepted == null) lines.close()
        return accepted
    }

    private fun negotiate(lines: LineChannel): Accepted? {
        val tvId = store.selfId()
        val tvNonce = LinkCrypto.randomBytes(NONCE_BYTES)
        lines.writeLine(TvLinkMessage.encode(TvLinkMessage.Banner(TvLinkMessage.VERSION, tvId, tvName().take(MAX_NAME), Hex.encode(tvNonce))))
        val hello = TvLinkMessage.decodeOrNull(lines.readLine() ?: return null) as? TvLinkMessage.Hello
            ?: return refuse(lines, TvLinkMessage.REFUSED_PROTOCOL)
        if (hello.version != TvLinkMessage.VERSION) return refuse(lines, TvLinkMessage.REFUSED_VERSION)
        val phoneNonce = Hex.decodeOrNull(hello.nonce)?.takeIf { it.size == NONCE_BYTES }
            ?: return refuse(lines, TvLinkMessage.REFUSED_PROTOCOL)
        val pairShare = hello.pairShare
        val secret: ByteArray
        val newlyPaired: Boolean
        if (pairShare == null) {
            secret = store.secret(hello.phoneId) ?: run {
                lines.writeLine(TvLinkMessage.encode(TvLinkMessage.NotPaired))
                return null
            }
            lines.writeLine(TvLinkMessage.encode(TvLinkMessage.Welcome()))
            newlyPaired = false
        } else {
            val shownCode = code.current
            val spake = Spake2(isPhone = false, code = shownCode, phoneId = hello.phoneId, tvId = tvId)
            val outcome = Hex.decodeOrNull(pairShare)?.let(spake::finish) ?: return refuse(lines, TvLinkMessage.REFUSED_PROTOCOL)
            lines.writeLine(TvLinkMessage.encode(TvLinkMessage.Welcome(Hex.encode(spake.share))))
            val confirm = TvLinkMessage.decodeOrNull(lines.readLine() ?: return null) as? TvLinkMessage.Confirm
                ?: return refuse(lines, TvLinkMessage.REFUSED_PROTOCOL)
            val mac = Hex.decodeOrNull(confirm.mac)
            if (mac == null || !LinkCrypto.constantTimeEquals(mac, outcome.peerConfirm)) {
                code.wrongGuess()
                val reason = if (code.current != shownCode) TvLinkMessage.REFUSED_NEW_CODE else TvLinkMessage.REFUSED_WRONG_CODE
                return refuse(lines, reason)
            }
            lines.writeLine(TvLinkMessage.encode(TvLinkMessage.Confirm(Hex.encode(outcome.myConfirm))))
            store.save(hello.phoneId, hello.phoneName.take(MAX_NAME), outcome.sharedKey)
            // A used code is spent: the next phone gets a new one.
            code.rotate()
            secret = outcome.sharedKey
            newlyPaired = true
        }
        val channel = SecureChannel(lines, SessionCipher(secret, phoneNonce, tvNonce, isPhone = false))
        // The phone's first sealed message proves it holds the key; nothing it sends counts before that.
        if (channel.receive() != TvLinkMessage.Ping) return null
        return Accepted(channel, hello.phoneId, hello.phoneName.take(MAX_NAME), newlyPaired)
    }

    private fun refuse(lines: LineChannel, reason: String): Accepted? {
        runCatching { lines.writeLine(TvLinkMessage.encode(TvLinkMessage.Refused(reason))) }
        return null
    }
}

/** Phone side of a new connection. */
internal class PhoneHandshake(private val store: TvLinkStore, private val phoneName: () -> String) {
    sealed interface Result {
        class Connected(val channel: SecureChannel, val tvId: String, val tvName: String) : Result
        /** This phone has no key for the TV: ask for the code on the TV and try again with it. */
        data class NeedsCode(val tvId: String, val tvName: String) : Result
        data class Refused(val reason: String) : Result
        data class Failed(val reason: String) : Result
    }

    /** With [code] the phone pairs; without it, it uses the key stored for the TV it reached. */
    fun run(lines: LineChannel, code: String? = null): Result {
        val result = try {
            negotiate(lines, code)
        } catch (error: IOException) {
            Result.Failed(error.javaClass.simpleName)
        } catch (error: GeneralSecurityException) {
            Result.Failed(error.javaClass.simpleName)
        }
        if (result !is Result.Connected) lines.close()
        return result
    }

    private fun negotiate(lines: LineChannel, code: String?): Result {
        val banner = TvLinkMessage.decodeOrNull(lines.readLine() ?: return Result.Failed("closed")) as? TvLinkMessage.Banner
            ?: return Result.Failed("protocol")
        if (banner.version != TvLinkMessage.VERSION) return Result.Refused(TvLinkMessage.REFUSED_VERSION)
        val tvNonce = Hex.decodeOrNull(banner.nonce)?.takeIf { it.size == NONCE_BYTES } ?: return Result.Failed("protocol")
        val stored = store.secret(banner.tvId)
        if (code == null && stored == null) return Result.NeedsCode(banner.tvId, banner.tvName)
        val phoneId = store.selfId()
        val phoneNonce = LinkCrypto.randomBytes(NONCE_BYTES)
        val spake = code?.let { Spake2(isPhone = true, code = it, phoneId = phoneId, tvId = banner.tvId) }
        lines.writeLine(TvLinkMessage.encode(TvLinkMessage.Hello(
            TvLinkMessage.VERSION, phoneId, phoneName().take(MAX_NAME), Hex.encode(phoneNonce), spake?.share?.let(Hex::encode),
        )))
        val reply = TvLinkMessage.decodeOrNull(lines.readLine() ?: return Result.Failed("closed"))
        val secret = when {
            reply is TvLinkMessage.Refused -> return Result.Refused(reply.reason)
            reply == TvLinkMessage.NotPaired -> {
                // The TV forgot this phone (it was removed there): drop the dead key too.
                store.remove(banner.tvId)
                return Result.NeedsCode(banner.tvId, banner.tvName)
            }
            reply !is TvLinkMessage.Welcome -> return Result.Failed("protocol")
            spake == null -> stored ?: return Result.Failed("protocol")
            else -> {
                val outcome = reply.pairShare?.let(Hex::decodeOrNull)?.let(spake::finish) ?: return Result.Failed("protocol")
                lines.writeLine(TvLinkMessage.encode(TvLinkMessage.Confirm(Hex.encode(outcome.myConfirm))))
                when (val answer = TvLinkMessage.decodeOrNull(lines.readLine() ?: return Result.Failed("closed"))) {
                    is TvLinkMessage.Refused -> return Result.Refused(answer.reason)
                    is TvLinkMessage.Confirm -> {
                        val mac = Hex.decodeOrNull(answer.mac)
                        // A TV that can't prove the same code is not the TV the user read it from.
                        if (mac == null || !LinkCrypto.constantTimeEquals(mac, outcome.peerConfirm)) return Result.Failed("confirm")
                    }
                    else -> return Result.Failed("protocol")
                }
                store.save(banner.tvId, banner.tvName.take(MAX_NAME), outcome.sharedKey)
                outcome.sharedKey
            }
        }
        val channel = SecureChannel(lines, SessionCipher(secret, phoneNonce, tvNonce, isPhone = true))
        channel.send(TvLinkMessage.Ping)
        return Result.Connected(channel, banner.tvId, banner.tvName.take(MAX_NAME))
    }
}

internal const val NONCE_BYTES = 16
internal const val MAX_NAME = 60
