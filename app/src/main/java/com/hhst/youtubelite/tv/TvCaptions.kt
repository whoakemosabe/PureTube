package com.hhst.youtubelite.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.core.JsonCache
import com.hhst.youtubelite.player.PREF_TTL_MS
import com.hhst.youtubelite.player.PlayerViewModel
import com.hhst.youtubelite.player.engine.PlaybackSnapshot
import com.hhst.youtubelite.player.surface.SubtitleOverlay
import com.hhst.youtubelite.player.surface.SubtitleStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How captions look on the TV. */
internal interface TvCaptionStyleStore {
    val style: StateFlow<SubtitleStyle>
    fun set(style: SubtitleStyle)
}

/** Kept where the phone player keeps its caption style, so both screens share one look. */
internal class CachedCaptionStyle(private val cache: JsonCache) : TvCaptionStyleStore {
    private val _style = MutableStateFlow(
        SubtitleStyle.decode(runCatching { cache.get(PlayerViewModel.KEY_SUBTITLE_STYLE, String::class.java) }.getOrNull()),
    )
    override val style: StateFlow<SubtitleStyle> = _style.asStateFlow()

    override fun set(style: SubtitleStyle) {
        val clean = style.clamped()
        if (clean == _style.value) return
        _style.value = clean
        runCatching { cache.put(PlayerViewModel.KEY_SUBTITLE_STYLE, clean.encode(), PREF_TTL_MS) }
    }
}

/** The caption style rows: each press steps that row to its next choice. */
internal enum class CaptionSetting(val label: String) { Size("Text size"), Colour("Text colour"), Background("Background"), Position("Position"), Bold("Bold text") }

internal val CAPTION_SIZES = listOf(0.75f, 1f, 1.25f, 1.5f, 2f, 2.5f)
internal val CAPTION_COLOURS = listOf(
    "White" to SubtitleStyle.COLOR_WHITE,
    "Yellow" to SubtitleStyle.COLOR_YELLOW,
    "Cyan" to 0xFF00FFFF.toInt(),
    "Green" to 0xFF00FF00.toInt(),
    "Pink" to 0xFFFF80C0.toInt(),
)

internal fun captionStyleRows(style: SubtitleStyle): List<TvPanelRow> = CaptionSetting.entries.map { TvPanelRow(it.name, it.label, captionValue(style, it)) }

internal fun captionValue(style: SubtitleStyle, setting: CaptionSetting): String = when (setting) {
    CaptionSetting.Size -> "${(style.textScale * 100).toInt()}%"
    CaptionSetting.Colour -> CAPTION_COLOURS.firstOrNull { it.second == style.colorArgb }?.first ?: SubtitleStyle.formatColorHex(style.colorArgb)
    CaptionSetting.Background -> when (style.background) {
        SubtitleStyle.Background.NONE -> "None"
        SubtitleStyle.Background.SEMI -> "See-through"
        SubtitleStyle.Background.OPAQUE -> "Solid"
    }
    CaptionSetting.Position -> if (style.vertical < 0.5f) "Top" else "Bottom"
    CaptionSetting.Bold -> if (style.bold) "On" else "Off"
}

/** [style] with [setting] moved on to its next choice, wrapping round at the end. */
internal fun stepCaptionStyle(style: SubtitleStyle, setting: CaptionSetting): SubtitleStyle = when (setting) {
    CaptionSetting.Size -> style.copy(textScale = CAPTION_SIZES.after { it >= style.textScale - 0.01f })
    CaptionSetting.Colour -> style.copy(colorArgb = CAPTION_COLOURS.map { it.second }.after { it == style.colorArgb })
    CaptionSetting.Background -> style.copy(background = SubtitleStyle.Background.entries.let { it[(style.background.ordinal + 1) % it.size] })
    CaptionSetting.Position -> style.copy(vertical = if (style.vertical < 0.5f) 1f else 0f, horizontal = 0f)
    CaptionSetting.Bold -> style.copy(bold = !style.bold)
}

/** The choice after the first one matching [current]; the first choice when none match. */
private fun <T> List<T>.after(current: (T) -> Boolean): T {
    val at = indexOfFirst(current)
    return if (at < 0) first() else this[(at + 1) % size]
}

/**
 * The words of the caption on screen, drawn over the picture. It reads the cues itself, so a
 * new line redraws only the captions. [lifted] moves them up clear of the player controls.
 */
@Composable
internal fun TvCaptionLayer(live: () -> PlaybackSnapshot, style: SubtitleStyle, lifted: Boolean) {
    val cues by remember { derivedStateOf { live().subtitleCues } }
    if (cues.isEmpty()) return
    val top = style.vertical < 0.5f
    Box(
        Modifier.fillMaxSize().padding(
            start = 64.dp, end = 64.dp,
            top = if (top && lifted) 140.dp else 40.dp,
            bottom = if (!top && lifted) 230.dp else 48.dp,
        ),
    ) {
        SubtitleOverlay(
            cues = cues,
            style = style.copy(textScale = style.textScale * TV_CAPTION_SCALE),
            modifier = Modifier.align(BiasAlignment(style.horizontal.coerceIn(-1f, 1f), style.verticalBias())).fillMaxWidth(0.8f),
        )
    }
}

/** The phone's sizes read small across a room; 100% on the TV is this much bigger. */
private const val TV_CAPTION_SCALE = 1.6f
