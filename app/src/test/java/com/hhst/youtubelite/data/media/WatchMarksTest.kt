package com.hhst.youtubelite.data.media

import com.hhst.youtubelite.extension.PrefStore
import com.hhst.youtubelite.home.domain.FeedItem
import com.hhst.youtubelite.home.domain.FeedMenuAction
import com.hhst.youtubelite.home.domain.FeedMenuKind
import com.hhst.youtubelite.home.domain.FeedVisibility
import com.hhst.youtubelite.home.domain.productionActions
import com.hhst.youtubelite.home.domain.visibleActions
import com.hhst.youtubelite.home.domain.withWatchMarks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchMarksTest {
    private class MemoryStore : PrefStore {
        val values = mutableMapOf<String, Any>()
        override fun contains(key: String) = key in values
        override fun getBool(key: String, default: Boolean) = values[key] as? Boolean ?: default
        override fun putBool(key: String, value: Boolean) { values[key] = value }
        override fun getLong(key: String, default: Long) = values[key] as? Long ?: default
        override fun putLong(key: String, value: Long) { values[key] = value }
        override fun getString(key: String, default: String) = values[key] as? String ?: default
        override fun putString(key: String, value: String) { values[key] = value }
    }

    private fun video(id: String, progress: Float?) =
        FeedItem.Video(id, "Title", "Author", "", "", "1:00", "UC", progress = progress)

    @Test
    fun marksSurviveARestartAndIgnoreBadIds() {
        val store = MemoryStore()
        val marks = WatchMarks(store)
        marks.mark("aqz-KE-bpKQ", true)
        marks.mark("TLkA0RELQ1g", false)
        marks.mark("not an id", true)
        assertEquals(mapOf("aqz-KE-bpKQ" to true, "TLkA0RELQ1g" to false), WatchMarks(store).marks.value)
        assertEquals(emptyMap<String, Boolean>(), WatchMarks.decode("junk,a:1,aqz-KE-bpKQ:2"))
    }

    @Test
    fun theOldestMarksDropAtTheCap() {
        val marks = WatchMarks(MemoryStore())
        val ids = (0..WatchMarks.MAX_MARKS).map { "v%010d".format(it) }
        ids.forEach { marks.mark(it, true) }
        assertEquals(WatchMarks.MAX_MARKS, marks.marks.value.size)
        assertFalse(ids.first() in marks.marks.value)
        assertTrue(ids.last() in marks.marks.value)
    }

    @Test
    fun aWatchedMarkFillsTheBarAndHideWatchedHidesIt() {
        val items = listOf(video("aqz-KE-bpKQ", 0.2f), video("TLkA0RELQ1g", 1f), video("eRsGyueVLvQ", 0.5f))
        val marked = items.withWatchMarks(mapOf("aqz-KE-bpKQ" to true, "TLkA0RELQ1g" to false))
        assertEquals(listOf(1f, null, 0.5f), marked.map { it.progress })
        assertEquals(listOf("TLkA0RELQ1g", "eRsGyueVLvQ"),
            marked.filter(FeedVisibility(hideWatched = true)::shows).map { it.id })
        assertTrue(items.withWatchMarks(emptyMap()) === items)
    }

    @Test
    fun theMenuOffersTheOppositeOfTheCurrentState() {
        val fresh = video("aqz-KE-bpKQ", 0.2f)
        val done = video("aqz-KE-bpKQ", 1f)
        assertTrue(FeedMenuAction.MarkWatched in FeedMenuKind.Video.productionActions(video = fresh))
        assertFalse(FeedMenuAction.MarkUnwatched in FeedMenuKind.Video.productionActions(video = fresh))
        assertTrue(FeedMenuAction.MarkUnwatched in FeedMenuKind.Video.productionActions(video = done))
        val fixture = FeedMenuKind.Video.visibleActions(live = false, video = done)
        assertTrue(FeedMenuAction.MarkUnwatched in fixture)
        assertFalse(FeedMenuAction.MarkWatched in fixture)
        assertNull(FeedMenuKind.Mix.visibleActions(live = false).firstOrNull { it == FeedMenuAction.MarkWatched })
    }
}
