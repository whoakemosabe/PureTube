package com.hhst.youtubelite.player.service

import com.hhst.youtubelite.extension.PreferenceKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** LiTube #371: a setting to stop a playing video when PureTube is swiped out of recents. */
class SwipeAwayTest {
    @Test fun playingKeepsGoingByDefault() {
        assertEquals(false, PreferenceKeys.DEFAULTS[PreferenceKeys.STOP_ON_SWIPE_AWAY])
        assertTrue(keepPlayingAfterSwipe(playing = true, stopOnSwipe = false))
    }

    @Test fun theSettingStopsAPlayingVideo() =
        assertFalse(keepPlayingAfterSwipe(playing = true, stopOnSwipe = true))

    @Test fun pausedPlaybackAlwaysEnds() =
        assertFalse(keepPlayingAfterSwipe(playing = false, stopOnSwipe = false))
}
