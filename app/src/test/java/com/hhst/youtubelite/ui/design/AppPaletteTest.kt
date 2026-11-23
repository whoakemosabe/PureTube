package com.hhst.youtubelite.ui.design

import androidx.compose.ui.graphics.Color
import com.hhst.youtubelite.ui.theme.AppAppearance
import com.hhst.youtubelite.ui.theme.ThemeMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** LiTube #383: a dark grey choice next to black Dark. */
class AppPaletteTest {
    @After fun reset() = AppAppearance.update(ThemeMode.System)

    @Test fun darkGreyIsADarkThemeOnGreyPages() {
        AppAppearance.update(ThemeMode.DarkGrey)
        assertEquals(true, AppAppearance.dark)
        assertTrue(AppAppearance.grey)
        assertEquals(Color(0xFF1F1F1F), appPalette(dark = true, grey = true).background)
    }

    @Test fun darkStaysBlack() {
        AppAppearance.update(ThemeMode.Dark)
        assertFalse(AppAppearance.grey)
        assertEquals(Color.Black, appPalette(dark = true, grey = false).background)
        // Grey only applies to a dark page; System resolving light stays light.
        assertEquals(LightAppPalette, appPalette(dark = false, grey = true))
    }

    @Test fun storedModesKeepTheirPositions() =
        assertEquals(listOf(ThemeMode.System, ThemeMode.Light, ThemeMode.Dark, ThemeMode.DarkGrey), ThemeMode.entries)
}
