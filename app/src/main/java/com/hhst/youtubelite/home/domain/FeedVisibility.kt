package com.hhst.youtubelite.home.domain

/**
 * Which feed cards the Hide Shorts and Hide watched settings let through. The screens draw
 * with it and the view models page with it: a page whose new cards are all hidden adds
 * nothing to look at, so it counts as empty and bounds the automatic loading
 * (LiTube #272, #260).
 */
data class FeedVisibility(val hideShorts: Boolean = false, val hideWatched: Boolean = false) {
    fun shows(item: FeedItem): Boolean = !(
        hideShorts && (item is FeedItem.Shorts || item is FeedItem.Video && item.short) ||
            hideWatched && item is FeedItem.Video && item.watched())

    fun count(items: List<FeedItem>): Int = items.count(::shows)

    companion object {
        fun fromPreferences(prefs: com.hhst.youtubelite.extension.ExtensionManager) = FeedVisibility(
            hideShorts = prefs.isEnabled(com.hhst.youtubelite.extension.PreferenceKeys.ENABLE_HIDE_SHORTS),
            hideWatched = prefs.isEnabled(com.hhst.youtubelite.extension.PreferenceKeys.ENABLE_HIDE_WATCHED),
        )
    }
}
