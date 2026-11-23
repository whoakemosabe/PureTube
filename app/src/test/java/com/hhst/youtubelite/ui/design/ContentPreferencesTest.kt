package com.hhst.youtubelite.ui.design

import com.hhst.youtubelite.extension.PreferenceKeys
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentPreferencesTest {
    @Test
    fun withoutAManagerEachSettingFallsBackToItsDefault() {
        // Reading false here hid the player title, which shows by default.
        assertTrue(preferenceOrDefault(null, PreferenceKeys.SHOW_PLAYER_TITLE))
        assertFalse(preferenceOrDefault(null, PreferenceKeys.ENABLE_HIDE_WATCHED))
        assertFalse(preferenceOrDefault(null, PreferenceKeys.ENABLE_HIDE_RELATED))
        // A stored value always wins.
        assertFalse(preferenceOrDefault(false, PreferenceKeys.SHOW_PLAYER_TITLE))
        assertTrue(preferenceOrDefault(true, PreferenceKeys.ENABLE_HIDE_WATCHED))
    }
}
