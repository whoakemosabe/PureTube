package com.hhst.youtubelite.ui.design

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls

/*
 * Frosted glass on Kyant's Backdrop 2.x, ported from Dropi and F1 Pro. A screen's scrolling
 * content is recorded with layerBackdrop and redrawn blurred behind its top bar and tab bar,
 * so the feed shows through as it scrolls underneath. Blur needs Android 12; older phones get
 * a solid tinted bar with the same look at rest.
 */

/**
 * The recorded page for the glass, painted onto the page background first. Without it the
 * copy is transparent between images, so the blurred copy only covers the pictures and the
 * sharp text underneath shows straight through the tint (Kyant's own samples do the same).
 */
@Composable
fun rememberGlassBackdrop(): LayerBackdrop {
    val background by rememberUpdatedState(DesignTokens.Background)
    return rememberLayerBackdrop {
        drawRect(background)
        drawContent()
    }
}

val glassAvailable: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** The current palette's background, and whether it is the dark one. */
private val glassBase: Color @Composable get() = DesignTokens.Background
private val glassDark: Boolean @Composable get() = LocalAppColors.current != LightAppPalette

/**
 * Frosted glass on any shape: the blurred, slightly more saturated page behind, under a tint
 * strong enough that bar text and icons stay readable over busy thumbnails.
 */
@Composable
fun Modifier.frosted(backdrop: LayerBackdrop, shape: Shape = RectangleShape): Modifier {
    val base = glassBase
    if (!glassAvailable) return this.background(base.copy(alpha = 0.94f), shape)
    val tint = base.copy(alpha = if (glassDark) 0.55f else 0.66f)
    return this.drawPlainBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            colorControls(saturation = 1.2f)
            blur(18.dp.toPx())
        },
        onDrawSurface = { drawRect(tint) },
    )
}
