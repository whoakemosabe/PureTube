package com.hhst.youtubelite.tv

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
    val rating: VideoRating = VideoRating.None,
    val subscribed: Boolean = false,
    /** An account write is on its way; its button waits for the server. */
    val busy: Boolean = false,
    val notice: String? = null,
    /** Null until the save panel asks; then the account's playlists and whether each has the video. */
    val saveChoices: List<SaveChoice>? = null,
    val savesFailed: Boolean = false,
)

/** The account calls the TV player makes; repositories in the app, fakes in tests. */
internal interface TvVideoOps {
    suspend fun video(videoId: String): WatchVideo
    suspend fun rate(videoId: String, rating: VideoRating, previous: VideoRating)
    suspend fun subscribe(channelId: String, subscribed: Boolean)
    suspend fun saveChoices(videoId: String): List<SaveChoice>
    suspend fun setSaved(playlistId: String, videoId: String, contains: Boolean)
}

internal class RepositoryTvVideoOps(private val repos: ContentRepositories) : TvVideoOps {
    override suspend fun video(videoId: String) = repos.watch.watch(videoId).video
    override suspend fun rate(videoId: String, rating: VideoRating, previous: VideoRating) = repos.videoActions.rate(videoId, rating, previous)
    override suspend fun subscribe(channelId: String, subscribed: Boolean) = repos.videoActions.subscribe(channelId, subscribed)
    override suspend fun saveChoices(videoId: String) = repos.saves.choices(videoId)
    override suspend fun setSaved(playlistId: String, videoId: String, contains: Boolean) =
        repos.saves.setContaining(playlistId, videoId, contains)
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
        loadJob = scope.launch {
            val video = attempt { ops.video(videoId) } ?: return@launch
            if (_state.value.videoId != videoId) return@launch
            _state.update { it.copy(video = video, rating = video.rating, subscribed = video.subscribed) }
            onVideo?.invoke(video)
        }
    }

    fun like() = rate(VideoRating.Like)
    fun dislike() = rate(VideoRating.Dislike)

    /** Pressing the lit button again takes the rating back. */
    private fun rate(target: VideoRating) {
        val current = _state.value
        val videoId = current.videoId ?: return
        if (current.busy) return
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
        if (current.busy) return
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

    fun clearNotice() = _state.update { it.copy(notice = null) }

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

    private fun messageFor(error: Exception) = when (error) {
        is SignInRequiredException -> "Sign in from Settings to do that."
        else -> "That didn't go through. Try again."
    }
}
