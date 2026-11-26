package com.hhst.youtubelite.tv.browse

import com.hhst.youtubelite.tv.ChannelWatch
import com.hhst.youtubelite.tv.ChannelWatchStats
import org.junit.Assert.assertEquals
import org.junit.Test

class TvChannelOrderTest {
    private fun channel(id: String, name: String) = TvCard("c-$id", name, "", "", channelId = id)
    private val cards = listOf(channel("a", "Alpha"), channel("b", "Bravo"), channel("c", "Charlie"))

    @Test fun mostWatchedPutsTheBiggestCountFirstThenAToZ() {
        val watched = mapOf("c" to ChannelWatch(5, 10), "b" to ChannelWatch(2, 99))
        assertEquals(listOf("Charlie", "Bravo", "Alpha"), arrangeChannels(cards, TvChannelSort.MostWatched, "", watched).map { it.title })
    }

    @Test fun recentlyWatchedPutsTheLatestFirst() {
        val watched = mapOf("c" to ChannelWatch(5, 10), "b" to ChannelWatch(2, 99))
        assertEquals(listOf("Bravo", "Charlie", "Alpha"), arrangeChannels(cards, TvChannelSort.Watched, "", watched).map { it.title })
    }

    @Test fun historyCountsBehindPlaysOnThisTv() {
        val history = listOf(
            TvCard("v-1", "x", "", "", videoId = "1", ownerId = "a"),
            TvCard("v-2", "y", "", "", videoId = "2", ownerId = "a"),
            TvCard("v-3", "z", "", "", videoId = "3", ownerId = "b"),
        )
        val merged = withHistory(mapOf("c" to ChannelWatch(1, 1_000)), history)
        assertEquals(2, merged.getValue("a").count)
        assertEquals(listOf("Charlie", "Alpha", "Bravo"), arrangeChannels(cards, TvChannelSort.Watched, "", merged).map { it.title })
        assertEquals(listOf("Alpha", "Charlie", "Bravo"), arrangeChannels(cards, TvChannelSort.MostWatched, "", merged).map { it.title })
    }

    @Test fun recordingCountsAndStores() {
        val once = ChannelWatchStats.recorded(emptyMap(), "a", 5)
        val twice = ChannelWatchStats.recorded(once, "a", 9)
        assertEquals(ChannelWatch(2, 9), twice.getValue("a"))
        assertEquals(emptyMap<String, ChannelWatch>(), ChannelWatchStats.decode("junk"))
    }
}
