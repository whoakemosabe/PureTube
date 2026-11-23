package com.hhst.youtubelite.player.surface

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowBrightnessTest {
    private var window = -1f // BRIGHTNESS_OVERRIDE_NONE: follow the system slider
    private val writes = mutableListOf<Float>()
    private val brightness = WindowBrightness(read = { window }, write = { window = it; writes += it })

    @Test fun restoreHandsBrightnessBackToTheSystem() {
        brightness.adjust(0.6f)
        assertTrue(window > 0.9f)
        assertTrue(brightness.overridden)
        brightness.endGesture()
        brightness.adjust(0.1f)
        brightness.restore()
        assertEquals(-1f, window)
        assertFalse(brightness.overridden)
    }

    @Test fun restoreWithoutASwipeWritesNothing() {
        brightness.restore()
        assertTrue(writes.isEmpty())
    }

    @Test fun aSwipeAfterRestoreCapturesTheSystemStateAgain() {
        brightness.adjust(0.2f)
        brightness.restore()
        window = 0.3f // an app-set level that is not ours
        brightness.adjust(0.2f)
        brightness.restore()
        assertEquals(0.3f, window)
    }

    // Following the system at 10%: the first swipe starts from 10%, not a bright 50%.
    @Test fun theFirstSwipeStartsFromThePhonesOwnBrightness() {
        var window = -1f
        val writes = mutableListOf<Float>()
        val brightness = WindowBrightness(read = { window }, write = { window = it; writes += it }, system = { 0.1f })
        brightness.adjust(0.02f)
        org.junit.Assert.assertEquals(0.12f, writes.single(), 0.001f)
        brightness.restore()
        org.junit.Assert.assertEquals(-1f, window, 0f)
    }
}
