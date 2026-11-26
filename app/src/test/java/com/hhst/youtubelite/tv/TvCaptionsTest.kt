package com.hhst.youtubelite.tv

import com.hhst.youtubelite.player.MemJsonCache
import com.hhst.youtubelite.player.engine.PlaybackSnapshot
import com.hhst.youtubelite.player.surface.SubtitleStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TvCaptionsTest {
    @Test
    fun eachRowStepsToItsNextChoiceAndWraps() {
        var style = SubtitleStyle()
        val sizes = (1..CAPTION_SIZES.size).map { style = stepCaptionStyle(style, CaptionSetting.Size); style.textScale }
        assertEquals(CAPTION_SIZES.drop(2) + CAPTION_SIZES.take(2), sizes)
        val yellow = stepCaptionStyle(SubtitleStyle(), CaptionSetting.Colour)
        assertEquals(SubtitleStyle.COLOR_YELLOW, yellow.colorArgb)
        assertEquals("Yellow", captionValue(yellow, CaptionSetting.Colour))
        assertEquals(SubtitleStyle.Background.OPAQUE, stepCaptionStyle(SubtitleStyle(), CaptionSetting.Background).background)
        val top = stepCaptionStyle(SubtitleStyle(), CaptionSetting.Position)
        assertEquals("Top", captionValue(top, CaptionSetting.Position))
        assertEquals("Bottom", captionValue(stepCaptionStyle(top, CaptionSetting.Position), CaptionSetting.Position))
        assertTrue(stepCaptionStyle(SubtitleStyle(), CaptionSetting.Bold).bold)
    }

    @Test
    fun unknownColourStartsTheCycleAndShowsItsHex() {
        val odd = SubtitleStyle(colorArgb = 0xFF123456.toInt())
        assertEquals("#123456", captionValue(odd, CaptionSetting.Colour))
        assertEquals(SubtitleStyle.COLOR_WHITE, stepCaptionStyle(odd, CaptionSetting.Colour).colorArgb)
    }

    @Test
    fun styleIsSharedWithThePhoneThroughTheSameKey() {
        val cache = MemJsonCache()
        val store = CachedCaptionStyle(cache)
        store.set(SubtitleStyle(textScale = 2f, bold = true))
        assertEquals(SubtitleStyle(textScale = 2f, bold = true), store.style.value)
        assertEquals(store.style.value, SubtitleStyle.decode(cache.get("player:subtitle_style", String::class.java)))
        assertEquals(store.style.value, CachedCaptionStyle(cache).style.value)
    }

    @Test
    fun captionTextChangesDoNotRedrawTheWholePlayer() {
        val snapshot = PlaybackSnapshot(subtitleCues = listOf("Hello"), positionMs = 5_000)
        assertEquals(withoutPosition(PlaybackSnapshot()), withoutPosition(snapshot))
    }

    @Test
    fun warmupWarmsEachVideoOnce() {
        val warmed = mutableListOf<String>()
        TvWarmup.resetForTest()
        TvWarmup.warm = { warmed += it }
        TvWarmup.now("a"); TvWarmup.now("a"); TvWarmup.now(null); TvWarmup.now("b")
        assertEquals(listOf("a", "b"), warmed)
        TvWarmup.resetForTest()
    }
}
