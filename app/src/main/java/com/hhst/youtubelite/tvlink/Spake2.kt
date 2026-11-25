package com.hhst.youtubelite.tvlink

import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * SPAKE2 (RFC 9382 shape) over P-256, used once per phone to pair it with a TV from the
 * short code the TV shows. Someone on the same Wi-Fi who sees the whole exchange learns
 * nothing that lets them test codes offline: each wrong guess costs one live attempt,
 * and the TV rotates its code after a few. Not constant time; it runs once per pairing.
 */
internal object P256 {
    val p = BigInteger("FFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFF", 16)
    val a: BigInteger = p - BigInteger.valueOf(3)
    val b = BigInteger("5AC635D8AA3A93E7B3EBBD55769886BC651D06B0CC53B0F63BCE3C3E27D2604B", 16)
    val n = BigInteger("FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551", 16)
    val G = Point(
        BigInteger("6B17D1F2E12C4247F8BCE6E563A440F277037D812DEB33A0F4A13945D898C296", 16),
        BigInteger("4FE342E2FE1A7F9B8EE7EB4A7C0F9E162BCE33576B315ECECBB6406837BF51F5", 16),
    )

    // RFC 9382 section 4 constants for P-256 (nobody knows their discrete logs).
    val M: Point = decodeCompressed("02886e2f97ace46e55ba9dd7242579f2993b64e16ef3dcab95afd497333d8fa12f")
    val N: Point = decodeCompressed("03d8bbd6c639c62937b04d997f38c3770719c629d7014d49a24b4f98baa1292b49")

    /** Affine point; [INFINITY] is the identity. */
    data class Point(val x: BigInteger, val y: BigInteger)

    private val INFINITY: Point? = null

    fun onCurve(point: Point): Boolean {
        if (point.x.signum() < 0 || point.x >= p || point.y.signum() < 0 || point.y >= p) return false
        val left = point.y.multiply(point.y).mod(p)
        val right = point.x.pow(3).add(a.multiply(point.x)).add(b).mod(p)
        return left == right
    }

    fun add(left: Point?, right: Point?): Point? {
        if (left == null) return right
        if (right == null) return left
        if (left.x == right.x) {
            if (left.y.add(right.y).mod(p).signum() == 0) return INFINITY
            return double(left)
        }
        val slope = right.y.subtract(left.y).multiply(right.x.subtract(left.x).modInverse(p)).mod(p)
        val x = slope.multiply(slope).subtract(left.x).subtract(right.x).mod(p)
        val y = slope.multiply(left.x.subtract(x)).subtract(left.y).mod(p)
        return Point(x, y)
    }

    private fun double(point: Point): Point? {
        if (point.y.signum() == 0) return INFINITY
        val slope = point.x.multiply(point.x).multiply(BigInteger.valueOf(3)).add(a)
            .multiply(point.y.shiftLeft(1).modInverse(p)).mod(p)
        val x = slope.multiply(slope).subtract(point.x.shiftLeft(1)).mod(p)
        val y = slope.multiply(point.x.subtract(x)).subtract(point.y).mod(p)
        return Point(x, y)
    }

    fun negate(point: Point?): Point? = point?.let { Point(it.x, p.subtract(it.y).mod(p)) }

    fun multiply(point: Point?, scalar: BigInteger): Point? {
        var result: Point? = INFINITY
        var addend = point
        val k = scalar.mod(n)
        for (bit in 0 until k.bitLength()) {
            if (k.testBit(bit)) result = add(result, addend)
            addend = add(addend, addend)
        }
        return result
    }

    /** Uncompressed SEC1 encoding, 65 bytes. */
    fun encode(point: Point): ByteArray = byteArrayOf(4) + fixed(point.x) + fixed(point.y)

    /** Null for anything that is not a valid point on the curve (never trust a peer's point). */
    fun decode(bytes: ByteArray): Point? {
        if (bytes.size != 65 || bytes[0] != 4.toByte()) return null
        val point = Point(BigInteger(1, bytes.copyOfRange(1, 33)), BigInteger(1, bytes.copyOfRange(33, 65)))
        return point.takeIf(::onCurve)
    }

    fun fixed(value: BigInteger): ByteArray {
        val raw = value.toByteArray()
        return when {
            raw.size == 32 -> raw
            raw.size > 32 -> raw.copyOfRange(raw.size - 32, raw.size)
            else -> ByteArray(32 - raw.size) + raw
        }
    }

    private fun decodeCompressed(hex: String): Point {
        val bytes = Hex.decode(hex)
        val x = BigInteger(1, bytes.copyOfRange(1, 33))
        val ySquared = x.pow(3).add(a.multiply(x)).add(b).mod(p)
        // p = 3 (mod 4), so a square root is ySquared^((p + 1) / 4).
        var y = ySquared.modPow(p.add(BigInteger.ONE).shiftRight(2), p)
        if (y.testBit(0) != (bytes[0] == 3.toByte())) y = p.subtract(y)
        return Point(x, y).also { check(onCurve(it)) { "SPAKE2 constant is not on P-256" } }
    }
}

/** One side of a pairing exchange. The phone is side A, the TV side B. */
internal class Spake2(
    private val isPhone: Boolean,
    code: String,
    private val phoneId: String,
    private val tvId: String,
    random: SecureRandom = SecureRandom(),
) {
    class Outcome(
        /** Long-term secret both sides store for this phone and TV. */
        val sharedKey: ByteArray,
        val myConfirm: ByteArray,
        val peerConfirm: ByteArray,
    )

    private val w: BigInteger = BigInteger(1, sha256("puretube-pair-v1:".toByteArray() + code.toByteArray())).mod(P256.n)
    private val secret: BigInteger = BigInteger(256 + 64, random).mod(P256.n.subtract(BigInteger.ONE)).add(BigInteger.ONE)
    private val blind = if (isPhone) P256.M else P256.N
    private val peerBlind = if (isPhone) P256.N else P256.M

    /** The value sent to the other side. */
    val share: ByteArray = P256.encode(checkNotNull(P256.add(P256.multiply(blind, w), P256.multiply(P256.G, secret))))

    /** Null when the peer's value is not a usable point. */
    fun finish(peerShare: ByteArray): Outcome? {
        val peer = P256.decode(peerShare) ?: return null
        val unblinded = P256.add(peer, P256.negate(P256.multiply(peerBlind, w))) ?: return null
        val k = P256.multiply(unblinded, secret) ?: return null
        val phoneShare = if (isPhone) share else peerShare
        val tvShare = if (isPhone) peerShare else share
        val transcript = lengthPrefixed(
            phoneId.toByteArray(), tvId.toByteArray(), phoneShare, tvShare, P256.encode(k), P256.fixed(w),
        )
        val prk = sha256(transcript)
        val key = LinkCrypto.expand(prk, "puretube shared key", 32)
        val phoneConfirm = LinkCrypto.hmac(LinkCrypto.expand(prk, "puretube confirm phone", 32), transcript)
        val tvConfirm = LinkCrypto.hmac(LinkCrypto.expand(prk, "puretube confirm tv", 32), transcript)
        return if (isPhone) Outcome(key, phoneConfirm, tvConfirm) else Outcome(key, tvConfirm, phoneConfirm)
    }

    private fun lengthPrefixed(vararg parts: ByteArray): ByteArray {
        var out = ByteArray(0)
        for (part in parts) {
            val length = part.size.toLong()
            out += ByteArray(8) { index -> (length ushr (8 * index)).toByte() }
            out += part
        }
        return out
    }

    private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
}
