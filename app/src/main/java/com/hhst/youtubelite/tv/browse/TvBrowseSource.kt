package com.hhst.youtubelite.tv.browse

import com.hhst.youtubelite.data.media.ContentRepositories
import com.hhst.youtubelite.data.media.MediaSession
import com.hhst.youtubelite.home.domain.FeedItem
import com.hhst.youtubelite.home.domain.FeedVisibility
import com.hhst.youtubelite.library.domain.HistoryEntry
import com.hhst.youtubelite.playlist.domain.PlaylistEntry
import com.hhst.youtubelite.playlist.domain.PlaylistSummary
import com.hhst.youtubelite.channel.domain.ChannelVideo
import com.hhst.youtubelite.explore.domain.ExploreCategory
import com.hhst.youtubelite.search.domain.SearchChannel
import com.hhst.youtubelite.search.domain.SearchFilters
import com.hhst.youtubelite.search.domain.SearchResult
import com.hhst.youtubelite.shorts.domain.ShortItem
import com.hhst.youtubelite.subscriptions.data.SubscriptionsFilter
import com.hhst.youtubelite.watch.domain.WatchRelated

/** One tile on a TV screen: a video to play or a playlist to open. */
data class TvCard(
    val key: String,
    val title: String,
    val subtitle: String,
    val artwork: String,
    val videoId: String? = null,
    val playlistId: String? = null,
    val author: String? = null,
    val duration: String = "",
    val progress: Float? = null,
    val live: Boolean = false,
    /** A channel to open instead of a video or playlist. */
    val channelId: String? = null,
    /** A Short: a tall tile, and the player keeps going through its list. */
    val short: Boolean = false,
)

/** One page of tiles and the cursor for the next, if the list goes on. */
data class TvPage(val cards: List<TvCard>, val next: String? = null)

/** The TV's sections, in sidebar order. */
enum class TvSection(val label: String, val needsAccount: Boolean = false) {
    Search("Search"),
    Home("Home"),
    Shorts("Shorts"),
    Explore("Explore"),
    Subscriptions("Subscriptions", needsAccount = true),
    Channels("Channels", needsAccount = true),
    History("History", needsAccount = true),
    Playlists("Playlists", needsAccount = true),
    WatchLater("Watch later", needsAccount = true),
    Downloads("Downloads"),
    Phone("Phone"),
    Settings("Settings"),
}

/** The tabs of a channel page, as on YouTube TV. */
enum class TvChannelTab(val label: String) { Videos("Videos"), Shorts("Shorts"), Live("Live"), Playlists("Playlists") }

/** The top of a channel page. [subscribed] is null when the page did not say. */
data class TvChannelHeader(
    val id: String,
    val name: String,
    val handle: String = "",
    val avatar: String = "",
    val banner: String = "",
    val subscribers: String = "",
    val videoCount: String = "",
    val description: String = "",
    val subscribed: Boolean? = null,
)

/** One opened channel: its header, and its tabs read against that same opened page. */
internal interface TvChannelReads {
    val header: TvChannelHeader
    suspend fun tab(tab: TvChannelTab, cursor: String?): TvPage
}

/** The phone's own repositories, read for TV screens. Same data, same rules, TV-shaped tiles. */
internal interface TvBrowseSource {
    val signedIn: Boolean
    suspend fun home(cursor: String?): TvPage
    suspend fun subscriptions(cursor: String?): TvPage
    suspend fun shorts(cursor: String?): TvPage
    /** The Explore page for one category; its shelves come flattened into one grid. */
    suspend fun explore(category: String, cursor: String?): TvPage
    suspend fun channels(cursor: String?): TvPage
    suspend fun channel(id: String): TvChannelReads
    /** Server-confirmed: returns only after the server took the change. */
    suspend fun subscribe(channelId: String, subscribed: Boolean)
    suspend fun search(query: String, cursor: String?): TvPage
    suspend fun history(cursor: String?): TvPage
    suspend fun playlists(cursor: String?): TvPage
    suspend fun playlist(id: String, cursor: String?): TvPage
    suspend fun related(videoId: String): TvPage
}

internal class RepositoryTvBrowseSource(
    private val repos: ContentRepositories,
    /** Hide Shorts and Hide watched, read at each page so a change applies to the next read. */
    private val visibility: () -> FeedVisibility = { FeedVisibility() },
) : TvBrowseSource {
    override val signedIn: Boolean get() = MediaSession.signedIn()

    override suspend fun home(cursor: String?): TvPage {
        val page = repos.home.loadHome("All", cursor)
        return TvPage(page.items.filter(visibility()::shows).flatMap(::cardsOf), page.nextKey)
    }

    override suspend fun subscriptions(cursor: String?): TvPage {
        val page = repos.subscriptions.subscriptions(SubscriptionsFilter.All, cursor)
        val shows = visibility()
        return TvPage(page.items.filterNot { it.short }.filter(shows::shows).map(::videoCard), page.nextKey)
    }

    override suspend fun search(query: String, cursor: String?): TvPage {
        val page = if (cursor == null) repos.search.search(query) else repos.search.next(query, SearchFilters(), cursor)
        // Channels first, as YouTube shows them, then videos, Shorts and playlists.
        return TvPage(page.channels.map(::channelCard) + page.results.map(::searchCard) +
            page.shorts.map { searchCard(it).copy(key = "s-${it.id}", short = true) } + page.playlists.map(::playlistCard), page.nextCursor)
    }

    private val shortsFeed by lazy { repos.shorts(null, null) }

    override suspend fun shorts(cursor: String?): TvPage {
        val page = if (cursor == null) shortsFeed.shorts() else shortsFeed.shortsNext(cursor)
        return TvPage(page.items.map(::shortCard), page.nextKey)
    }

    override suspend fun explore(category: String, cursor: String?): TvPage {
        val which = ExploreCategory.parse(category)
        val page = if (cursor == null) repos.explore.explore(which) else repos.explore.exploreNext(which, cursor)
        val shows = visibility()
        val videos = (page.videos + page.sections.flatMap { it.videos }).filter(shows::shows)
        val playlists = page.playlists + page.sections.flatMap { it.playlists }
        return TvPage(videos.filterNot { it.short }.map(::videoCard) + playlists.map(::playlistCard), page.nextKey)
    }

    override suspend fun channels(cursor: String?): TvPage {
        val page = repos.subscriptions.subscribedChannelsPage(cursor)
        return TvPage(page.channels.map { channel ->
            TvCard("c-${channel.channelId}", channel.name, channel.metadata, channel.avatar, channelId = channel.channelId)
        }, page.nextKey)
    }

    override suspend fun channel(id: String): TvChannelReads {
        val opened = repos.channel.channel(id)
        val profile = opened.profile
        val header = TvChannelHeader(id, profile.name, profile.handle, profile.avatar, profile.banner.orEmpty(),
            profile.subscribers, profile.videoCount, profile.fullDescription.ifBlank { profile.description }, profile.subscribed)
        val channels = repos.channel
        return object : TvChannelReads {
            override val header = header
            override suspend fun tab(tab: TvChannelTab, cursor: String?): TvPage = when (tab) {
                TvChannelTab.Videos -> when {
                    cursor != null -> channels.videos(id, cursor).let { TvPage(it.videos.map(::channelVideoCard), it.nextKey) }
                    opened.videosDeferred -> channels.videos(id, opened).let { TvPage(it.videos.map(::channelVideoCard), it.nextKey) }
                    else -> TvPage(opened.videos.map(::channelVideoCard), opened.videosNextKey)
                }
                TvChannelTab.Shorts -> (if (cursor == null) channels.shorts(id, opened) else channels.shortsNext(id, cursor)).let { page ->
                    TvPage(page.shorts.map { TvCard("s-${it.id}", it.title, it.views, it.artwork, videoId = it.id, author = profile.name, short = true) }, page.nextKey)
                }
                TvChannelTab.Live -> (if (cursor == null) channels.live(id, opened) else channels.liveNext(id, cursor)).let { page ->
                    TvPage(page.all.map {
                        TvCard("l-${it.id}", it.title, listOf(it.views, it.time).filter(String::isNotBlank).joinToString(" • "), it.artwork,
                            videoId = it.id, author = profile.name, duration = it.duration, live = it.liveNow)
                    }, page.nextKey)
                }
                TvChannelTab.Playlists -> (if (cursor == null) channels.playlists(id, opened) else channels.playlistsNext(id, cursor)).let { page ->
                    TvPage(page.playlists.map(::playlistCard), page.nextKey)
                }
            }

            private fun channelVideoCard(video: ChannelVideo) = TvCard(
                "v-${video.id}", video.title, listOf(video.views, video.age).filter(String::isNotBlank).joinToString(" • "), video.artwork,
                videoId = video.id, author = profile.name, duration = video.duration, live = video.live,
            )
        }
    }

    override suspend fun subscribe(channelId: String, subscribed: Boolean) = repos.videoActions.subscribe(channelId, subscribed)

    override suspend fun history(cursor: String?): TvPage {
        val page = if (cursor == null) repos.library.history() else repos.library.historyNext(cursor)
        return TvPage(page.entries.map(::historyCard), page.nextKey)
    }

    override suspend fun playlists(cursor: String?): TvPage {
        val page = if (cursor == null) repos.library.playlists() else repos.library.playlistsNext(cursor)
        return TvPage(page.playlists.map(::playlistCard), page.nextKey)
    }

    override suspend fun playlist(id: String, cursor: String?): TvPage {
        if (cursor == null) {
            val page = repos.playlist.playlist(id) ?: return TvPage(emptyList())
            return TvPage(page.entries.map(::entryCard), page.nextKey)
        }
        val page = repos.playlist.playlistNext(id, cursor) ?: return TvPage(emptyList())
        return TvPage(page.entries.map(::entryCard), page.nextKey)
    }

    override suspend fun related(videoId: String): TvPage =
        TvPage(repos.watch.watch(videoId).related.map(::relatedCard))

    companion object {
        const val WATCH_LATER = "WL"
        /** Keys of playlist entries; a list of only these plays in order. */
        const val ENTRY_PREFIX = "e-"

        internal fun cardsOf(item: FeedItem): List<TvCard> = when (item) {
            is FeedItem.Video -> if (item.short) emptyList() else listOf(videoCard(item))
            is FeedItem.Playlist -> listOf(playlistCard(item.summary))
            is FeedItem.Mix -> listOf(TvCard("mix-${item.id}", item.title, item.authors, item.artwork, playlistId = item.id))
            // Shorts have their own section with tall tiles; posts have no TV screen.
            is FeedItem.Shorts, is FeedItem.Post -> emptyList()
        }

        internal fun channelCard(channel: SearchChannel) = TvCard(
            key = "c-${channel.id}", title = channel.name,
            subtitle = listOf(channel.handle, channel.subscribers).filter(String::isNotBlank).joinToString(" • "),
            artwork = channel.artwork, channelId = channel.id,
        )

        internal fun shortCard(item: ShortItem) = TvCard(
            key = "s-${item.id}", title = item.title, subtitle = listOf(item.author, item.views).filter(String::isNotBlank).joinToString(" • "),
            artwork = item.artwork, videoId = item.id, author = item.author, short = true,
        )

        internal fun videoCard(video: FeedItem.Video) = TvCard(
            key = "v-${video.id}", title = video.title,
            subtitle = listOf(video.author, video.metadata).filter(String::isNotBlank).joinToString(" • "),
            artwork = video.artwork, videoId = video.id, author = video.author, duration = video.duration,
            progress = video.progress, live = video.live,
        )

        internal fun searchCard(result: SearchResult) = TvCard(
            key = "v-${result.id}", title = result.title,
            subtitle = listOf(result.channel, result.views, result.age).filter(String::isNotBlank).joinToString(" • "),
            artwork = result.artwork, videoId = result.id, author = result.channel, duration = result.duration,
            progress = result.progress,
        )

        internal fun historyCard(entry: HistoryEntry) = TvCard(
            key = "h-${entry.id}", title = entry.title,
            subtitle = listOf(entry.author, entry.views).filter(String::isNotBlank).joinToString(" • "),
            artwork = entry.artwork, videoId = entry.videoId, author = entry.author, duration = entry.duration,
            progress = entry.progress,
        )

        internal fun playlistCard(summary: PlaylistSummary) = TvCard(
            key = "p-${summary.id}", title = summary.title,
            subtitle = listOfNotNull(summary.owner.takeIf(String::isNotBlank), summary.videoCount?.let { "$it videos" }).joinToString(" • "),
            artwork = summary.artwork, playlistId = summary.id,
        )

        internal fun entryCard(entry: PlaylistEntry) = TvCard(
            key = "e-${entry.id}", title = entry.title,
            subtitle = listOf(entry.channel, entry.views).filter(String::isNotBlank).joinToString(" • "),
            artwork = entry.artwork, videoId = entry.videoId, author = entry.channel, duration = entry.duration,
        )

        internal fun relatedCard(related: WatchRelated) = TvCard(
            key = "r-${related.id}", title = related.title,
            subtitle = listOf(related.channel, related.views, related.age).filter(String::isNotBlank).joinToString(" • "),
            artwork = related.artwork, videoId = related.id, author = related.channel, duration = related.duration,
            progress = related.progress,
        )
    }
}
