package com.hhst.youtubelite.tvlink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KnownTvsTest {
    private val day = 24L * 60 * 60 * 1000

    @Test fun aTvThatMovesKeepsOneEntry() {
        val old = KnownTv("Living room", "192.168.1.20", 48765, seenAt = 0)
        val moved = KnownTv("Living room", "192.168.1.31", 48765, seenAt = day)
        val list = KnownTvs.remember(listOf(old), moved)
        assertEquals(listOf(moved), list)
        assertEquals(listOf(old), KnownTvs.replacedBy(listOf(old), moved))
    }

    @Test fun twoTvsStayTwoAndNewestComesFirst() {
        val a = KnownTv("Living room", "192.168.1.20", 48765, seenAt = 0)
        val b = KnownTv("Bedroom", "192.168.1.21", 48765, seenAt = day)
        assertEquals(listOf(b, a), KnownTvs.remember(listOf(a), b))
    }

    @Test fun seeingATvAgainRefreshesIt() {
        val a = KnownTv("Living room", "192.168.1.20", 48765, seenAt = 0)
        val again = a.copy(seenAt = day)
        assertEquals(listOf(again), KnownTvs.remember(listOf(a), again))
    }

    @Test fun oldTvsDropOff() {
        val gone = KnownTv("Old TV", "192.168.1.5", 48765, seenAt = 0)
        val now = KnownTvs.MAX_AGE_MS + day
        val fresh = KnownTv("Living room", "192.168.1.20", 48765, seenAt = now)
        assertEquals(listOf(fresh), KnownTvs.remember(listOf(gone), fresh))
        assertTrue(KnownTvs.fresh(listOf(gone), now).isEmpty())
    }

    @Test fun placeholderNamesNeverMergeTvs() {
        val a = KnownTv("Puretube TV (192.168.1.20)", "192.168.1.20", 48765, seenAt = 0)
        val b = KnownTv("Puretube TV (192.168.1.20)", "192.168.1.40", 48765, seenAt = day)
        assertEquals(2, KnownTvs.remember(listOf(a), b).size)
    }

    @Test fun theListIsCapped() {
        val many = (1..10).map { KnownTv("TV $it", "192.168.1.$it", 48765, seenAt = it.toLong()) }
        val list = many.fold(emptyList<KnownTv>()) { acc, tv -> KnownTvs.remember(acc, tv) }
        assertEquals(KnownTvs.MAX_TVS, list.size)
        assertEquals("TV 10", list.first().name)
    }

    @Test fun survivesStorage() {
        val list = listOf(KnownTv("Living room", "192.168.1.20", 48765, seenAt = 5))
        assertEquals(list, KnownTvs.decode(KnownTvs.encode(list)))
        assertTrue(KnownTvs.decode("garbage").isEmpty())
        assertTrue(KnownTvs.decode(null).isEmpty())
    }
}
