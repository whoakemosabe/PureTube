package com.hhst.youtubelite.data.media

import com.hhst.youtubelite.extension.MemoryPrefStore
import com.hhst.youtubelite.home.domain.FeedItem
import com.hhst.youtubelite.home.domain.withWatchMarks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatchProgressTest {
    private val id = "abcdefghijk"
    private val other = "bcdefghijkl"

    @Test
    fun recordsPublishesOnFlushAndSurvivesARestart() {
        var now = 0L
        val store = MemoryPrefStore()
        val progress = WatchProgress(store) { now }
        now = 20_000
        progress.record(id, 30_000, 60_000)
        assertEquals(0.5f, progress.progress.value[id]!!, 0.001f)
        now = 21_000
        progress.record(id, 40_000, 60_000)
        // Within ten seconds of the last publish: not yet shown, until the video stops.
        assertEquals(0.5f, progress.progress.value[id]!!, 0.001f)
        progress.flush()
        assertEquals(0.667f, progress.progress.value[id]!!, 0.001f)
        assertEquals(0.667f, WatchProgress(store).progress.value[id]!!, 0.001f)
    }

    @Test
    fun nearTheEndCountsAsWatchedAndBarelyStartedIsSkipped() {
        val progress = WatchProgress(MemoryPrefStore()) { 0L }
        progress.record(id, 58_000, 60_000)
        progress.record(other, 100, 60_000)
        progress.record(null, 1, 2)
        progress.record("bad", 1, 2)
        progress.flush()
        assertEquals(mapOf(id to 1f), progress.progress.value)
    }

    @Test
    fun localProgressWinsUnlessTheVideoIsMarked() {
        val video = FeedItem.Video(id, "", "", "", "", "", "", progress = 0.2f)
        val plain = FeedItem.Video(other, "", "", "", "", "", "")
        val shown = listOf(video, plain).withWatchMarks(emptyMap(), mapOf(id to 0.7f, other to 0.4f))
        assertEquals(listOf(0.7f, 0.4f), shown.map { it.progress })
        assertNull(listOf(video).withWatchMarks(mapOf(id to false), mapOf(id to 0.7f)).single().progress)
        assertEquals(1f, listOf(video).withWatchMarks(mapOf(id to true), mapOf(id to 0.7f)).single().progress)
    }

    @Test
    fun decodeSkipsBrokenEntries() {
        assertEquals(mapOf(id to 0.25f), WatchProgress.decode("$id:0.250,short:0.5,$other:2,$other:x"))
    }
}
