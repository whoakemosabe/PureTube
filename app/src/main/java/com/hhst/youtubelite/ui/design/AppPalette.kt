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
    /** The chosen highlight colour ([com.hhst.youtubelite.ui.theme.AccentColor]); [red] stays for LIVE and destructive actions. */
    val accent: Color = red,
) {
    /** Dark page palettes; compared by background so an accent copy keeps its darkness. */
    val dark: Boolean get() = background != Color.White
}

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

/**
 * Applies an accent choice. Each colour has a light-page and a dark-page shade, picked so the
 * heading text keeps 4.5:1 contrast on its page. [wallpaper] is the system's Material You
 * colour for this darkness (Android 12 and later); without one, Wallpaper falls back to red.
 */
fun AppPalette.withAccent(choice: com.hhst.youtubelite.ui.theme.AccentColor, dark: Boolean, wallpaper: Color? = null): AppPalette {
    val (accent, heading) = when (choice) {
        com.hhst.youtubelite.ui.theme.AccentColor.Red -> return copy(accent = red)
        com.hhst.youtubelite.ui.theme.AccentColor.Blue ->
            if (dark) Color(0xFF3EA6FF) to Color(0xFF6CBBFF) else Color(0xFF065FD4) to Color(0xFF065FD4)
        com.hhst.youtubelite.ui.theme.AccentColor.Green ->
            if (dark) Color(0xFF2FBF5B) to Color(0xFF5FD383) else Color(0xFF1E8E3E) to Color(0xFF137333)
        com.hhst.youtubelite.ui.theme.AccentColor.Purple ->
            if (dark) Color(0xFFA47CFF) to Color(0xFFBFA2FF) else Color(0xFF6E3FD8) to Color(0xFF6E3FD8)
        com.hhst.youtubelite.ui.theme.AccentColor.Wallpaper -> (wallpaper ?: return copy(accent = red)) to wallpaper
    }
    return copy(accent = accent, heading = heading)
}

val LocalAppColors = staticCompositionLocalOf { LightAppPalette }
