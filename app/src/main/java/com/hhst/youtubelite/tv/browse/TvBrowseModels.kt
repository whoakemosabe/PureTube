package com.hhst.youtubelite.tv.browse

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A playlist opened from a tile, over the section it came from. */
internal data class TvOpenPlaylist(val id: String, val title: String, val list: TvListModel)

/**
 * The lists behind every TV section, kept for the activity's life so going back to a
 * section shows the rows (and the spot) it had.
 */
internal class TvBrowseModels(private val scope: CoroutineScope, private val source: TvBrowseSource) {
    private val _section = MutableStateFlow(TvSection.Home)
    val section: StateFlow<TvSection> = _section.asStateFlow()
    private val _openPlaylist = MutableStateFlow<TvOpenPlaylist?>(null)
    val openPlaylist: StateFlow<TvOpenPlaylist?> = _openPlaylist.asStateFlow()
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val signedIn: Boolean get() = source.signedIn

    private val lists = mutableMapOf<TvSection, TvListModel>()
    private var relatedFor: String? = null
    private var relatedList: TvListModel? = null

    var searchResults: TvListModel = TvListModel(scope) { TvPage(emptyList()) }
        private set

    fun select(section: TvSection) {
        if (_section.value == section) return
        _openPlaylist.value = null
        _section.value = section
    }

    fun list(section: TvSection): TvListModel = lists.getOrPut(section) {
        TvListModel(scope) { cursor ->
            when (section) {
                TvSection.Home -> source.home(cursor)
                TvSection.Subscriptions -> source.subscriptions(cursor)
                TvSection.History -> source.history(cursor)
                TvSection.Playlists -> source.playlists(cursor)
                TvSection.WatchLater -> source.playlist(RepositoryTvBrowseSource.WATCH_LATER, cursor)
                else -> TvPage(emptyList())
            }
        }
    }

    fun search(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty() || trimmed == _query.value) return
        _query.value = trimmed
        searchResults = TvListModel(scope) { cursor -> if (cursor == null) source.search(trimmed) else TvPage(emptyList()) }
    }

    fun openPlaylist(card: TvCard) {
        val id = card.playlistId ?: return
        _openPlaylist.value = TvOpenPlaylist(id, card.title, TvListModel(scope) { cursor -> source.playlist(id, cursor) })
    }

    fun closePlaylist() {
        _openPlaylist.value = null
    }

    /** The recommended row for [videoId], reused while the same video plays. */
    fun related(videoId: String): TvListModel {
        relatedList?.takeIf { relatedFor == videoId }?.let { return it }
        return TvListModel(scope) { cursor -> if (cursor == null) source.related(videoId) else TvPage(emptyList()) }
            .also {
                relatedFor = videoId
                relatedList = it
            }
    }
}
