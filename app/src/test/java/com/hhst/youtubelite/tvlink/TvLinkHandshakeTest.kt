package com.hhst.youtubelite.tvlink

import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.math.BigInteger
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TvLinkHandshakeTest {
    private val pool = Executors.newCachedThreadPool()

    @After fun tearDown() { pool.shutdownNow() }

    private class Pipe {
        val phoneIn = PipedInputStream(LineChannel.MAX_LINE * 2)
        val tvIn = PipedInputStream(LineChannel.MAX_LINE * 2)
        private val phoneOut = PipedOutputStream(tvIn)
        private val tvOut = PipedOutputStream(phoneIn)
        val phone = LineChannel(phoneIn, phoneOut) { phoneOut.close(); phoneIn.close() }
        val tv = LineChannel(tvIn, tvOut) { tvOut.close(); tvIn.close() }
    }

    private fun connect(
        phoneStore: TvLinkStore,
        tvStore: TvLinkStore,
        code: PairingCode,
        typed: String?,
    ): Pair<PhoneHandshake.Result, TvHandshake.Accepted?> {
        val pipe = Pipe()
        val tv = pool.submit(Callable { TvHandshake(tvStore, { "Living room" }, code).run(pipe.tv) })
        val phone = PhoneHandshake(phoneStore) { "Pixel" }.run(pipe.phone, typed)
        // A refused phone closes its end; the TV side then ends on its own.
        if (phone !is PhoneHandshake.Result.Connected) pipe.phone.close()
        return phone to tv.get(5, TimeUnit.SECONDS)
    }

    @Test fun curveConstantsAreValidPoints() {
        assertTrue(P256.onCurve(P256.G))
        assertTrue(P256.onCurve(P256.M))
        assertTrue(P256.onCurve(P256.N))
        assertNull(P256.multiply(P256.G, P256.n))
        assertEquals(P256.G, P256.multiply(P256.G, P256.n.add(BigInteger.ONE)))
    }

    @Test fun offCurvePointsAreRejected() {
        val bad = P256.encode(P256.G).also { it[64] = (it[64] + 1).toByte() }
        assertNull(P256.decode(bad))
        assertNotNull(P256.decode(P256.encode(P256.G)))
    }

    @Test fun sameCodeAgreesOnOneKey() {
        val phone = Spake2(isPhone = true, code = "123456", phoneId = "p", tvId = "t")
        val tv = Spake2(isPhone = false, code = "123456", phoneId = "p", tvId = "t")
        val phoneOutcome = checkNotNull(phone.finish(tv.share))
        val tvOutcome = checkNotNull(tv.finish(phone.share))
        assertArrayEquals(phoneOutcome.sharedKey, tvOutcome.sharedKey)
        assertArrayEquals(phoneOutcome.myConfirm, tvOutcome.peerConfirm)
        assertArrayEquals(tvOutcome.myConfirm, phoneOutcome.peerConfirm)
    }

    @Test fun differentCodesNeverConfirm() {
        val phone = Spake2(isPhone = true, code = "123456", phoneId = "p", tvId = "t")
        val tv = Spake2(isPhone = false, code = "123457", phoneId = "p", tvId = "t")
        val phoneOutcome = checkNotNull(phone.finish(tv.share))
        val tvOutcome = checkNotNull(tv.finish(phone.share))
        assertFalse(phoneOutcome.sharedKey.contentEquals(tvOutcome.sharedKey))
        assertFalse(phoneOutcome.myConfirm.contentEquals(tvOutcome.peerConfirm))
    }

    @Test fun unknownPhoneIsAskedForTheCodeBeforeSendingAnything() {
        val (phone, tv) = connect(MemoryTvLinkStore("phone"), MemoryTvLinkStore("tv"), PairingCode { "111111" }, null)
        assertEquals(PhoneHandshake.Result.NeedsCode("tv", "Living room"), phone)
        assertNull(tv)
    }

    @Test fun rightCodePairsAndLaterConnectionsNeedNoCode() {
        val phoneStore = MemoryTvLinkStore("phone")
        val tvStore = MemoryTvLinkStore("tv")
        val codes = ArrayDeque(listOf("111111", "222222", "333333"))
        val code = PairingCode { codes.removeFirst() }
        val (phone, tv) = connect(phoneStore, tvStore, code, "111111")
        assertTrue(phone is PhoneHandshake.Result.Connected)
        assertNotNull(tv)
        assertTrue(tv!!.newlyPaired)
        assertEquals("Pixel", tv.phoneName)
        assertArrayEquals(phoneStore.secret("tv"), tvStore.secret("phone"))
        // A used code is spent.
        assertEquals("222222", code.current)

        val (again, tvAgain) = connect(phoneStore, tvStore, code, null)
        assertTrue(again is PhoneHandshake.Result.Connected)
        assertFalse(tvAgain!!.newlyPaired)

        // Sealed traffic flows both ways after the handshake.
        val channel = (again as PhoneHandshake.Result.Connected).channel
        channel.send(TvLinkMessage.Load("dQw4w9WgXcQ", "Title", startMs = 42_000))
        assertEquals(TvLinkMessage.Load("dQw4w9WgXcQ", "Title", startMs = 42_000), tvAgain.channel.receive())
        tvAgain.channel.send(TvLinkMessage.Status(videoId = "dQw4w9WgXcQ", state = TvLinkMessage.Status.STATE_READY, positionMs = 42_000))
        assertEquals(42_000L, (channel.receive() as TvLinkMessage.Status).positionMs)
    }

    @Test fun threeWrongCodesReplaceTheCode() {
        val tvStore = MemoryTvLinkStore("tv")
        val codes = ArrayDeque(listOf("111111", "999999"))
        val code = PairingCode { codes.removeFirst() }
        repeat(2) {
            val (phone, tv) = connect(MemoryTvLinkStore("phone"), tvStore, code, "000000")
            assertEquals(PhoneHandshake.Result.Refused(TvLinkMessage.REFUSED_WRONG_CODE), phone)
            assertNull(tv)
        }
        val (third, _) = connect(MemoryTvLinkStore("phone"), tvStore, code, "000000")
        assertEquals(PhoneHandshake.Result.Refused(TvLinkMessage.REFUSED_NEW_CODE), third)
        assertEquals("999999", code.current)
        assertTrue(tvStore.peers().isEmpty())
        // The old code no longer works once it was replaced.
        val (late, _) = connect(MemoryTvLinkStore("phone"), tvStore, code, "111111")
        assertTrue(late is PhoneHandshake.Result.Refused)
    }

    @Test fun phoneForgottenOnTheTvDropsItsKeyAndAsksAgain() {
        val phoneStore = MemoryTvLinkStore("phone")
        val tvStore = MemoryTvLinkStore("tv")
        val code = PairingCode { "111111" }
        connect(phoneStore, tvStore, code, "111111")
        tvStore.remove("phone")
        val (phone, _) = connect(phoneStore, tvStore, code, null)
        assertEquals(PhoneHandshake.Result.NeedsCode("tv", "Living room"), phone)
        assertNull(phoneStore.secret("tv"))
    }

    @Test fun aKeyThatDoesNotMatchIsNotAccepted() {
        val phoneStore = MemoryTvLinkStore("phone")
        val tvStore = MemoryTvLinkStore("tv")
        tvStore.save("phone", "Pixel", ByteArray(32) { 1 })
        phoneStore.save("tv", "Living room", ByteArray(32) { 2 })
        val (_, tv) = connect(phoneStore, tvStore, PairingCode { "111111" }, null)
        assertNull(tv)
    }

    @Test fun sessionFramesCannotBeReplayed() {
        val secret = ByteArray(32) { 7 }
        val phone = SessionCipher(secret, ByteArray(16) { 1 }, ByteArray(16) { 2 }, isPhone = true)
        val tv = SessionCipher(secret, ByteArray(16) { 1 }, ByteArray(16) { 2 }, isPhone = false)
        val first = phone.seal("a".toByteArray())
        assertEquals("a", String(tv.open(first)))
        val replayed = runCatching { tv.open(first) }
        assertTrue(replayed.isFailure)
        // Another connection's keys differ even with the same secret.
        val other = SessionCipher(secret, ByteArray(16) { 3 }, ByteArray(16) { 2 }, isPhone = true)
        assertNotEquals(Hex.encode(other.seal("a".toByteArray())), Hex.encode(first))
    }

    @Test fun codesAreSixDigits() {
        repeat(50) { assertTrue(PairingCode.randomCode().matches(Regex("[0-9]{6}"))) }
    }
}
