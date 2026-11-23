package com.hhst.youtubelite.extractor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryboardSpecTest {
    private val spec = StoryboardSpec(
        template = "https://i.ytimg.com/sb/abc/storyboard3_L\$L/\$N.jpg?sqp=x",
        levels = listOf(
            StoryboardLevelSpec(48, 27, 100, 10, 10, 0, "default", "s0"),
            StoryboardLevelSpec(160, 90, 100, 5, 5, 2_000, "M\$M", "s1"),
            StoryboardLevelSpec(640, 360, 100, 3, 3, 2_000, "M\$M", "s2"),
        ),
    )

    @Test
    fun picksTheLargestLevelThatFitsTheBubble() {
        val frame = spec.frame(0, 200_000)!!
        assertEquals(160, frame.width)
        assertEquals(5, frame.columns)
        assertEquals(5, frame.rows)
        assertTrue(frame.url, frame.url.startsWith("https://i.ytimg.com/"))
    }

    @Test
    fun theFirstFrameIsTheTopLeftCell() {
        val frame = spec.frame(0, 200_000)!!
        assertEquals(0, frame.column)
        assertEquals(0, frame.row)
    }

    @Test
    fun laterPositionsMoveAcrossTheSheet() {
        val first = spec.frame(0, 200_000)!!
        val later = spec.frame(10_000, 200_000)!!
        assertNotEquals(first.column to first.row, later.column to later.row)
        // Past one 5x5 sheet (25 frames x 2s) the next sheet is used.
        val nextSheet = spec.frame(60_000, 200_000)!!
        assertNotEquals(first.url, nextSheet.url)
    }

    @Test
    fun noUsableLevelGivesNoFrame() {
        assertNull(StoryboardSpec("https://i.ytimg.com/x", emptyList()).frame(0, 1_000))
        assertNull(StoryboardSpec("https://i.ytimg.com/x",
            listOf(StoryboardLevelSpec(640, 360, 100, 3, 3, 2_000, "M\$M", "s"))).frame(0, 1_000))
    }
}
