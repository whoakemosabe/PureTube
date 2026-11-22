package com.hhst.youtubelite.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTest {
    @Test fun comparesVersionsNumerically() {
        assertTrue(Updater.isNewer("v1.0.12", "1.0.9"))
        assertTrue(Updater.isNewer("v1.0.6", "1.0.5-debug"))
        assertFalse(Updater.isNewer("v1.0.5", "1.0.5"))
        assertFalse(Updater.isNewer("1.0.4", "1.0.5"))
    }

    @Test fun readsWhatsNewFromReleaseNotes() {
        val body = """
            Tap **puretube.apk** below on your phone to install or update.

            ### What's new since v1.0.5
            - Block more ads
            - Fix the player

            Puretube — ad-free YouTube for Android.
        """.trimIndent()
        assertEquals(listOf("Block more ads", "Fix the player"), Updater.whatsNew(body))
    }

    @Test fun collectsNotesFromEveryNewerRelease() {
        val json = """[
            {"tag_name":"v1.0.8","body":"### What's new since v1.0.7\n- Third"},
            {"tag_name":"v1.0.7","body":"### What's new since v1.0.6\n- Second"},
            {"tag_name":"v1.0.6","body":"### What's new since v1.0.5\n- First","prerelease":true},
            {"tag_name":"v1.0.5","body":"### What's new\n- Old"}
        ]"""
        assertEquals(listOf("Third", "Second"), Updater.notesSince(json, "1.0.5"))
        assertEquals(2, Updater.countSince(json, "1.0.5"))
    }
}
