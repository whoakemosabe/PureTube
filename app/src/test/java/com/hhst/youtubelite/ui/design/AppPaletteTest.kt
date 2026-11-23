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

    // Settings and menu headings are red text: they must stay readable on every page colour.
    @Test fun headingsPassTextContrastOnEveryPalette() {
        for (palette in listOf(LightAppPalette, DarkAppPalette, DarkGreyAppPalette)) {
            assertTrue("${palette.heading} on ${palette.background}", contrast(palette.heading, palette.background) >= 4.5)
            assertTrue("${palette.heading} on ${palette.surface}", contrast(palette.heading, palette.surface) >= 4.5)
        }
    }

    private fun contrast(a: Color, b: Color): Double {
        fun channel(c: Float) = if (c <= 0.03928f) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        fun lum(c: Color) = 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
        val (hi, lo) = listOf(lum(a), lum(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }
}
