package com.hhst.youtubelite.watch.presentation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Settings > Glow behind the player. */
class PlayerGlowTest {
    // Below Android 12 Modifier.blur does nothing: the "glow" would be a sharp copy of the thumbnail.
    @Test fun phonesWithoutBlurGetNoGlow() = assertFalse(PlayerGlow.available)

    @Test fun theGlowIsSofterOnLightPages() {
        assertTrue(PlayerGlow.LIGHT_ALPHA < PlayerGlow.DARK_ALPHA)
        assertTrue(PlayerGlow.DARK_ALPHA <= 0.6f)
    }
}
