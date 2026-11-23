package com.hhst.youtubelite.data.media

import com.hhst.youtubelite.watch.domain.VoteCounts
import org.junit.Assert.assertEquals
import org.junit.Test

class VoteCountsTest {
    @Test fun hiddenLikesShowNoNumbers() {
        assertEquals(VoteCounts(null, null), voteCounts(0, 0))
    }

    @Test fun realCountsStay() {
        assertEquals(VoteCounts(12, 0), voteCounts(12, 0))
        assertEquals(VoteCounts(0, 3), voteCounts(0, 3))
    }

    @Test fun negativeCountsClampToZero() {
        assertEquals(VoteCounts(0, 5), voteCounts(-1, 5))
    }
}
