package com.hhst.youtubelite.tv

import android.content.Context
import com.hhst.youtubelite.player.engine.PlaybackApi
import com.hhst.youtubelite.player.engine.PlaybackSnapshot
import com.hhst.youtubelite.tvlink.TvLinkMessage
import com.hhst.youtubelite.tvlink.TvLinkStore
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** The video the phone handed over, as the TV shows it. */
data class TvNowPlaying(val videoId: String, val title: String, val author: String?)

/**
 * Plays what the phone sends on this TV's own player and reports back. The TV fetches and
 * plays the video itself with the same engine as the phone, so the phone only ever sends
 * a video id and a position.
 */
internal class TvReceiver(context: Context, private val engine: PlaybackApi, store: TvLinkStore) {
    val server = TvLinkServer(context, store, ::onCommand, onPhoneLeft = {})
    private val _nowPlaying = MutableStateFlow<TvNowPlaying?>(null)
    val nowPlaying: StateFlow<TvNowPlaying?> = _nowPlaying.asStateFlow()
    val snapshot: StateFlow<PlaybackSnapshot> get() = engine.snapshot

    private var scope: CoroutineScope? = null
    private var pendingStart: PendingStart? = null
    private var lastSent: TvLinkMessage.Status? = null

    /** Position and play state to apply once the requested video is ready. */
    private data class PendingStart(val videoId: String, val startMs: Long, val autoplay: Boolean)

    fun start() {
        if (scope != null) return
        val running = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = running
        server.start()
        running.launch {
            engine.snapshot.collect { snapshot ->
                applyPendingStart(snapshot)
                val status = statusOf(snapshot)
                // State changes go out at once; position alone rides the once-a-second tick.
                if (lastSent?.copy(positionMs = 0, bufferedMs = 0) != status.copy(positionMs = 0, bufferedMs = 0)) publish(status)
            }
        }
        running.launch {
            while (isActive) {
                delay(STATUS_INTERVAL_MS)
                publish(statusOf(engine.snapshot.value))
            }
        }
    }

    fun stop() {
        scope?.cancel()
        scope = null
        server.stop()
    }

    /** Back on the TV remote while playing: stop here, and the phone ends its session. */
    fun stopPlayback() {
        pendingStart = null
        _nowPlaying.value = null
        engine.stop()
        publish(TvLinkMessage.Status())
    }

    /** A video picked on the TV itself, from a row or a search. */
    fun playLocal(videoId: String, title: String, author: String?) {
        _nowPlaying.value = TvNowPlaying(videoId, title, author)
        pendingStart = null
        engine.setVideoVisible(true)
        engine.play(videoId)
    }

    /** Next or previous on the TV: the phone's queue decides what comes next. */
    fun skip(forward: Boolean) = server.send(TvLinkMessage.Skip(forward))

    /** Whether a phone is there to pick the next video. */
    val hasPhone: Boolean get() = server.state.value.connectedPhone != null

    private fun onCommand(message: TvLinkMessage) {
        when (message) {
            is TvLinkMessage.Load -> {
                _nowPlaying.value = TvNowPlaying(message.videoId, message.title, message.author)
                pendingStart = PendingStart(message.videoId, message.startMs.coerceAtLeast(0), message.autoplay)
                engine.setVideoVisible(true)
                engine.play(message.videoId)
                applyPendingStart(engine.snapshot.value)
            }
            TvLinkMessage.Play -> engine.play()
            TvLinkMessage.Pause -> engine.pause()
            is TvLinkMessage.Seek -> engine.seekTo(message.positionMs.coerceAtLeast(0))
            is TvLinkMessage.Speed -> engine.setSpeed(message.speed.coerceIn(MIN_SPEED, MAX_SPEED))
            TvLinkMessage.Stop -> stopPlayback()
            else -> Unit
        }
    }

    private fun applyPendingStart(snapshot: PlaybackSnapshot) {
        val pending = pendingStart ?: return
        if (snapshot.videoId != pending.videoId || !snapshot.prepared) return
        pendingStart = null
        // The engine may resume from its own saved spot; the phone's position wins.
        if (abs(snapshot.positionMs - pending.startMs) > SEEK_SLACK_MS) engine.seekTo(pending.startMs)
        if (pending.autoplay) engine.play() else engine.pause()
    }

    private fun publish(status: TvLinkMessage.Status) {
        lastSent = status
        server.send(status)
    }

    companion object {
        private const val STATUS_INTERVAL_MS = 1_000L
        private const val SEEK_SLACK_MS = 2_000L
        private const val MIN_SPEED = 0.25f
        private const val MAX_SPEED = 2f

        internal fun statusOf(snapshot: PlaybackSnapshot): TvLinkMessage.Status {
            if (snapshot.videoId == null) return TvLinkMessage.Status()
            val state = when {
                snapshot.ended -> TvLinkMessage.Status.STATE_ENDED
                snapshot.error != null -> TvLinkMessage.Status.STATE_IDLE
                snapshot.prepared && !snapshot.isBuffering -> TvLinkMessage.Status.STATE_READY
                else -> TvLinkMessage.Status.STATE_BUFFERING
            }
            return TvLinkMessage.Status(
                videoId = snapshot.videoId,
                state = state,
                playing = snapshot.isPlaying,
                wantsPlay = snapshot.userWantsPlay,
                positionMs = snapshot.positionMs,
                durationMs = snapshot.durationMs,
                bufferedMs = snapshot.bufferedPositionMs,
                speed = snapshot.speed,
                error = snapshot.error,
            )
        }
    }
}
