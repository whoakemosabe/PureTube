package com.hhst.youtubelite.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.hhst.youtubelite.extension.ExtensionManager
import com.hhst.youtubelite.extension.PreferenceKeys
import org.koin.core.context.GlobalContext

val LocalHideShorts = staticCompositionLocalOf { false }
/** Feeds drop videos watched to the end (LiTube #277). */
val LocalHideWatched = staticCompositionLocalOf { false }
/** Videos marked watched or unwatched from a menu; they override YouTube's progress. */
val LocalWatchMarks = androidx.compose.runtime.compositionLocalOf { emptyMap<String, Boolean>() }

/** Cards, List or 2 columns on Home and Subscriptions (Settings > Feed layout). */
val LocalFeedLayout = staticCompositionLocalOf { com.hhst.youtubelite.home.domain.FeedLayout.Cards }

@Composable
internal fun rememberFeedLayout(): com.hhst.youtubelite.home.domain.FeedLayout {
    val manager = remember { GlobalContext.getOrNull()?.getOrNull<ExtensionManager>() }
        ?: return com.hhst.youtubelite.home.domain.FeedLayout.Cards
    var layout by remember(manager) { mutableStateOf(manager.feedLayout()) }
    DisposableEffect(manager) {
        val listener: (String) -> Unit = { key ->
            if (key == PreferenceKeys.FEED_LAYOUT || key == "*") layout = manager.feedLayout()
        }
        manager.addOnChangedListener(listener)
        listener("*")
        onDispose { manager.removeOnChangedListener(listener) }
    }
    return layout
}

/** The watch page glows with the video's colours behind the player. */
@Composable
internal fun rememberPlayerGlow(): Boolean = rememberFeedPreference(PreferenceKeys.PLAYER_GLOW)

@Composable
internal fun rememberWatchMarks(): Map<String, Boolean> {
    val marks = remember { GlobalContext.getOrNull()?.getOrNull<com.hhst.youtubelite.data.media.WatchMarks>() }
        ?: return emptyMap()
    return marks.marks.collectAsState().value
}

@Composable
internal fun rememberHideShorts(): Boolean = rememberFeedPreference(PreferenceKeys.ENABLE_HIDE_SHORTS)

@Composable
internal fun rememberHideWatched(): Boolean = rememberFeedPreference(PreferenceKeys.ENABLE_HIDE_WATCHED)

/** The watch page leaves out its related videos (LiTube #381). */
@Composable
internal fun rememberHideRelated(): Boolean = rememberFeedPreference(PreferenceKeys.ENABLE_HIDE_RELATED)

/** Player controls show the video title along the top (LiTube #338 asked to hide it). */
@Composable
internal fun rememberShowPlayerTitle(): Boolean = rememberFeedPreference(PreferenceKeys.SHOW_PLAYER_TITLE)

@Composable
private fun rememberFeedPreference(preference: String): Boolean {
    val manager = remember { GlobalContext.getOrNull()?.getOrNull<ExtensionManager>() }
    // Without a manager (previews, plain Compose tests) fall back to the setting's default:
    // reading false there hid the player title, which shows by default.
    fun read() = preferenceOrDefault(manager?.isEnabled(preference), preference)
    var hidden by remember(manager) { mutableStateOf(read()) }
    DisposableEffect(manager) {
        val listener: (String) -> Unit = { key ->
            if (key == preference || key == "*")
                hidden = read()
        }
        manager?.addOnChangedListener(listener)
        listener("*")
        onDispose { manager?.removeOnChangedListener(listener) }
    }
    return hidden
}

/** A stored value, or the setting's own default when no manager can be reached. */
internal fun preferenceOrDefault(stored: Boolean?, preference: String): Boolean =
    stored ?: (PreferenceKeys.DEFAULTS[preference] == true)
