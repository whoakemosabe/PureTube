package com.hhst.youtubelite.ui.design

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class AppPalette(
    val background: Color,
    val ink: Color,
    val secondary: Color,
    /** Secondary container: chips, pressed pills, quiet surfaces. */
    val surface: Color,
    /** Stronger state container: selected rows, skeletons, banners. */
    val ghost: Color,
    val border: Color,
    val navigationDivider: Color,
    val scrim: Color,
    val menuScrim: Color,
    /** Immediate press response under icons and rows. */
    val stateLayer: Color,
    val red: Color,
    val link: Color,
    /** Section headings (Settings, menus): brand red, shaded to pass 4.5:1 on the page. */
    val heading: Color = Color(0xFFCC0029),
)

val LightAppPalette = AppPalette(
    background = Color.White,
    ink = Color(0xFF0F0F0F),
    secondary = Color(0xFF606060),
    surface = Color(0xFFF2F2F2),
    ghost = Color(0xFFE5E5E5),
    border = Color.Black.copy(alpha = 0.1f),
    navigationDivider = Color.Black.copy(alpha = 0.2f),
    scrim = Color.Black.copy(alpha = 0.3f),
    menuScrim = Color.Black.copy(alpha = 0.6f),
    stateLayer = Color.Black.copy(alpha = 0.08f),
    red = Color(0xFFFF0033),
    link = Color(0xFF065FD4),
)

val DarkAppPalette = AppPalette(
    background = Color.Black,
    ink = Color(0xFFF1F1F1),
    secondary = Color(0xFFAAAAAA),
    surface = Color(0xFF212121),
    ghost = Color(0xFF272727),
    border = Color.White.copy(alpha = 0.1f),
    navigationDivider = Color.White.copy(alpha = 0.2f),
    scrim = Color.Black.copy(alpha = 0.5f),
    menuScrim = Color.Black.copy(alpha = 0.7f),
    stateLayer = Color.White.copy(alpha = 0.08f),
    red = Color(0xFFFF0033),
    link = Color(0xFF3EA6FF),
    heading = Color(0xFFFF6B81),
)

/**
 * Dark grey pages (LiTube #383). The first version used YouTube's current #0F0F0F, which
 * reads as black on a phone screen; #1F1F1F is plainly grey, with containers a step up.
 */
val DarkGreyAppPalette = DarkAppPalette.copy(
    background = Color(0xFF1F1F1F),
    surface = Color(0xFF2E2E2E),
    ghost = Color(0xFF454545),
)

fun appPalette(dark: Boolean, grey: Boolean): AppPalette = when {
    dark && grey -> DarkGreyAppPalette
    dark -> DarkAppPalette
    else -> LightAppPalette
}

val LocalAppColors = staticCompositionLocalOf { LightAppPalette }
