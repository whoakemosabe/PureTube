package com.hhst.youtubelite.tv.browse

import com.hhst.youtubelite.watch.domain.SignInRequiredException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A page opened from a tile, over the section it came from; Back closes the top one. */
internal sealed interface TvOpen {
    data class Playlist(val id: String, val title: String, val list: TvListModel) : TvOpen
    data class Channel(val model: TvChannelModel) : TvOpen
}

/** Explore's categories, as on the phone. */
internal val EXPLORE_CATEGORIES = listOf("Music", "Gaming", "News", "Sports")

/**
 * The lists behind every TV section, kept for the activity's life so going back to a
 * section shows the rows (and the spot) it had.
 */
internal class TvBrowseModels(private val scope: CoroutineScope, private val source: TvBrowseSource) {
    private val _section = MutableStateFlow(TvSection.Home)
    val section: StateFlow<TvSection> = _section.asStateFlow()
    private val _opened = MutableStateFlow<List<TvOpen>>(emptyList())
    /** Pages opened over the section, oldest first. */
    val opened: StateFlow<List<TvOpen>> = _opened.asStateFlow()
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    private val _exploreCategory = MutableStateFlow(EXPLORE_CATEGORIES.first())
    val exploreCategory: StateFlow<String> = _exploreCategory.asStateFlow()

    /** Bumped when the TV signs in or out, so screens drop rows read as the old identity. */
    private val _identity = MutableStateFlow(0L)
    val identity: StateFlow<Long> = _identity.asStateFlow()

    val signedIn: Boolean get() = source.signedIn

    private val lists = mutableMapOf<TvSection, TvListModel>()
    private val exploreLists = mutableMapOf<String, TvListModel>()
    private var relatedFor: String? = null
    private var relatedList: TvListModel? = null

    var searchResults: TvListModel = TvListModel(scope) { TvPage(emptyList()) }
        private set

    /** A sign-in or sign-out: every list belongs to the old account now. */
    fun identityChanged(generation: Long) {
        if (generation == _identity.value) return
        lists.clear()
        exploreLists.clear()
        relatedFor = null
        relatedList = null
        _opened.value = emptyList()
        _query.value = ""
        searchResults = TvListModel(scope) { TvPage(emptyList()) }
        _identity.value = generation
    }

    /** A setting that changes what lists show (Hide Shorts, Hide watched): read them again. */
    fun reloadLists() {
        lists.values.forEach(TvListModel::reload)
        exploreLists.values.forEach(TvListModel::reload)
    }

    fun select(section: TvSection) {
        if (_section.value == section) return
        _opened.value = emptyList()
        _section.value = section
    }

    fun list(section: TvSection): TvListModel = lists.getOrPut(section) {
        TvListModel(scope) { cursor ->
            when (section) {
                TvSection.Home -> source.home(cursor)
                TvSection.Shorts -> source.shorts(cursor)
                TvSection.Subscriptions -> source.subscriptions(cursor)
                TvSection.Channels -> source.channels(cursor)
                TvSection.History -> source.history(cursor)
                TvSection.Playlists -> source.playlists(cursor)
                TvSection.WatchLater -> source.playlist(RepositoryTvBrowseSource.WATCH_LATER, cursor)
                else -> TvPage(emptyList())
            }
        }
    }

    fun selectExplore(category: String) {
        if (category in EXPLORE_CATEGORIES) _exploreCategory.value = category
    }

    fun explore(category: String): TvListModel = exploreLists.getOrPut(category) {
        TvListModel(scope) { cursor -> source.explore(category, cursor) }
    }

    fun search(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty() || trimmed == _query.value) return
        _query.value = trimmed
        searchResults = TvListModel(scope) { cursor -> source.search(trimmed, cursor) }
    }

    /** Opens what a tile points at that isn't a video: a playlist or a channel. */
    fun open(card: TvCard) {
        when {
            card.channelId != null -> openChannel(card.channelId)
            card.playlistId != null -> {
                val id = card.playlistId
                push(TvOpen.Playlist(id, card.title, TvListModel(scope) { cursor -> source.playlist(id, cursor) }))
            }
        }
    }

    fun openChannel(channelId: String) {
        if (channelId.isBlank()) return
        val top = _opened.value.lastOrNull()
        if (top is TvOpen.Channel && top.model.channelId == channelId) return
        push(TvOpen.Channel(TvChannelModel(scope, source, channelId)))
    }

    private fun push(page: TvOpen) = _opened.update { (it + page).takeLast(MAX_OPEN) }

    /** Back: closes the top page. False when nothing was open. */
    fun closeTop(): Boolean {
        if (_opened.value.isEmpty()) return false
        _opened.update { it.dropLast(1) }
        return true
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

    private companion object {
        /** Channel to playlist to channel… stays this deep; older pages drop off. */
        const val MAX_OPEN = 8
    }
}

internal data class TvChannelState(
    val header: TvChannelHeader? = null,
    val loading: Boolean = true,
    val failed: Boolean = false,
    val tab: TvChannelTab = TvChannelTab.Videos,
    /** Null until the page says; changes only after the server confirms. */
    val subscribed: Boolean? = null,
    val busy: Boolean = false,
    val notice: String? = null,
)

/** One channel page: the header read once, each tab paged against that same opened page. */
internal class TvChannelModel(private val scope: CoroutineScope, private val source: TvBrowseSource, val channelId: String) {
    private val _state = MutableStateFlow(TvChannelState())
    val state: StateFlow<TvChannelState> = _state.asStateFlow()
    private var reads: TvChannelReads? = null
    private val tabs = mutableMapOf<TvChannelTab, TvListModel>()
    private var job: Job? = null

    init { load() }

    fun load() {
        job?.cancel()
        _state.update { it.copy(loading = true, failed = false) }
        job = scope.launch {
            val opened = try {
                source.channel(channelId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _state.update { it.copy(loading = false, failed = true) }
                return@launch
            }
            reads = opened
            tabs.clear()
            _state.update { it.copy(header = opened.header, loading = false, subscribed = opened.header.subscribed) }
        }
    }

    fun selectTab(tab: TvChannelTab) = _state.update { it.copy(tab = tab) }

    /** The tab's list; empty until the header is in. */
    fun list(tab: TvChannelTab): TvListModel {
        val opened = reads ?: return TvListModel(scope) { TvPage(emptyList()) }
        return tabs.getOrPut(tab) { TvListModel(scope) { cursor -> opened.tab(tab, cursor) } }
    }

    fun toggleSubscribe() {
        val current = _state.value
        if (current.busy || current.header == null) return
        val next = current.subscribed != true
        _state.update { it.copy(busy = true, notice = null) }
        scope.launch {
            try {
                source.subscribe(channelId, next)
                _state.update { it.copy(subscribed = next) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update {
                    it.copy(notice = if (error is SignInRequiredException) "Sign in from Settings to subscribe."
                        else "That didn't go through. Try again.")
                }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }
}
