package com.hhst.youtubelite.ui.design

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
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

    // The header matches the tab bar: its rim is the same curve, mirrored to the bottom edge.
    @Test fun theHeaderRimMirrorsTheTabBar() {
        assertTrue(HeaderGlass.Shape is CornerBasedShape)
        assertTrue(HeaderGlass.BottomRadius == NavigationGlass.TopRadius)
    }

    // One pane under the top bar and chips (Matt, 2026-11-25): it starts above the top bar, so
    // its bent top edge is off screen, and ends at the chips' bottom, where the rim is.
    @Test fun theHeaderIsOnePaneFromAboveTheTopBarToTheChipsBottom() {
        val shown = HeaderGlass.pane(topBarHeight = 48f, chipHeight = 48f, chipTravel = 108f, hidden = 0f)
        assertEquals(-NavigationGlass.RefractionHeight.value, shown.start, 0.001f)
        assertEquals(96f, shown.endInclusive, 0.001f)
        val hidden = HeaderGlass.pane(topBarHeight = 48f, chipHeight = 48f, chipTravel = 108f, hidden = 1f)
        assertEquals(-48f - NavigationGlass.RefractionHeight.value, hidden.start, 0.001f)
        assertEquals(-12f, hidden.endInclusive, 0.001f)
    }

    @Test fun thePaneNeverTurnsInsideOut() {
        val pane = HeaderGlass.pane(topBarHeight = 48f, chipHeight = 10f, chipTravel = 400f, hidden = 1f)
        assertTrue(pane.endInclusive >= pane.start)
    }

    // The chips slide up faster than the top bar; whatever passes behind it is not drawn.
    @Test fun chipsBehindTheTopBarAreHidden() {
        assertEquals(0f, HeaderGlass.chipsBehindTopBar(48f, 108f, hidden = 0f), 0.001f)
        assertEquals(30f, HeaderGlass.chipsBehindTopBar(48f, 108f, hidden = 0.5f), 0.001f)
        assertEquals(60f, HeaderGlass.chipsBehindTopBar(48f, 108f, hidden = 1f), 0.001f)
    }
}
