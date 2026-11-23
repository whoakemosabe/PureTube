package com.hhst.youtubelite.ui.design

import androidx.compose.ui.graphics.Color
import com.hhst.youtubelite.ui.theme.AppAppearance
import com.hhst.youtubelite.ui.theme.ThemeMode
import com.hhst.youtubelite.ui.theme.AccentColor
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

    @Test fun storedAccentsKeepTheirPositions() = assertEquals(
        listOf(AccentColor.Red, AccentColor.Blue, AccentColor.Green, AccentColor.Purple, AccentColor.Wallpaper), AccentColor.entries)

    @Test fun redKeepsTodaysColoursAndTheBrandRedStaysForLive() {
        assertEquals(DarkGreyAppPalette, DarkGreyAppPalette.withAccent(AccentColor.Red, dark = true))
        val blue = DarkGreyAppPalette.withAccent(AccentColor.Blue, dark = true)
        assertEquals(Color(0xFF3EA6FF), blue.accent)
        assertEquals(Color(0xFFFF0033), blue.red)
        assertEquals(DarkGreyAppPalette.background, blue.background)
    }

    // An accent copy is still a light or dark palette: glass and fields read darkness from it.
    @Test fun accentCopiesKeepTheirDarkness() {
        for (choice in AccentColor.entries) {
            assertFalse(LightAppPalette.withAccent(choice, dark = false, wallpaper = Color(0xFF2E7D62)).dark)
            assertTrue(DarkAppPalette.withAccent(choice, dark = true, wallpaper = Color(0xFF9EDCC5)).dark)
        }
    }

    @Test fun wallpaperUsesTheSystemColourOrFallsBackToRed() {
        assertEquals(Color(0xFF9EDCC5), DarkGreyAppPalette.withAccent(AccentColor.Wallpaper, true, Color(0xFF9EDCC5)).accent)
        assertEquals(DarkGreyAppPalette.red, DarkGreyAppPalette.withAccent(AccentColor.Wallpaper, true, null).accent)
    }

    @Test fun accentHeadingsPassTextContrastOnTheirPages() {
        for (choice in AccentColor.entries.filter { it != AccentColor.Wallpaper }) {
            for ((palette, dark) in listOf(LightAppPalette to false, DarkAppPalette to true, DarkGreyAppPalette to true)) {
                val tinted = palette.withAccent(choice, dark)
                assertTrue("$choice heading on ${palette.background}", contrast(tinted.heading, palette.background) >= 4.5)
                assertTrue("$choice heading on ${palette.surface}", contrast(tinted.heading, palette.surface) >= 4.5)
            }
        }
    }

    private fun contrast(a: Color, b: Color): Double {
        fun channel(c: Float) = if (c <= 0.03928f) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        fun lum(c: Color) = 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
        val (hi, lo) = listOf(lum(a), lum(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }
}
