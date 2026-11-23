package com.hhst.youtubelite.home.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** Settings > Feed layout on Home and Subscriptions. */
class FeedLayoutTest {
    @Test fun storedLayoutsKeepTheirPositions() =
        assertEquals(listOf(FeedLayout.Cards, FeedLayout.List, FeedLayout.Grid), FeedLayout.entries)

    @Test fun aPhoneShowsOneCardOneRowOrTwoGridColumns() {
        val phone = 390f
        val cards = HomeLayout.columns(phone)
        assertEquals(1, FeedLayout.Cards.columns(cards, phone))
        assertEquals(1, FeedLayout.List.columns(cards, phone))
        assertEquals(2, FeedLayout.Grid.columns(cards, phone))
    }

    @Test fun wideScreensKeepCardColumnsForRowsAndDoubleThemForTheGrid() {
        val tablet = 1000f
        val cards = HomeLayout.columns(tablet)
        assertEquals(cards, FeedLayout.List.columns(cards, tablet))
        assertEquals(cards * 2, FeedLayout.Grid.columns(cards, tablet))
    }

    // Phone cards run edge to edge; rows and the grid would touch the screen edge without a margin.
    @Test fun onlyCardsRunEdgeToEdge() {
        assertEquals(0f, FeedLayout.Cards.outerPadding(0f))
        assertEquals(12f, FeedLayout.List.outerPadding(0f))
        assertEquals(12f, FeedLayout.Grid.outerPadding(0f))
        assertEquals(16f, FeedLayout.Grid.outerPadding(16f))
        assertEquals(10f, FeedLayout.Grid.columnGap(16f))
        assertEquals(16f, FeedLayout.List.columnGap(16f))
    }
}
