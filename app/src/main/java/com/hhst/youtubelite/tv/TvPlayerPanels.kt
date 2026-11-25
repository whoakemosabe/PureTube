package com.hhst.youtubelite.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.hhst.youtubelite.player.SleepChoice
import com.hhst.youtubelite.player.engine.PlaybackApi
import com.hhst.youtubelite.player.engine.PlaybackSnapshot
import com.hhst.youtubelite.player.engine.SubtitleTrack
import com.hhst.youtubelite.player.surface.PlayerUi
import com.hhst.youtubelite.ui.design.AppIcon

/** The player's side panels, one at a time, opened from its buttons. */
internal enum class TvPanel { Description, Comments, Channel, Save, Settings, Quality, Speed, Captions, Audio, Sleep }

/** One row in a panel: a label, an optional value on the right, a check when it is the current pick. */
internal data class TvPanelRow(val key: String, val label: String, val value: String? = null, val checked: Boolean = false)

internal fun settingsRows(snapshot: PlaybackSnapshot, sleep: SleepChoice): List<TvPanelRow> = listOfNotNull(
    TvPanelRow(TvPanel.Quality.name, "Quality", snapshot.qualityLabel ?: snapshot.activeQuality?.let { "Auto ($it)" } ?: "Auto"),
    TvPanelRow(TvPanel.Speed.name, "Playback speed", speedLabel(snapshot.speed)),
    TvPanelRow(TvPanel.Captions.name, "Captions", captionLabel(snapshot)),
    TvPanelRow(TvPanel.Audio.name, "Audio track", snapshot.audioTracks.firstOrNull { it.key == snapshot.audioTrackKey }?.label ?: "Default")
        .takeIf { snapshot.audioTracks.size > 1 },
    TvPanelRow(TvPanel.Sleep.name, "Sleep timer", sleepLabel(sleep)),
)

internal fun qualityRows(snapshot: PlaybackSnapshot) =
    listOf(TvPanelRow(AUTO, "Auto", snapshot.activeQuality, checked = snapshot.qualityLabel == null)) +
        snapshot.qualities.map { TvPanelRow(it, it, checked = it == snapshot.qualityLabel) }

internal fun speedRows(snapshot: PlaybackSnapshot) =
    PlayerUi.SpeedChoices.filter { it <= MAX_TV_SPEED }.map { TvPanelRow(it.toString(), speedLabel(it), checked = it == snapshot.speed) }

internal fun captionRows(snapshot: PlaybackSnapshot) =
    listOf(TvPanelRow(OFF, "Off", checked = !snapshot.subtitleEnabled || snapshot.subtitleKey == null)) +
        snapshot.subtitleTracks.map { TvPanelRow(it.key, trackLabel(it), checked = snapshot.subtitleEnabled && it.key == snapshot.subtitleKey) }

internal fun audioRows(snapshot: PlaybackSnapshot) =
    snapshot.audioTracks.map { TvPanelRow(it.key, it.label, checked = it.key == snapshot.audioTrackKey) }

internal fun sleepRows(sleep: SleepChoice) =
    SleepChoice.choices.map { TvPanelRow(sleepKey(it), sleepLabel(it), checked = it == sleep) }

internal fun sleepChoiceOf(key: String): SleepChoice = SleepChoice.choices.firstOrNull { sleepKey(it) == key } ?: SleepChoice.Off

private fun sleepKey(choice: SleepChoice) = when (choice) {
    SleepChoice.Off -> OFF
    SleepChoice.EndOfVideo -> "end"
    is SleepChoice.Minutes -> choice.minutes.toString()
}

private fun sleepLabel(choice: SleepChoice) = when (choice) {
    SleepChoice.Off -> "Off"
    SleepChoice.EndOfVideo -> "End of video"
    is SleepChoice.Minutes -> "${choice.minutes} minutes"
}

private fun speedLabel(speed: Float) = if (speed == 1f) "Normal" else "${"%.2f".format(speed).trimEnd('0').trimEnd('.')}x"

private fun trackLabel(track: SubtitleTrack) = track.language + if (track.autoGenerated) " (auto-generated)" else ""

private fun captionLabel(snapshot: PlaybackSnapshot): String {
    if (!snapshot.subtitleEnabled) return "Off"
    return snapshot.subtitleTracks.firstOrNull { it.key == snapshot.subtitleKey }?.let(::trackLabel) ?: "Off"
}

/** Applies a pick from a settings sub-panel to the engine (or the sleep timer). */
internal fun applyPick(panel: TvPanel, key: String, engine: PlaybackApi, setSleep: (SleepChoice) -> Unit) {
    when (panel) {
        TvPanel.Quality -> engine.setQuality(key.takeUnless { it == AUTO })
        TvPanel.Speed -> key.toFloatOrNull()?.let(engine::setSpeed)
        TvPanel.Captions -> engine.setSubtitle(key.takeUnless { it == OFF })
        TvPanel.Audio -> engine.setAudioTrack(key)
        TvPanel.Sleep -> setSleep(sleepChoiceOf(key))
        else -> Unit
    }
}

private const val DESCRIPTION_STEP = 240f
internal const val AUTO = "auto"
internal const val OFF = "off"
private const val MAX_TV_SPEED = 2f

/** A frosted panel down the right of the player, like the YouTube TV settings sheet. */
@Composable
internal fun TvSidePanel(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
        Column(
            Modifier
                .width(460.dp)
                .fillMaxHeight()
                .background(Brush.horizontalGradient(listOf(Color(0xE6161616), Color(0xF5161616))))
                .padding(horizontal = 28.dp, vertical = 36.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = TvColors.Text)
            Spacer(Modifier.height(20.dp))
            content()
        }
    }
}

@Composable
internal fun TvPanelList(rows: List<TvPanelRow>, focusKey: String? = null, onPick: (TvPanelRow) -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(rows.size) { requestFocusSoon(first) }
    // Focus opens on the row the user came back from, else the current pick, else the top.
    val start = rows.indexOfFirst { it.key == focusKey }.takeIf { it >= 0 }
        ?: rows.indexOfFirst { it.checked }.coerceAtLeast(0)
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(rows.size, key = { rows[it].key }) { index ->
            val row = rows[index]
            Surface(
                onClick = { onPick(row) },
                modifier = Modifier.fillMaxWidth().height(56.dp).then(if (index == start) Modifier.focusRequester(first) else Modifier),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = TvColors.Text,
                    focusedContainerColor = TvColors.Focused,
                    focusedContentColor = TvColors.OnFocused,
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            ) {
                Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(24.dp)) {
                        if (row.checked) AppIcon(AppIcon.Check, Modifier.size(24.dp), color = androidx.tv.material3.LocalContentColor.current)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(row.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f))
                    row.value?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                            color = androidx.tv.material3.LocalContentColor.current.copy(alpha = 0.7f))
                    }
                }
            }
        }
    }
}

/** The description: views, age and the full text, scrolled with the D-pad. */
@Composable
internal fun TvDescription(meta: String, text: String) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { requestFocusSoon(focus) }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    Surface(
        onClick = {},
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .onPreviewKeyEvent { event ->
                // Up and down read the text; the panel keeps focus until Back closes it.
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val step = when (event.key) {
                    Key.DirectionDown -> DESCRIPTION_STEP
                    Key.DirectionUp -> -DESCRIPTION_STEP
                    else -> return@onPreviewKeyEvent false
                }
                scope.launch { scroll.animateScrollBy(step) }
                true
            },
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll),
        ) {
            if (meta.isNotBlank()) Text(meta, color = TvColors.Muted, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(12.dp))
            Text(text.ifBlank { "No description." }, color = TvColors.Text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** Top comments, read-only, one focusable card each so the D-pad scrolls through them. */
@Composable
internal fun TvCommentList(comments: TvComments, onNearEnd: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(comments.threads.isNotEmpty()) { if (comments.threads.isNotEmpty()) requestFocusSoon(first) }
    when {
        comments.turnedOff -> Text("Comments are turned off.", color = TvColors.Muted)
        comments.threads.isEmpty() && comments.failed -> Text("Couldn't load comments.", color = TvColors.Muted)
        comments.threads.isEmpty() && comments.loading -> Text("Loading…", color = TvColors.Muted)
        comments.threads.isEmpty() -> Text("No comments yet.", color = TvColors.Muted)
        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(comments.threads.size, key = { comments.threads[it].id }) { index ->
                val thread = comments.threads[index]
                Surface(
                    onClick = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (index == 0) Modifier.focusRequester(first) else Modifier)
                        .onFocusChanged { if (it.isFocused && index >= comments.threads.size - 3) onNearEnd() },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.Transparent,
                        contentColor = TvColors.Text,
                        focusedContainerColor = TvColors.Frost,
                        focusedContentColor = TvColors.Text,
                    ),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(2.dp, TvColors.Focused),
                            shape = RoundedCornerShape(12.dp)),
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            listOf(thread.author.displayName.ifBlank { thread.author.handle }, thread.age).filter(String::isNotBlank).joinToString(" • "),
                            style = MaterialTheme.typography.labelLarge, color = TvColors.Muted, maxLines = 1,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(thread.body, style = MaterialTheme.typography.bodyMedium, maxLines = 6, overflow = TextOverflow.Ellipsis)
                        val counts = listOfNotNull(thread.likes.takeIf(String::isNotBlank)?.let { "$it likes" },
                            thread.replyCount.takeIf { it > 0 }?.let { if (it == 1) "1 reply" else "$it replies" })
                        if (counts.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(counts.joinToString(" • "), style = MaterialTheme.typography.labelMedium, color = TvColors.Muted)
                        }
                    }
                }
            }
            if (comments.loading) item { Text("Loading…", color = TvColors.Muted, modifier = Modifier.padding(14.dp)) }
        }
    }
}
