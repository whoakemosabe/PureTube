package com.hhst.youtubelite.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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

@Composable
internal fun rememberHideShorts(): Boolean = rememberFeedPreference(PreferenceKeys.ENABLE_HIDE_SHORTS)

@Composable
internal fun rememberHideWatched(): Boolean = rememberFeedPreference(PreferenceKeys.ENABLE_HIDE_WATCHED)

/** The watch page leaves out its related videos (LiTube #381). */
@Composable
internal fun rememberHideRelated(): Boolean = rememberFeedPreference(PreferenceKeys.ENABLE_HIDE_RELATED)

@Composable
private fun rememberFeedPreference(preference: String): Boolean {
    val manager = remember { GlobalContext.getOrNull()?.getOrNull<ExtensionManager>() }
    var hidden by remember(manager) { mutableStateOf(manager?.isEnabled(preference) == true) }
    DisposableEffect(manager) {
        val listener: (String) -> Unit = { key ->
            if (key == preference || key == "*")
                hidden = manager?.isEnabled(preference) == true
        }
        manager?.addOnChangedListener(listener)
        listener("*")
        onDispose { manager?.removeOnChangedListener(listener) }
    }
    return hidden
}
