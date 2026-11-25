package com.hhst.youtubelite.tv

import com.hhst.youtubelite.data.media.PlaybackSample
import com.hhst.youtubelite.data.media.PlaybackWatchReporting
import com.hhst.youtubelite.data.media.toReportingVideo
import com.hhst.youtubelite.player.SleepChoice
import com.hhst.youtubelite.player.SleepTimer
import com.hhst.youtubelite.player.engine.PlaybackApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the TV player keeps beside the engine: the watch page and its account actions, the
 * sleep timer, and watch-history reporting, so what is watched on the TV shows up in
 * History on every device.
 */
internal class TvPlayerSession(
    scope: CoroutineScope,
    private val engine: PlaybackApi,
    ops: TvVideoOps,
    private val reporting: PlaybackWatchReporting?,
) {
    val video = TvVideoModel(scope, ops)
    private val _sleep = MutableStateFlow<SleepChoice>(SleepChoice.Off)
    val sleep: StateFlow<SleepChoice> = _sleep.asStateFlow()
    private val timer = SleepTimer(scope, onChange = { _sleep.value = it }, onFire = engine::pause,
        armEndOfVideo = engine::setPauseAtEndOfVideo)
    /** The video the sleep timer stopped at its end; autoplay must not move past it. */
    @Volatile var sleptOn: String? = null
        private set

    init {
        video.onVideo = { reporting?.publish(it.toReportingVideo()) }
    }

    fun setSleep(choice: SleepChoice) = timer.set(choice)

    fun start() {
        engine.onWatchContext = { videoId, tracking, generation -> reporting?.armTracking(videoId, tracking, generation) }
        engine.onWatchBoundary = { state, position ->
            reporting?.finishPlayback(PlaybackSample(state.videoId, position, state.durationMs, state.isPlaying, state.discontinuityEpoch))
        }
        engine.onPausedAtEndOfVideo = {
            if (timer.onVideoEnded()) sleptOn = engine.snapshot.value.videoId
        }
        reporting?.attach {
            val s = engine.snapshot.value
            PlaybackSample(s.videoId, s.positionMs, s.durationMs, s.isPlaying, s.discontinuityEpoch)
        }
    }

    fun stop() {
        reporting?.detach()
        engine.onWatchContext = null
        engine.onWatchBoundary = null
        engine.onPausedAtEndOfVideo = null
    }
}
