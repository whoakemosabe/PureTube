package com.hhst.youtubelite.tv

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** How often and how lately a channel's videos were played on this TV. */
@Serializable
internal data class ChannelWatch(val count: Int = 0, val lastAt: Long = 0)

/**
 * Per-channel play counts, kept on this device for the Channels page's "Most watched" and
 * "Recently watched" orders. YouTube doesn't hand these out, so they start from now.
 */
internal object ChannelWatchStats {
    private const val PREFS = "tv-channel-watch"
    private const val KEY = "stats"
    private const val MAX_CHANNELS = 500
    private val json = Json { ignoreUnknownKeys = true }
    @Volatile private var context: Context? = null

    fun init(context: Context) { this.context = context.applicationContext }

    fun all(): Map<String, ChannelWatch> = decode(prefs()?.getString(KEY, null))

    @Synchronized fun record(channelId: String, now: Long = System.currentTimeMillis()) {
        if (channelId.isBlank()) return
        val prefs = prefs() ?: return
        val next = recorded(decode(prefs.getString(KEY, null)), channelId, now)
        prefs.edit().putString(KEY, json.encodeToString(next)).apply()
    }

    /** [stats] with one more play of [channelId]; the least recent fall off past the cap. */
    internal fun recorded(stats: Map<String, ChannelWatch>, channelId: String, now: Long): Map<String, ChannelWatch> {
        val before = stats[channelId] ?: ChannelWatch()
        val next = stats + (channelId to ChannelWatch(before.count + 1, now))
        return if (next.size <= MAX_CHANNELS) next
        else next.entries.sortedByDescending { it.value.lastAt }.take(MAX_CHANNELS).associate { it.key to it.value }
    }

    internal fun decode(text: String?): Map<String, ChannelWatch> =
        text?.let { runCatching { json.decodeFromString<Map<String, ChannelWatch>>(it) }.getOrNull() }.orEmpty()

    private fun prefs() = context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
