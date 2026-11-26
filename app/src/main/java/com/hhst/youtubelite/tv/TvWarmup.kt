package com.hhst.youtubelite.tv

import com.hhst.youtubelite.extractor.Extractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Gets a video ready before it is pressed, as the phone Home does: a tile that keeps focus
 * for a moment, and the video that plays next, have their streams looked up early, so
 * pressing play starts sooner. Moving across tiles quickly warms nothing.
 */
internal object TvWarmup {
    private val scope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    private var pending: Job? = null
    private var last: String? = null

    /** Swapped in tests; the app's extractor otherwise. */
    internal var warm: (String) -> Unit = { id ->
        runCatching { org.koin.java.KoinJavaComponent.getKoin().getOrNull<Extractor>()?.warm(id) }
    }

    /** Focus rested on [videoId]: warm it if focus is still there after a short pause. */
    fun rest(videoId: String?) {
        pending?.cancel()
        if (videoId == null) return
        pending = scope.launch {
            delay(REST_MS)
            now(videoId)
        }
    }

    /** Warms [videoId] straight away (the next video in the queue), once per video. */
    fun now(videoId: String?) {
        if (videoId == null || videoId == last) return
        last = videoId
        warm(videoId)
    }

    internal fun resetForTest() {
        pending?.cancel()
        pending = null
        last = null
    }

    private const val REST_MS = 600L
}
