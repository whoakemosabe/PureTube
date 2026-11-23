package com.hhst.youtubelite.extractor

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive

/**
 * The seek bar preview sheets are only in the /player reply ("storyboards" →
 * playerStoryboardSpecRenderer.spec); the /next watch page never carries them. Live
 * streams use playerLiveStoryboardSpecRenderer, which has no fixed timeline, so it is skipped.
 */
internal object PlayerStoryboard {
    private val spec = Regex("\"playerStoryboardSpecRenderer\"\\s*:\\s*\\{\\s*\"spec\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")

    fun isPlayerRequest(url: String): Boolean = url.contains("/youtubei/v1/player")

    fun from(body: String?): StoryboardSpec? {
        val raw = body?.let { spec.find(it)?.groupValues?.get(1) } ?: return null
        val text = runCatching { Json.parseToJsonElement("\"$raw\"").jsonPrimitive.content }.getOrNull() ?: return null
        return StoryboardSpec.from(com.liskovsoft.youtubeapi.videoinfo.V2.readStoryboard(text))
    }

}
