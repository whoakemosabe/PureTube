package com.hhst.youtubelite.extractor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerStoryboardTest {
    // Shape of a real /player reply: the spec lives under storyboards, never in /next.
    private val body = """
        {"playabilityStatus":{"status":"OK"},
         "storyboards":{"playerStoryboardSpecRenderer":{"spec":"https://i.ytimg.com/sb/dQw4w9WgXcQ/storyboard3_L${'$'}L/${'$'}N.jpg?sqp=-oaymwENSDfyq4qpAwVwAcABBqLzl_8DBgjR&v=1|48#27#100#10#10#0#default#rs${'$'}AOn4CLA|80#45#107#10#10#2000#M${'$'}M#rs${'$'}AOn4CLB|160#90#107#5#5#2000#M${'$'}M#rs${'$'}AOn4CLC","recommendedLevel":2}},
         "videoDetails":{"videoId":"dQw4w9WgXcQ","lengthSeconds":"213"}}
    """.trimIndent()

    @Test fun readsThePreviewSheetsFromThePlayerReply() {
        val spec = PlayerStoryboard.from(body)!!
        assertEquals(3, spec.levels.size)
        val frame = spec.frame(30_000, 213_000)!!
        assertEquals(160, frame.width)
        assertTrue(frame.url, frame.url.startsWith("https://i.ytimg.com/sb/dQw4w9WgXcQ/storyboard3_L2/"))
        assertFalse(frame.url, frame.url.contains("\\u0026"))
        assertEquals("i.ytimg.com", java.net.URI(frame.url.substringBefore("?")).host)
    }

    @Test fun aReplyWithoutSheetsGivesNone() {
        assertNull(PlayerStoryboard.from("""{"playabilityStatus":{"status":"OK"}}"""))
        assertNull(PlayerStoryboard.from(null))
        // Live streams have no fixed timeline to preview.
        assertNull(PlayerStoryboard.from("""{"storyboards":{"playerLiveStoryboardSpecRenderer":{"spec":"https://i.ytimg.com/sb/x/storyboard_live_90_3x3_b1/M#.jpg|159#90#3#3"}}}"""))
    }

    @Test fun onlyPlayerRequestsAreRead() {
        assertTrue(PlayerStoryboard.isPlayerRequest("https://www.youtube.com/youtubei/v1/player?prettyPrint=false"))
        assertFalse(PlayerStoryboard.isPlayerRequest("https://www.youtube.com/youtubei/v1/next"))
    }

    @Test fun cachedChaptersWithoutSheetsPickThemUpFromThePlayer() {
        val cache = FakeCache()
        cache.putChapters("v", ChapterList(emptyList(), null))
        val spec = PlayerStoryboard.from(body)
        val promise = Promise(ChapterList(), kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined), autostart = false) {
            it.resolveChapters("v", cache, storyboard = { spec }) { error("cached") }
        }
        promise.start()
        assertEquals(spec, cache.getChapters("v")?.storyboard)
    }
}
