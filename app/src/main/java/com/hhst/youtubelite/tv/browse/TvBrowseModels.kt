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
import com.hhst.youtubelite.search.domain.SearchFilters
import com.hhst.youtubelite.subscriptions.data.SubscriptionsFilter

/** A list's scroll spot and focused tile. */
internal data class TvPlace(val index: Int, val offset: Int, val focusedKey: String?)

/** A page opened from a tile, over the section it came from; Back closes the top one. */
internal sealed interface TvOpen {
    data class Playlist(val id: String, val title: String, val list: TvListModel) : TvOpen
    data class Channel(val model: TvChannelModel) : TvOpen
}

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

    /** Bumped when the TV signs in or out, so screens drop rows read as the old identity. */
    private val _identity = MutableStateFlow(0L)
    val identity: StateFlow<Long> = _identity.asStateFlow()

    val signedIn: Boolean get() = source.signedIn

    private val lists = mutableMapOf<TvSection, TvListModel>()
    private var relatedFor: String? = null
    private var relatedList: TvListModel? = null

    var searchResults: TvListModel = TvListModel(scope) { TvPage(emptyList()) }
        private set

    /** A sign-in or sign-out: every list belongs to the old account now. */
    fun identityChanged(generation: Long) {
        if (generation == _identity.value) return
        lists.clear()
        relatedFor = null
        relatedList = null
        _opened.value = emptyList()
        _query.value = ""
        searchResults = TvListModel(scope) { TvPage(emptyList()) }
        _identity.value = generation
    }

    /**
     * A setting that changes what lists show (Hide Shorts, Hide watched): the section on
     * screen reads again now, the rest when they are next shown, so the TV isn't asked for
     * every list at once.
     */
    fun reloadLists() {
        val showing = _section.value
        lists.forEach { (section, list) -> if (section == showing) list.reload() else list.markStale() }
        places.clear()
    }

    /** Reads a section again from the top; Home also refreshes every shelf on it. */
    fun refresh(section: TvSection) {
        val sections = if (section == TvSection.Home) homeShelves(signedIn).map(TvHomeShelf::section) else listOf(section)
        sections.forEach { lists[it]?.reload() }
        sections.forEach { places.remove(it.name) }
        if (section == TvSection.Home) places.remove("home")
        if (section == TvSection.Channels) _channelFilter.value = ""
    }

    /** Where each screen's list was scrolled and which tile had focus, to come back to after a video. */
    val places = mutableMapOf<String, TvPlace>()

    /** The paged list these cards were read from, so a playlist keeps paging while it plays. */
    fun sourceOf(cards: List<TvCard>): TvListModel? {
        if (cards.isEmpty()) return null
        val candidates = lists.values + searchResults + _opened.value.flatMap { page ->
            when (page) {
                is TvOpen.Playlist -> listOf(page.list)
                is TvOpen.Channel -> page.model.openLists()
            }
        }
        return candidates.firstOrNull { it.state.value.cards === cards }
            ?: candidates.firstOrNull { model -> model.state.value.cards.map(TvCard::key) == cards.map(TvCard::key) }
    }

    fun select(section: TvSection) {
        if (_section.value == section) return
        _opened.value = emptyList()
        _section.value = section
    }

    fun list(section: TvSection): TvListModel = lists.getOrPut(section) {
        TvListModel(scope) { cursor ->
            when (section) {
                TvSection.Home -> source.home(_homeFilter.value, cursor)
                TvSection.Shorts -> source.shorts(cursor)
                TvSection.Subscriptions -> source.subscriptions(_subscriptionsFilter.value, cursor)
                TvSection.Channels -> source.channels(cursor)
                TvSection.History -> source.history(cursor)
                TvSection.Playlists -> source.playlists(cursor)
                TvSection.WatchLater -> source.playlist(RepositoryTvBrowseSource.WATCH_LATER, cursor)
                else -> section.explore?.let { source.explore(it, cursor) } ?: TvPage(emptyList())
            }
        }
    }

    private val _homeFilter = MutableStateFlow(RepositoryTvBrowseSource.HOME_ALL)
    val homeFilter: StateFlow<String> = _homeFilter.asStateFlow()
    val homeFilters: List<String> get() = source.homeFilters
    private val _subscriptionsFilter = MutableStateFlow(SubscriptionsFilter.All)
    val subscriptionsFilter: StateFlow<SubscriptionsFilter> = _subscriptionsFilter.asStateFlow()
    private val _searchFilters = MutableStateFlow(SearchFilters())
    val searchFilters: StateFlow<SearchFilters> = _searchFilters.asStateFlow()
    private val _notice = MutableStateFlow<String?>(null)
    /** A short line over the screen after a tile's menu action. */
    val notice: StateFlow<String?> = _notice.asStateFlow()
    fun clearNotice() { _notice.value = null }

    /** A Home chip: Recommended reads again for it. */
    fun filterHome(filter: String) {
        if (filter == _homeFilter.value) return
        _homeFilter.value = filter
        lists.remove(TvSection.Home)
        places.remove(TvSection.Home.name)
    }

    fun filterSubscriptions(filter: SubscriptionsFilter) {
        if (filter == _subscriptionsFilter.value) return
        _subscriptionsFilter.value = filter
        lists.remove(TvSection.Subscriptions)
        places.remove(TvSection.Subscriptions.name)
    }

    fun search(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty() || trimmed == _query.value) return
        _query.value = trimmed
        source.recordSearch(trimmed)
        searchResults = searchList(trimmed, _searchFilters.value)
    }

    fun filterSearch(filters: SearchFilters) {
        if (filters == _searchFilters.value) return
        _searchFilters.value = filters
        val query = _query.value
        if (query.isNotBlank()) searchResults = searchList(query, filters)
    }

    private fun searchList(query: String, filters: SearchFilters) = TvListModel(scope) { cursor -> source.search(query, filters, cursor) }

    suspend fun searchRows(text: String) = source.searchRows(text)

    private val subscriptions = mutableMapOf<String, Boolean>()
    private val _menuSubscribed = MutableStateFlow<Boolean?>(null)
    /** For the open tile menu: whether its channel is subscribed; null while looking it up. */
    val menuSubscribed: StateFlow<Boolean?> = _menuSubscribed.asStateFlow()
    private var menuChannel: String? = null

    /** A tile's menu opened: find out whether its channel is subscribed, for the Subscribe row. */
    fun lookUpSubscription(card: TvCard) {
        val channelId = card.ownerId?.takeIf(String::isNotBlank)
        menuChannel = channelId
        _menuSubscribed.value = channelId?.let(subscriptions::get)
        if (channelId == null || !source.signedIn || channelId in subscriptions) return
        scope.launch {
            val subscribed = try {
                source.isSubscribed(channelId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            } ?: return@launch
            subscriptions[channelId] = subscribed
            if (menuChannel == channelId) _menuSubscribed.value = subscribed
        }
    }

    /** A tile's menu: Subscribe or Unsubscribe to its channel. Says so once YouTube took it. */
    fun toggleSubscription(card: TvCard) {
        val channelId = card.ownerId?.takeIf(String::isNotBlank) ?: return
        val next = subscriptions[channelId] != true
        val name = card.author?.takeIf(String::isNotBlank) ?: "the channel"
        scope.launch {
            _notice.value = try {
                source.subscribe(channelId, next)
                subscriptions[channelId] = next
                if (menuChannel == channelId) _menuSubscribed.value = next
                lists[TvSection.Subscriptions]?.markStale()
                if (next) "Subscribed to $name" else "Unsubscribed from $name"
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (error is SignInRequiredException) "Sign in to subscribe." else "That didn't go through. Try again."
            }
        }
    }

    /** A tile's menu: Dislike. Says so once YouTube took it. */
    fun dislike(card: TvCard) {
        val videoId = card.videoId ?: return
        scope.launch {
            _notice.value = try {
                source.dislike(videoId)
                "Disliked. You'll see fewer like this."
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (error is SignInRequiredException) "Sign in to rate videos." else "That didn't go through. Try again."
            }
        }
    }

    /** A tile's menu: Save to Watch later. Says so once the server took it. */
    fun saveToWatchLater(card: TvCard) {
        val videoId = card.videoId ?: return
        scope.launch {
            _notice.value = try {
                source.saveToWatchLater(videoId)
                lists[TvSection.WatchLater]?.markStale()
                "Saved to Watch later"
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (error is SignInRequiredException) "Sign in to save videos." else "Couldn't save that. Try again."
            }
        }
    }

    /** A tile's menu: Not interested. The tile leaves Home only after YouTube took it. */
    fun notInterested(card: TvCard) {
        scope.launch {
            _notice.value = try {
                if (source.notInterested(card)) {
                    lists[TvSection.Home]?.remove(card.key)
                    "Got it. You'll see fewer like this."
                } else "That isn't available for this video."
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                "That didn't go through. Try again."
            }
        }
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

    private val _channelSort = MutableStateFlow(TvChannelSort.AToZ)
    val channelSort: StateFlow<TvChannelSort> = _channelSort.asStateFlow()
    private val _channelFilter = MutableStateFlow("")
    val channelFilter: StateFlow<String> = _channelFilter.asStateFlow()

    fun sortChannels(sort: TvChannelSort) { _channelSort.value = sort }
    fun filterChannels(text: String) { _channelFilter.value = text }

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

/** How the Channels section orders your subscriptions. */
internal enum class TvChannelSort(val label: String) { AToZ("A–Z"), Recent("Recently active"), New("New videos") }

/** Your subscriptions as Channels shows them: filtered by name, then ordered. */
internal fun arrangeChannels(cards: List<TvCard>, sort: TvChannelSort, filter: String): List<TvCard> {
    val words = filter.trim().lowercase().split(' ').filter(String::isNotBlank)
    val matching = cards.filter { card -> words.all { card.title.lowercase().contains(it) } }
    return when (sort) {
        TvChannelSort.AToZ -> matching.sortedBy { it.title.lowercase() }
        // The server lists channels by recent activity already.
        TvChannelSort.Recent -> matching
        TvChannelSort.New -> matching.filter(TvCard::fresh)
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

    /** The tab lists read so far. */
    fun openLists(): List<TvListModel> = tabs.values.toList()

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
