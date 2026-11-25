package com.hhst.youtubelite.tv.browse

import com.hhst.youtubelite.data.media.ContentRepositories
import com.hhst.youtubelite.data.media.MediaSession
import com.hhst.youtubelite.home.domain.FeedItem
import com.hhst.youtubelite.library.domain.HistoryEntry
import com.hhst.youtubelite.playlist.domain.PlaylistEntry
import com.hhst.youtubelite.playlist.domain.PlaylistSummary
import com.hhst.youtubelite.search.domain.SearchResult
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
)

/** One page of tiles and the cursor for the next, if the list goes on. */
data class TvPage(val cards: List<TvCard>, val next: String? = null)

/** The TV's sections, in sidebar order. */
enum class TvSection(val label: String, val needsAccount: Boolean = false) {
    Search("Search"),
    Home("Home"),
    Subscriptions("Subscriptions", needsAccount = true),
    History("History", needsAccount = true),
    Playlists("Playlists", needsAccount = true),
    WatchLater("Watch later", needsAccount = true),
    Phone("Phone"),
    Settings("Settings"),
}

/** The phone's own repositories, read for TV screens. Same data, same rules, TV-shaped tiles. */
internal interface TvBrowseSource {
    val signedIn: Boolean
    suspend fun home(cursor: String?): TvPage
    suspend fun subscriptions(cursor: String?): TvPage
    suspend fun search(query: String): TvPage
    suspend fun history(cursor: String?): TvPage
    suspend fun playlists(cursor: String?): TvPage
    suspend fun playlist(id: String, cursor: String?): TvPage
    suspend fun related(videoId: String): TvPage
}

internal class RepositoryTvBrowseSource(private val repos: ContentRepositories) : TvBrowseSource {
    override val signedIn: Boolean get() = MediaSession.signedIn()

    override suspend fun home(cursor: String?): TvPage {
        val page = repos.home.loadHome("All", cursor)
        return TvPage(page.items.flatMap(::cardsOf), page.nextKey)
    }

    override suspend fun subscriptions(cursor: String?): TvPage {
        val page = repos.subscriptions.subscriptions(SubscriptionsFilter.All, cursor)
        return TvPage(page.items.filterNot { it.short }.map(::videoCard), page.nextKey)
    }

    override suspend fun search(query: String): TvPage {
        val page = repos.search.search(query)
        return TvPage(page.results.map(::searchCard) + page.playlists.map(::playlistCard))
    }

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

        internal fun cardsOf(item: FeedItem): List<TvCard> = when (item) {
            is FeedItem.Video -> if (item.short) emptyList() else listOf(videoCard(item))
            is FeedItem.Playlist -> listOf(playlistCard(item.summary))
            is FeedItem.Mix -> listOf(TvCard("mix-${item.id}", item.title, item.authors, item.artwork, playlistId = item.id))
            // Shorts and posts have their own players on the phone; the TV rows skip them for now.
            is FeedItem.Shorts, is FeedItem.Post -> emptyList()
        }

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
