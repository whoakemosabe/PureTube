package com.hhst.youtubelite.ui.design

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Test

/** The bottom tab bar's clear glass with a refracting rim (Matt, 2026-11-23). */
class NavigationGlassTest {
    // Kyant's lens needs corner radii to trace the rim; any other shape throws at draw time.
    @Test fun theBarShapeHasCornersForTheLens() = assertTrue(NavigationGlass.Shape is CornerBasedShape)

    @Test fun theRimBendsLessThanTheBarIsTall() {
        assertTrue(NavigationGlass.RefractionHeight > 0.dp)
        assertTrue(NavigationGlass.RefractionHeight < DesignTokens.BottomBarHeight / 2)
    }

    // Clearer than the top bars (0.55 / 0.66), but never so clear that tab labels drown.
    @Test fun theBarIsClearerThanTheTopBarsButStillTinted() {
        assertTrue(NavigationGlass.DARK_TINT in 0.3f..0.5f)
        assertTrue(NavigationGlass.LIGHT_TINT in 0.4f..0.6f)
    }
}
