package com.hhst.youtubelite.tv.browse

import com.hhst.youtubelite.data.media.homeShelfTitles
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
        assertEquals(TileAction.entries - TileAction.StartOver, tileActions(home, signedIn = true))
        assertTrue(TileAction.StartOver in tileActions(home.copy(progress = 0.4f), signedIn = true))
        assertTrue(TileAction.Subscribe !in tileActions(home, signedIn = false))
        val signedOut = tileActions(home, signedIn = false)
        assertTrue(TileAction.WatchLater !in signedOut && TileAction.NotInterested !in signedOut && TileAction.Channel in signedOut)
        val search = TvCard("s-x", "x", "", "", videoId = "x")
        assertTrue(TileAction.NotInterested !in tileActions(search, signedIn = true) && TileAction.Channel !in tileActions(search, true))
    }

    @Test
    fun recommendedDropsYouTubesGeneralShelves() {
        val cards = listOf(video("a"), video("b", "Recommended"), video("c", "Trending"), video("d", "Music videos for you"),
            video("e", "Because you watched X"), video("f", "Breaking news"))
        assertEquals(listOf("a", "b", "e"), personalCards(cards).map(TvCard::title))
    }
}
