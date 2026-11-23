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
import androidx.compose.ui.layout.layout
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars

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
private val glassDark: Boolean @Composable get() = LocalAppColors.current.dark

/**
 * The bottom tab bar's glass (Matt, 2026-11-23): clearer than the top bars, with rounded
 * top corners and a band along the top edge that bends the feed behind it like the rim of a
 * glass pane (Kyant's lens refraction, Android 13+). A soft light line traces the rim.
 */
object NavigationGlass {
    val TopRadius = 24.dp
    /** How deep the bent band along the rim reaches, and how far it shifts the picture. */
    val RefractionHeight = 18.dp
    val RefractionAmount = 28.dp
    val Blur = 6.dp
    const val DARK_TINT = 0.38f
    const val LIGHT_TINT = 0.5f
    val Shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = TopRadius, topEnd = TopRadius)
}

@Composable
fun Modifier.navigationGlass(backdrop: LayerBackdrop): Modifier =
    clearGlass(backdrop, NavigationGlass.Shape, rim = GlassRim.Top)

/**
 * The header's glass, matching the tab bar (Matt, 2026-11-23): the same clear tint and blur.
 * The chip strip is the header's lower pane, so it carries the rounded, refracting rim along
 * its bottom edge; the top bar above it is the same glass without a rim, so the two read as
 * one pane while the header slides away on scroll.
 */
object HeaderGlass {
    val BottomRadius = NavigationGlass.TopRadius
    val Shape = androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = BottomRadius, bottomEnd = BottomRadius)
}

@Composable
fun Modifier.headerGlass(backdrop: LayerBackdrop, rim: Boolean): Modifier =
    if (rim) clearGlass(backdrop, HeaderGlass.Shape, rim = GlassRim.Bottom)
    else clearGlass(backdrop, RectangleShape, rim = GlassRim.None)

enum class GlassRim { Top, Bottom, None }

@Composable
private fun Modifier.clearGlass(backdrop: LayerBackdrop, shape: androidx.compose.ui.graphics.Shape, rim: GlassRim): Modifier {
    val base = glassBase
    val dark = glassDark
    val light = if (dark) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.7f)
    val edge = if (dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.10f)
    if (!glassAvailable) return this.background(base.copy(alpha = 0.94f), shape)
    val tint = base.copy(alpha = if (dark) NavigationGlass.DARK_TINT else NavigationGlass.LIGHT_TINT)
    return this.drawPlainBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            colorControls(saturation = 1.4f)
            blur(NavigationGlass.Blur.toPx())
            // The lens bends every edge of a rounded pane; a rimless pane stays flat so it can
            // butt against its neighbour. A no-op before Android 13 (no runtime shaders).
            if (rim != GlassRim.None)
                lens(NavigationGlass.RefractionHeight.toPx(), NavigationGlass.RefractionAmount.toPx())
        },
        onDrawSurface = {
            drawRect(tint)
            val band = NavigationGlass.RefractionHeight.toPx()
            when (rim) {
                // Light catching the rim, fading into the pane.
                GlassRim.Top -> drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(
                    0f to light.copy(alpha = light.alpha * 0.5f), 1f to Color.Transparent, endY = band))
                GlassRim.Bottom -> drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(
                    0f to Color.Transparent, 1f to light.copy(alpha = light.alpha * 0.5f),
                    startY = size.height - band, endY = size.height))
                GlassRim.None -> Unit
            }
            if (rim != GlassRim.None) drawOutline(shape.createOutline(size, layoutDirection, this), edge,
                style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        },
    )
}

/**
 * The system navigation bar's height while the keyboard is closed. Tab screens grow their
 * feed and tab bar down by this much, so the glass reaches the bottom edge of the screen
 * with the feed behind it, instead of stopping above a plain strip.
 */
@Composable
fun navigationBarExtra(): androidx.compose.ui.unit.Dp {
    val density = androidx.compose.ui.platform.LocalDensity.current
    if (WindowInsets.ime.getBottom(density) > 0) return 0.dp
    return with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
}

/** Measures [extra] taller than offered and overflows downwards; the parent still sees the offered size. */
fun Modifier.extendBelow(extra: androidx.compose.ui.unit.Dp): Modifier = this.then(Modifier.layout { measurable, constraints ->
    val px = extra.roundToPx()
    if (px <= 0 || !constraints.hasBoundedHeight) {
        val placeable = measurable.measure(constraints)
        return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
    val placeable = measurable.measure(constraints.copy(minHeight = constraints.minHeight + px, maxHeight = constraints.maxHeight + px))
    layout(placeable.width, (placeable.height - px).coerceAtLeast(0)) { placeable.place(0, 0) }
})
