package com.hhst.youtubelite.data.media

import com.hhst.youtubelite.extension.PrefStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Videos the user marked as watched or unwatched from a video menu (LiTube #164, #165). They
 * stay on this phone and win over the progress YouTube reports, so a marked video fills its
 * bar and Hide watched hides it; an unwatched mark clears the bar again.
 */
class WatchMarks(private val store: PrefStore) {
    private val _marks = MutableStateFlow(decode(store.getString(KEY, "")))
    val marks: StateFlow<Map<String, Boolean>> = _marks.asStateFlow()

    fun mark(videoId: String, watched: Boolean) {
        if (!VIDEO_ID.matches(videoId)) return
        synchronized(this) {
            // Newest last; the oldest marks drop once the cap is reached.
            val next = LinkedHashMap(_marks.value).apply { remove(videoId); put(videoId, watched) }
            while (next.size > MAX_MARKS) next.remove(next.keys.first())
            _marks.value = next
            store.putString(KEY, encode(next))
        }
    }

    companion object {
        const val KEY = "watch_marks"
        const val MAX_MARKS = 2000
        private val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")

        internal fun encode(marks: Map<String, Boolean>): String =
            marks.entries.joinToString(",") { "${it.key}:${if (it.value) 1 else 0}" }

        internal fun decode(raw: String): Map<String, Boolean> = buildMap {
            raw.split(',').forEach { entry ->
                val id = entry.substringBefore(':')
                val flag = entry.substringAfter(':', "")
                if (VIDEO_ID.matches(id) && flag in setOf("0", "1")) put(id, flag == "1")
            }
        }
    }
}
