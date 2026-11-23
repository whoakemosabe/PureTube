package com.hhst.youtubelite.player.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortsAdvanceTest {
    @Test fun switchOnMakesShortsPlayNext() {
        assertEquals(LoopMode.QUEUE_NEXT, ShortsAdvance.modeForSwitch(true, null))
        assertEquals(LoopMode.QUEUE_NEXT, ShortsAdvance.modeForSwitch(true, LoopMode.LOOP_ONE))
        assertNull(ShortsAdvance.modeForSwitch(true, LoopMode.QUEUE_NEXT))
    }

    @Test fun switchOffReturnsToLoopButKeepsPauseAtEnd() {
        assertEquals(LoopMode.LOOP_ONE, ShortsAdvance.modeForSwitch(false, LoopMode.QUEUE_NEXT))
        assertNull(ShortsAdvance.modeForSwitch(false, LoopMode.PAUSE_AT_END))
        assertNull(ShortsAdvance.modeForSwitch(false, null))
    }

    @Test fun aPickInAShortMovesTheSwitchWithoutAFightBack() {
        assertTrue(ShortsAdvance.switchFor(LoopMode.QUEUE_NEXT))
        assertFalse(ShortsAdvance.switchFor(LoopMode.PAUSE_AT_END))
        // Pause at end turns the switch off; the switch must not then rewrite it to Loop.
        assertNull(ShortsAdvance.modeForSwitch(ShortsAdvance.switchFor(LoopMode.PAUSE_AT_END), LoopMode.PAUSE_AT_END))
    }
}
