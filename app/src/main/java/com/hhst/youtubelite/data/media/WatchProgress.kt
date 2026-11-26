package com.hhst.youtubelite.data.media

import com.hhst.youtubelite.extension.PrefStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * How far into each video you got, kept on this device. YouTube reports progress for only
 * some feed items, and often late, so lists show this where it is known. Kept for the
 * newest [MAX_VIDEOS]; private browsing records nothing (the caller stops sampling).
 */
class WatchProgress(private val store: PrefStore, private val clock: () -> Long = System::currentTimeMillis) {
    private var current = decode(store.getString(KEY, ""))
    private val _progress = MutableStateFlow<Map<String, Float>>(current)
    /** Published at most every [PUBLISH_EVERY_MS] while playing, so lists don't redraw each second. */
    val progress: StateFlow<Map<String, Float>> = _progress.asStateFlow()
    private var publishedAt = 0L

    @Synchronized
    fun record(videoId: String?, positionMs: Long, durationMs: Long) {
        if (videoId == null || !VIDEO_ID.matches(videoId) || durationMs <= 0 || positionMs < 0) return
        val fraction = (positionMs.toDouble() / durationMs).toFloat().coerceIn(0f, 1f)
        // Near the end counts as finished, as YouTube's bar fills.
        val shown = if (fraction >= DONE_FRACTION) 1f else fraction
        if (shown < MIN_FRACTION && current[videoId] == null) return
        current = LinkedHashMap(current).apply { remove(videoId); put(videoId, shown) }
            .also { while (it.size > MAX_VIDEOS) it.remove(it.keys.first()) }
        if (clock() - publishedAt >= PUBLISH_EVERY_MS) flush()
    }

    /** Saves and publishes now: when a video stops or ends. */
    @Synchronized
    fun flush() {
        publishedAt = clock()
        if (_progress.value == current) return
        _progress.value = current
        store.putString(KEY, encode(current))
    }

    companion object {
        const val KEY = "watch_progress"
        const val MAX_VIDEOS = 2000
        const val PUBLISH_EVERY_MS = 10_000L
        private const val DONE_FRACTION = 0.95f
        private const val MIN_FRACTION = 0.01f
        private val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")

        internal fun encode(progress: Map<String, Float>): String =
            progress.entries.joinToString(",") { "${it.key}:${String.format(Locale.US, "%.3f", it.value)}" }

        internal fun decode(raw: String): Map<String, Float> = buildMap {
            raw.split(',').forEach { entry ->
                val id = entry.substringBefore(':')
                val value = entry.substringAfter(':', "").toFloatOrNull()
                if (VIDEO_ID.matches(id) && value != null && value in 0f..1f) put(id, value)
            }
        }
    }
}
