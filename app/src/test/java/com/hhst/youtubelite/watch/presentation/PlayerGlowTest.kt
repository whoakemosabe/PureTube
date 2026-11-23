package com.hhst.youtubelite.watch.presentation

import org.junit.Assert.assertTrue
import org.junit.Test

/** Settings > Glow behind the player. */
class PlayerGlowTest {
    // The accent glow is a gradient, so it needs no blur and shows on every Android version.
    @Test fun theAccentGlowShowsWithoutBlur() {
        assertTrue(PlayerGlow.FOLLOWS_ACCENT)
        assertTrue(PlayerGlow.available)
    }

    @Test fun theGlowIsSofterOnLightPages() {
        assertTrue(PlayerGlow.LIGHT_ALPHA < PlayerGlow.DARK_ALPHA)
        assertTrue(PlayerGlow.DARK_ALPHA <= 0.6f)
    }
}
