package com.hhst.youtubelite.tv.browse

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TvBrowseModelsTest {
    private class FakeSource : TvBrowseSource {
        val playlistCalls = mutableListOf<String>()
        override val signedIn = true
        override suspend fun home(cursor: String?) = TvPage(emptyList())
        override suspend fun subscriptions(cursor: String?) = TvPage(emptyList())
        override suspend fun search(query: String) = TvPage(listOf(TvCard("v-$query", query, "", "", videoId = query)))
        override suspend fun history(cursor: String?) = TvPage(emptyList())
        override suspend fun playlists(cursor: String?) = TvPage(emptyList())
        override suspend fun playlist(id: String, cursor: String?): TvPage { playlistCalls += id; return TvPage(emptyList()) }
        override suspend fun related(videoId: String) = TvPage(emptyList())
    }

    @Test fun watchLaterReadsTheWatchLaterPlaylist() = runTest {
        val source = FakeSource()
        val models = TvBrowseModels(listScope(), source)
        models.list(TvSection.WatchLater).loadFirst(); advanceUntilIdle()
        assertEquals(listOf("WL"), source.playlistCalls)
    }

    @Test fun sectionListsAreKeptAndRelatedIsKeptPerVideo() = runTest {
        val models = TvBrowseModels(listScope(), FakeSource())
        assertSame(models.list(TvSection.Home), models.list(TvSection.Home))
        val related = models.related("a")
        assertSame(related, models.related("a"))
        assertNotSame(related, models.related("b"))
    }

    @Test fun switchingSectionClosesAnOpenPlaylist() = runTest {
        val models = TvBrowseModels(listScope(), FakeSource())
        models.openPlaylist(TvCard("p-1", "Mix", "", "", playlistId = "1"))
        models.select(TvSection.History)
        assertNull(models.openPlaylist.value)
    }

    @Test fun searchRunsTheTrimmedQuery() = runTest {
        val models = TvBrowseModels(listScope(), FakeSource())
        models.search("  cats ")
        models.searchResults.loadFirst(); advanceUntilIdle()
        assertEquals("cats", models.query.value)
        assertEquals(listOf("cats"), models.searchResults.state.value.cards.map { it.title })
    }
}

/** A scope on the test's clock, so advanceUntilIdle runs the list's loads. */
@OptIn(ExperimentalCoroutinesApi::class)
private fun TestScope.listScope() = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
