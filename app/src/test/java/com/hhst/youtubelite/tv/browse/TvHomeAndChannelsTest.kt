package com.hhst.youtubelite.tv.browse

import com.hhst.youtubelite.shorts.domain.ShortItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvHomeAndChannelsTest {
    private fun channel(title: String, fresh: Boolean = false) = TvCard("c-$title", title, "", "", channelId = title, fresh = fresh)

    @Test
    fun filterKeepsChannelsWithEveryWord() {
        val cards = listOf(channel("Linus Tech Tips"), channel("Tech Linked"), channel("Veritasium"))
        assertEquals(listOf("Linus Tech Tips"), arrangeChannels(cards, TvChannelSort.Recent, " tech  LINUS ").map(TvCard::title))
    }

    @Test
    fun aToZIgnoresCaseAndRecentKeepsServerOrder() {
        val cards = listOf(channel("veritasium"), channel("Apple"), channel("Mkbhd"))
        assertEquals(listOf("Apple", "Mkbhd", "veritasium"), arrangeChannels(cards, TvChannelSort.AToZ, "").map(TvCard::title))
        assertEquals(cards, arrangeChannels(cards, TvChannelSort.Recent, ""))
    }

    @Test
    fun newKeepsOnlyChannelsWithNewVideos() {
        val cards = listOf(channel("A", fresh = true), channel("B"), channel("C", fresh = true))
        assertEquals(listOf("A", "C"), arrangeChannels(cards, TvChannelSort.New, "").map(TvCard::title))
    }

    @Test
    fun signedOutHomeDropsAccountShelves() {
        val signedOut = homeShelves(signedIn = false).map(TvHomeShelf::section)
        assertFalse(signedOut.any { it.needsAccount })
        assertEquals(TvSection.Home, signedOut.first())
        assertTrue(TvSection.Shorts in signedOut)
        val signedIn = homeShelves(signedIn = true).map(TvHomeShelf::section)
        assertTrue(TvSection.History in signedIn && TvSection.Subscriptions in signedIn)
        assertTrue(homeShelves(true).single { it.section == TvSection.Shorts }.tall)
    }

    @Test
    fun shortWithoutPictureUsesItsTallFrame() {
        val card = RepositoryTvBrowseSource.shortCard(ShortItem("abc", "", "", "", "", "", "", ""))
        assertEquals("https://i.ytimg.com/vi/abc/oar2.jpg", card.artwork)
        assertTrue(card.short)
        val kept = RepositoryTvBrowseSource.shortCard(ShortItem("abc", "T", "", "", "https://x/y.jpg", "", "", ""))
        assertEquals("https://x/y.jpg", kept.artwork)
    }
}

class TvUpdateRowTest {
    @Test
    fun summarySaysWhatTheCheckFound() {
        org.junit.Assert.assertEquals("Checking…", updateSummary(com.hhst.youtubelite.update.UpdateUi.Checking, "1.0.29", null))
        org.junit.Assert.assertEquals("You have the latest version, 1.0.29.",
            updateSummary(com.hhst.youtubelite.update.UpdateUi.UpToDate("1.0.29"), "1.0.29", null))
        org.junit.Assert.assertEquals("You have 1.0.29. Press to see what's new and install.",
            updateSummary(com.hhst.youtubelite.update.UpdateUi.Idle, "1.0.29", "1.0.30"))
        org.junit.Assert.assertEquals("Offline", updateSummary(com.hhst.youtubelite.update.UpdateUi.Error("Offline"), "1.0.29", "1.0.30"))
    }
}
