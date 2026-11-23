package com.hhst.youtubelite.player

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerTest {
    @Test fun aCountdownPausesOnceAndTurnsItselfOff() = runTest {
        var fired = 0
        val shown = mutableListOf<SleepChoice>()
        val timer = SleepTimer(this, { shown += it }, { fired++ })
        timer.set(SleepChoice.Minutes(15))
        advanceTimeBy(15 * 60_000L - 1)
        runCurrent()
        assertEquals(0, fired)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(1, fired)
        assertEquals(SleepChoice.Off, timer.choice)
        assertEquals(SleepChoice.Off, shown.last())
    }

    @Test fun changingOrCancellingDropsTheOldCountdown() = runTest {
        var fired = 0
        val timer = SleepTimer(this, {}, { fired++ })
        timer.set(SleepChoice.Minutes(15))
        timer.set(SleepChoice.Minutes(60))
        advanceTimeBy(30 * 60_000L)
        runCurrent()
        assertEquals(0, fired)
        timer.set(SleepChoice.Off)
        advanceTimeBy(60 * 60_000L)
        runCurrent()
        assertEquals(0, fired)
    }

    @Test fun endOfVideoTakesOnlyThatEnd() = runTest {
        var fired = 0
        val timer = SleepTimer(this, {}, { fired++ })
        assertFalse(timer.onVideoEnded())
        timer.set(SleepChoice.EndOfVideo)
        assertTrue(timer.onVideoEnded())
        assertEquals(1, fired)
        assertFalse(timer.onVideoEnded())
    }

    // Loop one, Pause at end and Shorts never report an end, so the player must stop itself.
    @Test fun endOfVideoAsksThePlayerToStopAtTheEndAndReleasesItAfter() = runTest {
        val armed = mutableListOf<Boolean>()
        val timer = SleepTimer(this, {}, {}, armEndOfVideo = { armed += it })
        timer.set(SleepChoice.EndOfVideo)
        assertEquals(listOf(true), armed)
        timer.onVideoEnded()
        assertEquals(listOf(true, false), armed)
        timer.set(SleepChoice.Minutes(15))
        assertEquals(false, armed.last())
    }
}
