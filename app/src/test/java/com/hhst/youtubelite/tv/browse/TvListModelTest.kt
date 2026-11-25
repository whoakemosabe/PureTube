package com.hhst.youtubelite.tv.browse

import com.hhst.youtubelite.watch.domain.SignInRequiredException
import java.io.IOException
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
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TvListModelTest {
    private fun card(id: String) = TvCard(key = "v-$id", title = id, subtitle = "", artwork = "", videoId = id)

    private class Pages(val pages: Map<String?, TvPage>) {
        val calls = mutableListOf<String?>()
        var fail = false
        suspend fun load(cursor: String?): TvPage {
            calls += cursor
            if (fail) throw IOException("offline")
            return pages.getValue(cursor)
        }
    }

    private fun TestScope.model(pages: Pages) = TvListModel(listScope()) { pages.load(it) }

    @Test fun pagesAppendAndTheListEndsWithoutACursor() = runTest {
        val pages = Pages(mapOf(null to TvPage(listOf(card("a")), "c1"), "c1" to TvPage(listOf(card("b")))))
        val model = model(pages)
        model.loadFirst(); advanceUntilIdle()
        model.loadMore(); advanceUntilIdle()
        model.loadMore(); advanceUntilIdle()
        assertEquals(listOf("a", "b"), model.state.value.cards.map { it.title })
        assertTrue(model.state.value.done)
        assertEquals(listOf(null, "c1"), pages.calls)
    }

    @Test fun aFailedAppendKeepsTheRowsAndTheCursorForARetry() = runTest {
        val pages = Pages(mapOf(null to TvPage(listOf(card("a")), "c1"), "c1" to TvPage(listOf(card("b")))))
        val model = model(pages)
        model.loadFirst(); advanceUntilIdle()
        pages.fail = true
        model.loadMore(); advanceUntilIdle()
        assertEquals(listOf("a"), model.state.value.cards.map { it.title })
        assertTrue(model.state.value.appendFailed)
        pages.fail = false
        model.loadMore(); advanceUntilIdle()
        assertEquals(listOf("a", "b"), model.state.value.cards.map { it.title })
        assertEquals(listOf(null, "c1", "c1"), pages.calls)
    }

    @Test fun aRepeatedCursorEndsTheList() = runTest {
        val pages = Pages(mapOf(null to TvPage(listOf(card("a")), "c1"), "c1" to TvPage(listOf(card("b")), "c1")))
        val model = model(pages)
        model.loadFirst(); advanceUntilIdle()
        repeat(3) { model.loadMore(); advanceUntilIdle() }
        assertEquals(listOf(null, "c1"), pages.calls)
        assertTrue(model.state.value.done)
    }

    @Test fun duplicateTilesAcrossPagesShowOnce() = runTest {
        val pages = Pages(mapOf(null to TvPage(listOf(card("a"), card("b")), "c1"), "c1" to TvPage(listOf(card("b"), card("c")))))
        val model = model(pages)
        model.loadFirst(); advanceUntilIdle()
        model.loadMore(); advanceUntilIdle()
        assertEquals(listOf("a", "b", "c"), model.state.value.cards.map { it.title })
    }

    @Test fun signInFailuresAreMarkedForTheSignInMessage() = runTest {
        val model = TvListModel(listScope()) { throw SignInRequiredException() }
        model.loadFirst(); advanceUntilIdle()
        assertTrue(model.state.value.failed)
        assertTrue(model.state.value.needsAccount)
    }

    @Test fun aReloadDropsTheAnswerToTheOlderRequest() = runTest {
        val slow = CompletableDeferred<TvPage>()
        var first = true
        val model = TvListModel(listScope()) {
            if (first) { first = false; slow.await() } else TvPage(listOf(card("new")))
        }
        model.loadFirst(); advanceUntilIdle()
        model.reload(); advanceUntilIdle()
        slow.complete(TvPage(listOf(card("old")))); advanceUntilIdle()
        assertEquals(listOf("new"), model.state.value.cards.map { it.title })
        assertFalse(model.state.value.loading)
    }

    @Test fun loadFirstKeepsALoadedList() = runTest {
        val pages = Pages(mapOf(null to TvPage(listOf(card("a")))))
        val model = model(pages)
        model.loadFirst(); advanceUntilIdle()
        model.loadFirst(); advanceUntilIdle()
        assertEquals(listOf<String?>(null), pages.calls)
    }
}

/** A scope on the test's clock, so advanceUntilIdle runs the list's loads. */
@OptIn(ExperimentalCoroutinesApi::class)
private fun TestScope.listScope() = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
