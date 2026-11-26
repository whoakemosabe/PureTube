package com.hhst.youtubelite.tv.browse

import com.hhst.youtubelite.data.media.firstShelf
import com.hhst.youtubelite.data.media.homeShelfTitles
import com.hhst.youtubelite.data.media.keepShelf
import com.hhst.youtubelite.home.domain.FeedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TvHomeGroupsTest {
    private fun video(id: String, group: String? = null, owner: String? = null) =
        TvCard("v-$id", id, "", "", videoId = id, group = group, ownerId = owner)

    @Test
    fun homeSplitsIntoYouTubeShelvesInOrder() {
        val cards = listOf(video("a"), video("b", "Trending"), video("c"), video("d", "Music"), video("e", "Trending"))
        val groups = homeGroups(cards)
        assertEquals(listOf("Recommended", "Trending", "Music"), groups.map { it.first })
        assertEquals(listOf("b", "c", "e"), groups[1].second.map(TvCard::title))
        assertEquals(listOf("a"), groups[0].second.map(TvCard::title))
    }

    @Test
    fun shelfTitlesKeepTheFirstShelfAndSkipUnnamed() {
        val titles = homeShelfTitles(listOf(
            "Recommended" to listOf("a", "b"),
            " " to listOf("c"),
            "Trending" to listOf("b", null, "d"),
        ))
        assertEquals(mapOf("a" to "Recommended", "b" to "Recommended", "d" to "Trending"), titles)
    }

    @Test
    fun tileMenuOffersOnlyWhatFits() {
        val playlist = TvCard("p-1", "List", "", "", playlistId = "1")
        assertEquals(listOf(TileAction.Play), tileActions(playlist, signedIn = true))
        val home = video("x", owner = "UC1")
        assertEquals(TileAction.entries.toList(), tileActions(home, signedIn = true))
        val signedOut = tileActions(home, signedIn = false)
        assertTrue(TileAction.WatchLater !in signedOut && TileAction.NotInterested !in signedOut && TileAction.Channel in signedOut)
        val search = TvCard("s-x", "x", "", "", videoId = "x")
        assertTrue(TileAction.NotInterested !in tileActions(search, signedIn = true) && TileAction.Channel !in tileActions(search, true))
    }

    private fun feed(id: String, group: String?) = FeedItem.Video(id, id, "", "", "", "", "", group = group)

    @Test
    fun signedInHomeKeepsOnlyTheMadeForYouShelf() {
        val items = listOf(feed("a", null), feed("b", "Recommended"), feed("c", "Trending"), feed("d", "Recommended"))
        assertEquals("Recommended", firstShelf(items))
        assertEquals(listOf("a", "b", "d"), keepShelf(items, "Recommended").map { it.id })
        assertEquals(items, keepShelf(items, null))
    }
}
