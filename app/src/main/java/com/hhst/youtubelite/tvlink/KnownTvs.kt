package com.hhst.youtubelite.tvlink

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A Puretube TV this phone has found or used, so it is listed again before discovery hears it. */
@Serializable
internal data class KnownTv(val name: String, val host: String, val port: Int, val seenAt: Long)

/**
 * The phone's list of TVs it has met. One entry per TV: a TV that comes back at a new
 * address (the router gave it another one) replaces its old entry, and a TV not seen for
 * [MAX_AGE_MS] drops off, so the cast list holds no dead addresses.
 */
internal object KnownTvs {
    const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
    const val MAX_TVS = 6
    private val json = Json { ignoreUnknownKeys = true }

    fun remember(list: List<KnownTv>, tv: KnownTv): List<KnownTv> =
        fresh(listOf(tv) + list.filterNot { it.sameAddress(tv) || it.sameTv(tv) }, tv.seenAt).take(MAX_TVS)

    fun fresh(list: List<KnownTv>, now: Long): List<KnownTv> = list.filter { now - it.seenAt in 0..MAX_AGE_MS }

    /** The entries that [tv] makes stale: the same TV listed under another address. */
    fun replacedBy(list: List<KnownTv>, tv: KnownTv): List<KnownTv> = list.filter { it.sameTv(tv) && !it.sameAddress(tv) }

    fun decode(text: String?): List<KnownTv> =
        text?.let { runCatching { json.decodeFromString<List<KnownTv>>(it) }.getOrNull() }.orEmpty()

    fun encode(list: List<KnownTv>): String = json.encodeToString(list)

    private fun KnownTv.sameAddress(other: KnownTv) = host == other.host && port == other.port

    /** Placeholder names ("Puretube TV (host)") say nothing about which TV it is. */
    private fun KnownTv.sameTv(other: KnownTv) =
        name.isNotBlank() && !name.startsWith(PLACEHOLDER) && name.equals(other.name, ignoreCase = true)

    const val PLACEHOLDER = "Puretube TV ("
}
