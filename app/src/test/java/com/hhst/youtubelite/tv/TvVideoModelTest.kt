package com.hhst.youtubelite.tv

import com.hhst.youtubelite.comments.domain.CommentAuthor
import com.hhst.youtubelite.comments.domain.CommentReply
import com.hhst.youtubelite.comments.domain.CommentThread
import com.hhst.youtubelite.comments.domain.RepliesPage
import com.hhst.youtubelite.tv.browse.TvCard
import com.hhst.youtubelite.comments.domain.CommentsPage
import com.hhst.youtubelite.player.SleepChoice
import com.hhst.youtubelite.player.engine.PlaybackSnapshot
import com.hhst.youtubelite.player.engine.SubtitleTrack
import com.hhst.youtubelite.saves.domain.SaveChoice
import com.hhst.youtubelite.watch.domain.SignInRequiredException
import com.hhst.youtubelite.watch.domain.VideoRating
import com.hhst.youtubelite.watch.domain.WatchComment
import com.hhst.youtubelite.watch.domain.WatchVideo
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TvVideoModelTest {
    private fun video(id: String) = WatchVideo(id, "Title $id", "Chan", "@chan", "UC1", "", "1K", "10K views", "1 day ago", "",
        WatchComment("", "", ""), "About $id")

    private class FakeOps(val pages: Map<String, CompletableDeferred<WatchVideo>> = emptyMap()) : TvVideoOps {
        val rates = mutableListOf<VideoRating>()
        var fail: Exception? = null
        var gate: CompletableDeferred<Unit>? = null
        var choices = listOf(SaveChoice("WL", "Watch later", false))
        val saved = mutableListOf<Pair<String, Boolean>>()
        override suspend fun video(videoId: String) = pages.getValue(videoId).await()
        override suspend fun rate(videoId: String, rating: VideoRating, previous: VideoRating) {
            gate?.await()
            fail?.let { throw it }
            rates += rating
        }
        override suspend fun subscribe(channelId: String, subscribed: Boolean) { fail?.let { throw it } }
        override suspend fun saveChoices(videoId: String) = choices
        override suspend fun setSaved(playlistId: String, videoId: String, contains: Boolean) { saved += playlistId to contains }
        /** Pages by cursor (null is the first); a missing cursor fails. */
        var commentPages: Map<String?, CommentsPage> = emptyMap()
        val commentCursors = mutableListOf<String?>()
        var replyPages: Map<String?, RepliesPage> = emptyMap()
        override suspend fun replies(videoId: String, thread: CommentThread, cursor: String?) =
            replyPages[cursor] ?: throw IOException("offline")
        override suspend fun comments(videoId: String, cursor: String?): CommentsPage {
            commentCursors += cursor
            return commentPages[cursor] ?: throw IOException("offline")
        }
    }

    private fun reply(id: String) = CommentReply(id, CommentAuthor("Name", "@name", ""), "1 day ago", "Reply $id", "")

    private fun thread(id: String) = CommentThread(id, CommentAuthor("Name", "@name", ""), "1 day ago", "Body $id", "3", 0)

    private fun TestScope.loaded(ops: FakeOps, id: String = "a"): TvVideoModel {
        val model = TvVideoModel(CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)), ops)
        model.load(id)
        advanceUntilIdle()
        return model
    }

    private fun ready(vararg ids: String) = ids.associateWith { id -> CompletableDeferred(video(id)) }

    @Test fun aLikeShowsOnlyAfterTheServerConfirmsIt() = runTest {
        val ops = FakeOps(ready("a")).apply { gate = CompletableDeferred() }
        val model = loaded(ops)
        model.like(); advanceUntilIdle()
        assertEquals(VideoRating.None, model.state.value.rating)
        assertTrue(model.state.value.busy)
        ops.gate!!.complete(Unit); advanceUntilIdle()
        assertEquals(VideoRating.Like, model.state.value.rating)
        assertFalse(model.state.value.busy)
    }

    @Test fun pressingLikeAgainTakesItBack() = runTest {
        val ops = FakeOps(ready("a"))
        val model = loaded(ops)
        model.like(); advanceUntilIdle()
        model.like(); advanceUntilIdle()
        assertEquals(listOf(VideoRating.Like, VideoRating.None), ops.rates)
        assertEquals(VideoRating.None, model.state.value.rating)
    }

    @Test fun aSignedOutTvIsToldToSignIn() = runTest {
        val ops = FakeOps(ready("a")).apply { fail = SignInRequiredException() }
        val model = loaded(ops)
        model.toggleSubscribe(); advanceUntilIdle()
        assertFalse(model.state.value.subscribed)
        assertEquals("Sign in from Settings to do that.", model.state.value.notice)
    }

    @Test fun aFailedWriteKeepsTheOldState() = runTest {
        val ops = FakeOps(ready("a")).apply { fail = IOException("offline") }
        val model = loaded(ops)
        model.dislike(); advanceUntilIdle()
        assertEquals(VideoRating.None, model.state.value.rating)
        assertNotNull(model.state.value.notice)
    }

    @Test fun anOldVideosPageNeverReplacesTheCurrentOne() = runTest {
        val slow = CompletableDeferred<WatchVideo>()
        val ops = FakeOps(mapOf("a" to slow, "b" to CompletableDeferred(video("b"))))
        val model = TvVideoModel(CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)), ops)
        model.load("a"); advanceUntilIdle()
        model.load("b"); advanceUntilIdle()
        slow.complete(video("a")); advanceUntilIdle()
        assertEquals("b", model.state.value.video?.id)
    }

    @Test fun savingFlipsThePlaylistAfterTheWrite() = runTest {
        val ops = FakeOps(ready("a"))
        val model = loaded(ops)
        model.openSaves(); advanceUntilIdle()
        model.toggleSave(model.state.value.saveChoices!!.single()); advanceUntilIdle()
        assertEquals(listOf("WL" to true), ops.saved)
        assertTrue(model.state.value.saveChoices!!.single().contains)
    }

    @Test fun aFailedCommentsPageKeepsTheRowsAndRetriesTheSameCursor() = runTest {
        val ops = FakeOps(ready("a")).apply { commentPages = mapOf(null to CommentsPage("2", listOf(thread("1")), "c2")) }
        val model = loaded(ops)
        model.loadComments(); advanceUntilIdle()
        model.loadComments(); advanceUntilIdle()
        assertEquals(listOf("1"), model.state.value.comments.threads.map { it.id })
        assertTrue(model.state.value.comments.failed)
        ops.commentPages += "c2" to CommentsPage("2", listOf(thread("2")), null)
        model.loadComments(); advanceUntilIdle()
        assertEquals(listOf(null, "c2", "c2"), ops.commentCursors)
        assertEquals(listOf("1", "2"), model.state.value.comments.threads.map { it.id })
    }

    @Test fun aRepeatedCommentsCursorEndsTheList() = runTest {
        val ops = FakeOps(ready("a")).apply {
            commentPages = mapOf(null to CommentsPage("", listOf(thread("1")), "c2"), "c2" to CommentsPage("", listOf(thread("2")), "c2"))
        }
        val model = loaded(ops)
        repeat(4) { model.loadComments(); advanceUntilIdle() }
        assertEquals(listOf(null, "c2"), ops.commentCursors)
        assertEquals(null, model.state.value.comments.next)
    }

    @Test fun repliesOpenPageAndCloseBackToTheList() = runTest {
        val ops = FakeOps(ready("a")).apply {
            commentPages = mapOf(null to CommentsPage("", listOf(thread("1")), null))
            replyPages = mapOf(null to RepliesPage(listOf(reply("r1")), "n"), "n" to RepliesPage(listOf(reply("r2")), "n"))
        }
        val model = loaded(ops)
        model.loadComments(); advanceUntilIdle()
        val parent = model.state.value.comments.threads.single()
        model.loadReplies(parent); advanceUntilIdle()
        model.loadReplies(parent); advanceUntilIdle()
        model.loadReplies(parent); advanceUntilIdle()
        assertEquals(listOf("r1", "r2"), model.state.value.comments.replies.map { it.id })
        assertEquals(null, model.state.value.comments.repliesNext)
        assertTrue(model.closeReplies())
        assertEquals(null, model.state.value.comments.replyThread)
        assertEquals(listOf("1"), model.state.value.comments.threads.map { it.id })
        assertFalse(model.closeReplies())
    }

    @Test fun shortsAndPlaylistsPlayInOrderOtherListsDont() {
        val shorts = listOf(TvCard("s-1", "", "", "", videoId = "1", short = true), TvCard("s-2", "", "", "", videoId = "2", short = true))
        assertEquals(shorts, playQueue(shorts[0], shorts))
        val entries = listOf(TvCard("e-1", "", "", "", videoId = "1"), TvCard("e-2", "", "", "", videoId = "2"))
        assertEquals(entries, playQueue(entries[0], entries))
        val home = listOf(TvCard("v-1", "", "", "", videoId = "1"), TvCard("v-2", "", "", "", videoId = "2"))
        assertTrue(playQueue(home[0], home).isEmpty())
    }

    @Test fun settingsHideTheAudioRowWithOneTrackAndCapSpeedAtTwo() {
        val snapshot = PlaybackSnapshot(qualities = listOf("1080p", "720p"), speed = 1.5f,
            subtitleTracks = listOf(SubtitleTrack("en", autoGenerated = true)))
        assertEquals(listOf("Quality", "Playback speed", "Captions", "Sleep timer"), settingsRows(snapshot, SleepChoice.Off).map { it.label })
        assertTrue(speedRows(snapshot).single { it.checked }.label == "1.5x")
        assertTrue(speedRows(snapshot).all { it.key.toFloat() <= 2f })
        assertEquals(listOf("Auto", "1080p", "720p"), qualityRows(snapshot).map { it.label })
        assertEquals("Off", captionRows(snapshot).first { it.checked }.label)
    }

    @Test fun sleepKeysRoundTrip() {
        SleepChoice.choices.forEach { choice ->
            val row = sleepRows(choice).single { it.checked }
            assertEquals(choice, sleepChoiceOf(row.key))
        }
    }
}
