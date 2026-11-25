package com.hhst.youtubelite.tv.browse

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TvBrowseModelsTest {
    private class FakeSource : TvBrowseSource {
        val playlistCalls = mutableListOf<String>()
        override val signedIn = true
        override suspend fun home(cursor: String?) = TvPage(emptyList())
        override suspend fun subscriptions(cursor: String?) = TvPage(emptyList())
        override suspend fun search(query: String, cursor: String?) = TvPage(listOf(TvCard("v-$query", query, "", "", videoId = query)))
        override suspend fun shorts(cursor: String?) = TvPage(emptyList())
        override suspend fun explore(category: String, cursor: String?) = TvPage(emptyList())
        override suspend fun channels(cursor: String?) = TvPage(emptyList())
        var subscribeGate: CompletableDeferred<Unit>? = null
        var subscribeFails = false
        val channelTabs = mutableListOf<Pair<TvChannelTab, String?>>()
        override suspend fun channel(id: String): TvChannelReads = object : TvChannelReads {
            override val header = TvChannelHeader(id, "Chan $id", subscribed = false)
            override suspend fun tab(tab: TvChannelTab, cursor: String?): TvPage {
                channelTabs += tab to cursor
                return TvPage(listOf(TvCard("v-$id-${tab.name}", tab.label, "", "", videoId = "x")))
            }
        }
        override suspend fun subscribe(channelId: String, subscribed: Boolean) {
            subscribeGate?.await()
            if (subscribeFails) throw java.io.IOException("offline")
        }
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

    @Test fun switchingSectionClosesOpenPages() = runTest {
        val models = TvBrowseModels(listScope(), FakeSource())
        models.open(TvCard("p-1", "Mix", "", "", playlistId = "1"))
        models.openChannel("UC1")
        assertEquals(2, models.opened.value.size)
        models.select(TvSection.History)
        assertTrue(models.opened.value.isEmpty())
    }

    @Test fun backClosesTheTopPageFirst() = runTest {
        val models = TvBrowseModels(listScope(), FakeSource())
        models.openChannel("UC1")
        models.open(TvCard("p-1", "Mix", "", "", playlistId = "1"))
        assertTrue(models.closeTop())
        assertTrue(models.opened.value.single() is TvOpen.Channel)
        assertTrue(models.closeTop())
        assertFalse(models.closeTop())
    }

    @Test fun aChannelCardOpensTheChannel() = runTest {
        val models = TvBrowseModels(listScope(), FakeSource())
        models.open(TvCard("c-UC1", "Chan", "", "", channelId = "UC1"))
        assertEquals("UC1", (models.opened.value.single() as TvOpen.Channel).model.channelId)
    }

    @Test fun channelTabsReadAgainstTheOpenedPage() = runTest {
        val source = FakeSource()
        val models = TvBrowseModels(listScope(), source)
        models.openChannel("UC1"); advanceUntilIdle()
        val channel = (models.opened.value.single() as TvOpen.Channel).model
        assertEquals("Chan UC1", channel.state.value.header?.name)
        channel.list(TvChannelTab.Shorts).loadFirst(); advanceUntilIdle()
        assertSame(channel.list(TvChannelTab.Shorts), channel.list(TvChannelTab.Shorts))
        assertEquals(listOf(TvChannelTab.Shorts to null), source.channelTabs)
    }

    @Test fun subscribeShowsOnlyAfterTheServerConfirms() = runTest {
        val source = FakeSource().apply { subscribeGate = CompletableDeferred() }
        val models = TvBrowseModels(listScope(), source)
        models.openChannel("UC1"); advanceUntilIdle()
        val channel = (models.opened.value.single() as TvOpen.Channel).model
        channel.toggleSubscribe(); advanceUntilIdle()
        assertEquals(false, channel.state.value.subscribed)
        source.subscribeGate!!.complete(Unit); advanceUntilIdle()
        assertEquals(true, channel.state.value.subscribed)
    }

    @Test fun aFailedSubscribeKeepsTheOldState() = runTest {
        val source = FakeSource().apply { subscribeFails = true }
        val models = TvBrowseModels(listScope(), source)
        models.openChannel("UC1"); advanceUntilIdle()
        val channel = (models.opened.value.single() as TvOpen.Channel).model
        channel.toggleSubscribe(); advanceUntilIdle()
        assertEquals(false, channel.state.value.subscribed)
        assertNotNull(channel.state.value.notice)
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
