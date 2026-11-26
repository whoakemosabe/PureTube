package com.hhst.youtubelite.tv

import com.hhst.youtubelite.comments.domain.CommentSort
import com.hhst.youtubelite.comments.domain.CommentReply
import com.hhst.youtubelite.comments.domain.CommentTarget
import com.hhst.youtubelite.comments.domain.CommentThread
import com.hhst.youtubelite.comments.domain.RepliesPage
import com.hhst.youtubelite.comments.domain.CommentsPage
import com.hhst.youtubelite.data.media.ContentRepositories
import com.hhst.youtubelite.saves.domain.SaveChoice
import com.hhst.youtubelite.watch.domain.SignInRequiredException
import com.hhst.youtubelite.watch.domain.VideoRating
import com.hhst.youtubelite.watch.domain.WatchVideo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The watch page behind the TV player: channel, description, rating, subscription and saves. */
internal data class TvVideoState(
    val videoId: String? = null,
    val video: WatchVideo? = null,
    /** The watch page didn't load after its tries; [TvVideoModel.retry] asks again. */
    val loadFailed: Boolean = false,
    val rating: VideoRating = VideoRating.None,
    val subscribed: Boolean = false,
    /** An account write is on its way; its button waits for the server. */
    val busy: Boolean = false,
    val notice: String? = null,
    /** Null until the save panel asks; then the account's playlists and whether each has the video. */
    val saveChoices: List<SaveChoice>? = null,
    val savesFailed: Boolean = false,
    val comments: TvComments = TvComments(),
    /** Like and dislike counts, each only when its setting is on. */
    val votes: com.hhst.youtubelite.watch.domain.VoteCounts? = null,
)

/** Top comments for the comments panel, paged as focus nears the end. */
internal data class TvComments(
    val threads: List<CommentThread> = emptyList(),
    val total: String = "",
    val loading: Boolean = false,
    val failed: Boolean = false,
    val turnedOff: Boolean = false,
    val next: String? = null,
    val started: Boolean = false,
    /** The comment whose replies are open, or null for the list. */
    val replyThread: CommentThread? = null,
    val replies: List<CommentReply> = emptyList(),
    val repliesLoading: Boolean = false,
    val repliesFailed: Boolean = false,
    val repliesNext: String? = null,
)

/** The account calls the TV player makes; repositories in the app, fakes in tests. */
internal interface TvVideoOps {
    suspend fun video(videoId: String): WatchVideo
    suspend fun rate(videoId: String, rating: VideoRating, previous: VideoRating)
    suspend fun subscribe(channelId: String, subscribed: Boolean)
    suspend fun saveChoices(videoId: String): List<SaveChoice>
    suspend fun setSaved(playlistId: String, videoId: String, contains: Boolean)
    suspend fun comments(videoId: String, cursor: String?): CommentsPage
    suspend fun replies(videoId: String, thread: CommentThread, cursor: String?): RepliesPage
    /** The channel's picture when the watch page left it out; null when it can't be found. */
    suspend fun avatar(channelId: String): String? = null
    /** Like and dislike counts as the settings allow; null when both are off or unknown. */
    suspend fun votes(videoId: String): com.hhst.youtubelite.watch.domain.VoteCounts? = null
}

internal class RepositoryTvVideoOps(
    private val repos: ContentRepositories,
    /** Show likes, show dislikes: the phone's two settings. */
    private val showVotes: () -> Pair<Boolean, Boolean> = { true to false },
) : TvVideoOps {
    override suspend fun votes(videoId: String): com.hhst.youtubelite.watch.domain.VoteCounts? {
        val (likes, dislikes) = showVotes()
        if (!likes && !dislikes) return null
        val counts = repos.votes.counts(videoId)
        return counts.copy(likes = counts.likes.takeIf { likes }, dislikes = counts.dislikes.takeIf { dislikes })
    }
    override suspend fun video(videoId: String) = repos.watch.watch(videoId).video
    override suspend fun rate(videoId: String, rating: VideoRating, previous: VideoRating) = repos.videoActions.rate(videoId, rating, previous)
    override suspend fun subscribe(channelId: String, subscribed: Boolean) = repos.videoActions.subscribe(channelId, subscribed)
    override suspend fun saveChoices(videoId: String) = repos.saves.choices(videoId)
    override suspend fun setSaved(playlistId: String, videoId: String, contains: Boolean) =
        repos.saves.setContaining(playlistId, videoId, contains)
    private val commentReads by lazy { repos.comments() }
    override suspend fun comments(videoId: String, cursor: String?) = commentReads.comments(videoId, CommentSort.Top, cursor)
    override suspend fun avatar(channelId: String): String? = repos.avatars.cached(channelId) ?: repos.avatars.avatar(channelId)
    override suspend fun replies(videoId: String, thread: CommentThread, cursor: String?) =
        commentReads.repliesPage(CommentTarget.Video(videoId), thread, cursor)
}

/**
 * Account actions change what the buttons show only after the server confirmed them, and
 * every answer is checked against the video still playing before it is shown.
 */
internal class TvVideoModel(private val scope: CoroutineScope, private val ops: TvVideoOps) {
    private val _state = MutableStateFlow(TvVideoState())
    val state: StateFlow<TvVideoState> = _state.asStateFlow()
    private var loadJob: Job? = null
    /** Called with the loaded page, for watch-history reporting. */
    var onVideo: ((WatchVideo) -> Unit)? = null

    fun load(videoId: String) {
        if (_state.value.videoId == videoId) return
        loadJob?.cancel()
        _state.value = TvVideoState(videoId = videoId)
        fetch(videoId)
    }

    /** Reads the watch page again when it failed (a panel that needs it is open). */
    fun retry() {
        val current = _state.value
        val videoId = current.videoId ?: return
        if (current.video != null || loadJob?.isActive == true) return
        _state.update { it.copy(loadFailed = false) }
        fetch(videoId)
    }

    private fun fetch(videoId: String) {
        loadJob = scope.launch {
            // A couple of quiet retries: one bad answer shouldn't leave the video without its page.
            var video: WatchVideo? = null
            for (wait in LOAD_RETRY_WAITS_MS) {
                if (wait > 0) kotlinx.coroutines.delay(wait)
                video = attempt { ops.video(videoId) }
                if (video != null || _state.value.videoId != videoId) break
            }
            if (_state.value.videoId != videoId) return@launch
            if (video == null) {
                _state.update { it.copy(loadFailed = true) }
                return@launch
            }
            _state.update { it.copy(video = video, loadFailed = false, rating = video.rating, subscribed = video.subscribed) }
            onVideo?.invoke(video)
            launch {
                val votes = attempt { ops.votes(videoId) } ?: return@launch
                if (_state.value.videoId == videoId) _state.update { it.copy(votes = votes) }
            }
            // The watch page often leaves the channel picture out; the phone looks it up the same way.
            if (video.avatar.isNullOrBlank() && video.channelId.startsWith("UC")) {
                val avatar = attempt { ops.avatar(video.channelId) }?.takeIf(String::isNotBlank) ?: return@launch
                if (_state.value.videoId == videoId) _state.update { state -> state.copy(video = state.video?.copy(avatar = avatar)) }
            }
        }
    }

    fun like() = rate(VideoRating.Like)
    fun dislike() = rate(VideoRating.Dislike)

    /** Pressing the lit button again takes the rating back. */
    private fun rate(target: VideoRating) {
        val current = _state.value
        val videoId = current.videoId ?: return
        if (current.busy) return waitNotice()
        val next = if (current.rating == target) VideoRating.None else target
        write(videoId) {
            ops.rate(videoId, next, current.rating)
            _state.update { it.copy(rating = next) }
        }
    }

    fun toggleSubscribe() {
        val current = _state.value
        val videoId = current.videoId ?: return
        val channelId = current.video?.channelId?.takeIf(String::isNotBlank) ?: return
        if (current.busy) return waitNotice()
        val next = !current.subscribed
        write(videoId) {
            ops.subscribe(channelId, next)
            _state.update { it.copy(subscribed = next) }
        }
    }

    fun openSaves() {
        val videoId = _state.value.videoId ?: return
        _state.update { it.copy(saveChoices = null, savesFailed = false) }
        scope.launch {
            val choices = try {
                ops.saveChoices(videoId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (_state.value.videoId == videoId) {
                    _state.update { it.copy(savesFailed = true, notice = messageFor(error)) }
                }
                return@launch
            }
            if (_state.value.videoId == videoId) _state.update { it.copy(saveChoices = choices) }
        }
    }

    fun toggleSave(choice: SaveChoice) {
        val videoId = _state.value.videoId ?: return
        if (_state.value.busy) return
        write(videoId) {
            ops.setSaved(choice.id, videoId, !choice.contains)
            _state.update { state ->
                state.copy(saveChoices = state.saveChoices?.map { if (it.id == choice.id) it.copy(contains = !choice.contains) else it })
            }
        }
    }

    private val seenCommentCursors = mutableSetOf<String>()

    /** First page when the panel opens, then the next page each call; rows stay if a page fails. */
    fun loadComments() {
        val current = _state.value
        val videoId = current.videoId ?: return
        val comments = current.comments
        if (comments.loading || comments.turnedOff) return
        if (comments.started && comments.next == null && !comments.failed) return
        val cursor = if (comments.started && !(comments.failed && comments.threads.isEmpty())) comments.next else null
        if (cursor == null) seenCommentCursors.clear()
        _state.update { it.copy(comments = it.comments.copy(loading = true, failed = false, started = true)) }
        scope.launch {
            val page = try {
                ops.comments(videoId, cursor)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (_state.value.videoId == videoId) _state.update { it.copy(comments = it.comments.copy(loading = false, failed = true)) }
                return@launch
            }
            if (_state.value.videoId != videoId) return@launch
            // A cursor seen before would loop: the list ends there.
            val following = page.nextCursor?.takeIf { seenCommentCursors.add(it) }
            _state.update { state ->
                val known = state.comments.threads.map(CommentThread::id).toSet()
                state.copy(comments = state.comments.copy(
                    threads = state.comments.threads + page.threads.filter { it.id !in known },
                    total = page.total.ifBlank { state.comments.total },
                    loading = false, turnedOff = page.turnedOff, next = following,
                ))
            }
        }
    }

    private val seenReplyCursors = mutableSetOf<String>()

    /** Opens a comment's replies, or loads their next page when that comment is already open. */
    fun loadReplies(thread: CommentThread) {
        val current = _state.value
        val videoId = current.videoId ?: return
        val open = current.comments.replyThread?.id == thread.id
        if (open && (current.comments.repliesLoading || (current.comments.repliesNext == null && !current.comments.repliesFailed))) return
        val cursor = if (open && !(current.comments.repliesFailed && current.comments.replies.isEmpty())) current.comments.repliesNext else null
        if (!open) seenReplyCursors.clear()
        _state.update { state ->
            state.copy(comments = if (open) state.comments.copy(repliesLoading = true, repliesFailed = false)
                else state.comments.copy(replyThread = thread, replies = emptyList(), repliesLoading = true, repliesFailed = false, repliesNext = null))
        }
        scope.launch {
            val page = try {
                ops.replies(videoId, thread, cursor)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (_state.value.videoId == videoId && _state.value.comments.replyThread?.id == thread.id) {
                    _state.update { it.copy(comments = it.comments.copy(repliesLoading = false, repliesFailed = true)) }
                }
                return@launch
            }
            if (_state.value.videoId != videoId || _state.value.comments.replyThread?.id != thread.id) return@launch
            val following = page.nextCursor?.takeIf { seenReplyCursors.add(it) }
            _state.update { state ->
                val known = state.comments.replies.map(CommentReply::id).toSet()
                state.copy(comments = state.comments.copy(
                    replies = state.comments.replies + page.replies.filter { it.id !in known },
                    repliesLoading = false, repliesNext = following,
                ))
            }
        }
    }

    /** Back from the replies to the comment list. */
    fun closeReplies(): Boolean {
        if (_state.value.comments.replyThread == null) return false
        _state.update { it.copy(comments = it.comments.copy(replyThread = null, replies = emptyList(), repliesLoading = false,
            repliesFailed = false, repliesNext = null)) }
        return true
    }

    fun clearNotice() = _state.update { it.copy(notice = null) }

    /** A press while YouTube is still answering the last one. */
    private fun waitNotice() = _state.update { it.copy(notice = "One moment, still saving…") }

    private fun write(videoId: String, block: suspend () -> Unit) {
        _state.update { it.copy(busy = true, notice = null) }
        scope.launch {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (_state.value.videoId == videoId) _state.update { it.copy(notice = messageFor(error)) }
            } finally {
                if (_state.value.videoId == videoId) _state.update { it.copy(busy = false) }
            }
        }
    }

    private suspend fun <T> attempt(block: suspend () -> T): T? = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    private companion object {
        val LOAD_RETRY_WAITS_MS = longArrayOf(0L, 2_000L, 5_000L)
    }

    private fun messageFor(error: Exception) = when (error) {
        is SignInRequiredException -> "Sign in from Settings to do that."
        else -> "That didn't go through. Try again."
    }
}
