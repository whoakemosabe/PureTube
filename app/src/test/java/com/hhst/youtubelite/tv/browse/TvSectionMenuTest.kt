package com.hhst.youtubelite.tv.browse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvSectionMenuTest {
    @Test fun homeOffersRefreshFeedSwitchesAndWaysOut() {
        val actions = sectionActions(TvSection.Home, signedIn = false)
        assertEquals(SectionAction.Refresh, actions.first())
        assertTrue(SectionAction.HideShorts in actions && SectionAction.HideWatched in actions)
        assertTrue(SectionAction.SignIn in actions)
        assertEquals(SectionAction.Settings, actions.last())
    }

    @Test fun signedInHidesSignIn() {
        assertFalse(SectionAction.SignIn in sectionActions(TvSection.Home, signedIn = true))
    }

    @Test fun shortsHasNoHideShortsSwitch() {
        assertFalse(SectionAction.HideShorts in sectionActions(TvSection.Shorts, signedIn = true))
    }

    @Test fun onlySwitchesCarryASettingKey() {
        SectionAction.entries.forEach { action ->
            val isSwitch = action in setOf(SectionAction.HideShorts, SectionAction.HideWatched, SectionAction.BetterTitles)
            assertEquals(isSwitch, action.key != null)
        }
    }
}
