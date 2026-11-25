package com.hhst.youtubelite.tv

import com.hhst.youtubelite.player.engine.PlaybackSnapshot
import com.hhst.youtubelite.tvlink.TvLinkMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TvReceiverTest {
    @Test fun nothingLoadedReportsIdleWithNoVideo() {
        val status = TvReceiver.statusOf(PlaybackSnapshot())
        assertNull(status.videoId)
        assertEquals(TvLinkMessage.Status.STATE_IDLE, status.state)
    }

    @Test fun statesFollowTheEngine() {
        val base = PlaybackSnapshot(videoId = "abc", positionMs = 5_000, durationMs = 60_000, bufferedPositionMs = 20_000)
        assertEquals(TvLinkMessage.Status.STATE_BUFFERING, TvReceiver.statusOf(base).state)
        assertEquals(TvLinkMessage.Status.STATE_READY, TvReceiver.statusOf(base.copy(prepared = true)).state)
        assertEquals(TvLinkMessage.Status.STATE_BUFFERING, TvReceiver.statusOf(base.copy(prepared = true, isBuffering = true)).state)
        assertEquals(TvLinkMessage.Status.STATE_ENDED, TvReceiver.statusOf(base.copy(prepared = true, ended = true)).state)
        val failed = TvReceiver.statusOf(base.copy(error = "Playback failed"))
        assertEquals(TvLinkMessage.Status.STATE_IDLE, failed.state)
        assertEquals("Playback failed", failed.error)
    }

    @Test fun positionsAndIntentTravel() {
        val status = TvReceiver.statusOf(PlaybackSnapshot(videoId = "abc", prepared = true, isPlaying = true,
            userWantsPlay = true, positionMs = 5_000, durationMs = 60_000, bufferedPositionMs = 20_000, speed = 1.5f))
        assertEquals(5_000L, status.positionMs)
        assertEquals(60_000L, status.durationMs)
        assertEquals(20_000L, status.bufferedMs)
        assertEquals(1.5f, status.speed)
        assertTrue(status.playing && status.wantsPlay)
    }

    @Test fun messagesSurviveTheWire() {
        val messages = listOf(
            TvLinkMessage.Load("abc", "Title", "Channel", 1_000, autoplay = false),
            TvLinkMessage.Play, TvLinkMessage.Pause, TvLinkMessage.Seek(9), TvLinkMessage.Speed(1.25f),
            TvLinkMessage.Stop, TvLinkMessage.Ping, TvLinkMessage.Skip(forward = true), TvLinkMessage.NotPaired,
            TvLinkMessage.Status(videoId = "abc", state = TvLinkMessage.Status.STATE_READY, positionMs = 3),
        )
        messages.forEach { assertEquals(it, TvLinkMessage.decodeOrNull(TvLinkMessage.encode(it))) }
        assertNull(TvLinkMessage.decodeOrNull("{\"t\":\"self_destruct\"}"))
        assertNull(TvLinkMessage.decodeOrNull("not json"))
    }

    @Test fun statsShowWhatThePlayerIsDoing() {
        val lines = statsLines(PlaybackSnapshot(videoId = "abc", positionMs = 10_000, bufferedPositionMs = 40_000))
        assertTrue(lines.any { it.startsWith("Buffer") && it.contains("30s ahead") })
    }
}
