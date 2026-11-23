package com.hhst.youtubelite.home

import com.hhst.youtubelite.home.domain.FeedItem
import com.hhst.youtubelite.home.domain.watched
import com.hhst.youtubelite.extension.PreferenceKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** LiTube #277: "Hide watched videos" drops what YouTube marks as watched. */
class WatchedVideoTest {
    private fun video(progress: Float?) = FeedItem.Video("v", "Title", "Author", "", "", "1:00", "UC", progress = progress)

    @Test fun watchedMeansTheLastTenthOrMore() {
        assertTrue(video(1f).watched())
        assertTrue(video(0.9f).watched())
        assertFalse(video(0.5f).watched())
        assertFalse(video(null).watched())
    }

    @Test fun offByDefault() = assertEquals(false, PreferenceKeys.DEFAULTS[PreferenceKeys.ENABLE_HIDE_WATCHED])
}
