package com.hhst.youtubelite.tv

import com.hhst.youtubelite.tv.browse.TvCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvHomeRowsTest {
    @Test fun durationsReadAsMilliseconds() {
        assertEquals(754_000L, TvHomeRows.durationMs("12:34"))
        assertEquals(3_723_000L, TvHomeRows.durationMs("1:02:03"))
        assertEquals(0L, TvHomeRows.durationMs("LIVE"))
        assertEquals(0L, TvHomeRows.durationMs(""))
    }

    @Test fun onlyHalfWatchedVideosGoToContinueWatching() {
        fun card(progress: Float?) = TvCard("h", "", "", "", videoId = "abcdefghijk", progress = progress)
        assertTrue(TvHomeRows.inProgress(card(0.4f)))
        assertFalse(TvHomeRows.inProgress(card(null)))
        assertFalse(TvHomeRows.inProgress(card(0.99f)))
    }
}
