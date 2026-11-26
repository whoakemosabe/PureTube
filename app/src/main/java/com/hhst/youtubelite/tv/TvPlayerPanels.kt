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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import com.hhst.youtubelite.comments.domain.CommentThread
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import com.hhst.youtubelite.ui.design.Artwork
import androidx.compose.ui.unit.sp
import com.hhst.youtubelite.player.SleepChoice
import com.hhst.youtubelite.watch.chat.LiveChatContent
import com.hhst.youtubelite.watch.chat.LiveChatUiState
import com.hhst.youtubelite.player.engine.LoopMode
import com.hhst.youtubelite.player.engine.PlaybackApi
import com.hhst.youtubelite.player.engine.PlaybackSnapshot
import com.hhst.youtubelite.player.engine.SubtitleTrack
import com.hhst.youtubelite.player.surface.PlayerUi
import com.hhst.youtubelite.player.surface.SubtitleStyle
import com.hhst.youtubelite.ui.design.AppIcon

/** The player's side panels, one at a time, opened from its buttons. */
internal enum class TvPanel { Description, Comments, Chat, Channel, Save, Settings, Quality, Speed, Captions, CaptionStyle, Audio, Sleep }

/** One row in a panel: a label, an optional value on the right, a check when it is the current pick. */
internal data class TvPanelRow(
    val key: String,
    val label: String,
    val value: String? = null,
    val checked: Boolean = false,
    val icon: AppIcon? = null,
    /** Opens another panel: drawn with an arrow. */
    val opens: Boolean = false,
)

internal fun settingsRows(snapshot: PlaybackSnapshot, sleep: SleepChoice): List<TvPanelRow> = listOfNotNull(
    TvPanelRow(TvPanel.Captions.name, "Captions", captionLabel(snapshot), icon = AppIcon.Feedback, opens = true),
    TvPanelRow(TvPanel.Quality.name, "Quality", snapshot.qualityLabel ?: snapshot.activeQuality?.let { "Auto ($it)" } ?: "Auto",
        icon = AppIcon.Sparkle, opens = true),
    TvPanelRow(TvPanel.Speed.name, "Playback speed", speedLabel(snapshot.speed), icon = AppIcon.Clock, opens = true),
    TvPanelRow(TvPanel.Audio.name, "Audio track", snapshot.audioTracks.firstOrNull { it.key == snapshot.audioTrackKey }?.label ?: "Default",
        icon = AppIcon.Voice, opens = true)
        .takeIf { snapshot.audioTracks.size > 1 },
    TvPanelRow(TvPanel.Sleep.name, "Sleep timer", sleepLabel(sleep), icon = AppIcon.Moon, opens = true),
    TvPanelRow(LOOP, "Loop video", if (snapshot.loopMode == LoopMode.LOOP_ONE) "On" else "Off", icon = AppIcon.Refresh),
)

/** Loop on plays this video again and again; off goes back to playing what's next. */
internal fun toggledLoop(mode: LoopMode): LoopMode = if (mode == LoopMode.LOOP_ONE) LoopMode.QUEUE_NEXT else LoopMode.LOOP_ONE

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

internal fun captionSummary(style: SubtitleStyle): String =
    listOf(captionValue(style, CaptionSetting.Size), captionValue(style, CaptionSetting.Colour), captionValue(style, CaptionSetting.Background)).joinToString(", ")

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

/** 845, 34.8K, 1.2M: as YouTube writes counts. */
internal fun compactCount(n: Long): String = when {
    n >= 1_000_000_000 -> trimmed(n / 1_000_000_000.0) + "B"
    n >= 1_000_000 -> trimmed(n / 1_000_000.0) + "M"
    n >= 1_000 -> trimmed(n / 1_000.0) + "K"
    else -> n.toString()
}

private fun trimmed(value: Double): String =
    if (value >= 100) value.toLong().toString() else "%.1f".format(java.util.Locale.US, value).removeSuffix(".0")

private const val DESCRIPTION_STEP = 240f
internal const val AUTO = "auto"
internal const val OFF = "off"
internal const val LOOP = "loop"
private const val MAX_TV_SPEED = 2f

/**
 * A panel down the right of the player, like YouTube TV's settings sheet: a dark card that
 * floats clear of the edges, with the video dimmed beside it. [eyebrow] is the small line
 * over the title, saying where the panel opened from.
 */
@Composable
internal fun TvSidePanel(
    title: String,
    modifier: Modifier = Modifier,
    wide: Boolean = false,
    eyebrow: String? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier.fillMaxSize().background(Brush.horizontalGradient(0.35f to Color.Transparent, 1f to TvMenu.Scrim)),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            Modifier
                // Room above for the clock, which stays in the corner.
                .padding(top = 72.dp, end = 32.dp, bottom = 32.dp)
                .width(if (wide) 580.dp else 480.dp)
                .fillMaxHeight()
                .background(TvMenu.Panel, TvMenu.PanelShape)
                .padding(horizontal = 16.dp, vertical = 24.dp),
        ) {
            TvMenuHeader(title, eyebrow)
            Box(Modifier.padding(horizontal = 4.dp)) { content() }
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
    // Open scrolled to that row, so a pick far down a long list (captions) is on screen to take focus.
    val list = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = (start - 2).coerceAtLeast(0))
    // A list of choices keeps a column for the check, so labels line up whichever is picked.
    val checkable = rows.any { it.checked }
    LazyColumn(state = list, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(rows.size, key = { rows[it].key }) { index ->
            val row = rows[index]
            TvMenuRow(
                label = row.label,
                onClick = { onPick(row) },
                modifier = if (index == start) Modifier.focusRequester(first) else Modifier,
                icon = row.icon,
                value = row.value,
                checked = row.checked,
                checkable = checkable && row.icon == null,
                opens = row.opens,
            )
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

/** Top comments, one focusable card each so the D-pad scrolls; pressing one opens its replies. */
@Composable
internal fun TvCommentList(comments: TvComments, onNearEnd: () -> Unit, onReplies: (CommentThread) -> Unit, onMoreReplies: () -> Unit) {
    val thread = comments.replyThread
    // Kept while the replies are open, so Back lands on the same comment, not the top.
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    var returnTo by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    if (thread != null) {
        returnTo = thread.id
        TvReplies(thread, comments, onMoreReplies)
        return
    }
    val first = remember { FocusRequester() }
    val back = remember { FocusRequester() }
    LaunchedEffect(comments.threads.isNotEmpty()) {
        if (comments.threads.isEmpty()) return@LaunchedEffect
        if (returnTo != null && comments.threads.any { it.id == returnTo }) requestFocusSoon(back) else requestFocusSoon(first)
    }
    when {
        comments.turnedOff -> Text("Comments are turned off.", color = TvColors.Muted)
        comments.threads.isEmpty() && comments.failed -> Text("Couldn't load comments.", color = TvColors.Muted)
        comments.threads.isEmpty() && comments.loading -> Text("Loading…", color = TvColors.Muted)
        comments.threads.isEmpty() -> Text("No comments yet.", color = TvColors.Muted)
        else -> LazyColumn(state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(comments.threads.size, key = { comments.threads[it].id }) { index ->
                val item = comments.threads[index]
                CommentCard(
                    author = item.author.displayName.ifBlank { item.author.handle },
                    avatar = item.author.artwork,
                    age = item.age, body = item.body, likes = item.likes,
                    replies = item.replyCount,
                    // A comment with replies opens them; one without shows all its text.
                    onClick = { if (item.replyCount > 0) onReplies(item) else expanded = expanded.let { if (item.id in it) it - item.id else it + item.id } },
                    maxLines = if (item.id in expanded) Int.MAX_VALUE else COMMENT_LINES,
                    modifier = Modifier
                        .then(when {
                            item.id == returnTo -> Modifier.focusRequester(back)
                            index == 0 -> Modifier.focusRequester(first)
                            else -> Modifier
                        })
                        .onFocusChanged { if (it.isFocused && index >= comments.threads.size - 3) onNearEnd() },
                )
            }
            if (comments.loading) item { Text("Loading…", color = TvColors.Muted, modifier = Modifier.padding(14.dp)) }
        }
    }
}

/** One comment and its replies; Back returns to the list. */
@Composable
private fun TvReplies(thread: CommentThread, comments: TvComments, onMore: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(thread.id) { requestFocusSoon(first) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "top") {
            CommentCard(thread.author.displayName.ifBlank { thread.author.handle }, thread.author.artwork, thread.age, thread.body,
                thread.likes, 0, {}, Modifier.focusRequester(first), maxLines = 12)
        }
        items(comments.replies.size, key = { comments.replies[it].id }) { index ->
            val reply = comments.replies[index]
            CommentCard(reply.author.displayName.ifBlank { reply.author.handle }, reply.author.artwork, reply.age, reply.body,
                reply.likes, 0, {}, Modifier.padding(start = 36.dp), small = true, focusModifier = Modifier.onFocusChanged { if (it.isFocused && index >= comments.replies.size - 3) onMore() })
        }
        when {
            comments.repliesLoading -> item { Text("Loading…", color = TvColors.Muted, modifier = Modifier.padding(start = 42.dp, top = 8.dp)) }
            comments.repliesFailed -> item { Text("Couldn't load replies.", color = TvColors.Muted, modifier = Modifier.padding(start = 42.dp, top = 8.dp)) }
        }
    }
}

@Composable
private fun CommentCard(
    author: String,
    avatar: String,
    age: String,
    body: String,
    likes: String,
    replies: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    maxLines: Int = 6,
    small: Boolean = false,
    focusModifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().then(focusModifier),
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
        // As on YouTube: the round picture, then name and age, the comment, and its counts.
        Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.Top) {
            val picture = if (small) 28.dp else 36.dp
            Box(Modifier.size(picture).clip(CircleShape).background(TvColors.Frost), contentAlignment = Alignment.Center) {
                if (avatar.isNotBlank()) Artwork(avatar, Modifier.fillMaxSize().clip(CircleShape))
                else AppIcon(AppIcon.You, Modifier.size(picture * 0.6f), color = TvColors.Muted)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(author, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, color = TvColors.Text,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (age.isNotBlank()) {
                        Text("  $age", style = MaterialTheme.typography.labelMedium, color = TvColors.Muted, maxLines = 1)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = TvColors.Text, maxLines = maxLines,
                    overflow = TextOverflow.Ellipsis, lineHeight = 20.sp)
                if (likes.isNotBlank() || replies > 0) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (likes.isNotBlank()) {
                            AppIcon(AppIcon.Like, Modifier.size(14.dp), color = TvColors.Muted)
                            Text(" $likes", style = MaterialTheme.typography.labelMedium, color = TvColors.Muted)
                        }
                        if (replies > 0) {
                            if (likes.isNotBlank()) Spacer(Modifier.width(18.dp))
                            Text(if (replies == 1) "1 reply" else "$replies replies", style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium, color = TvColors.FocusedLink)
                        }
                    }
                }
            }
        }
    }
}

/** Live chat as it comes in, newest at the bottom. Reading only: typing chat with a remote isn't practical. */
@Composable
internal fun TvChatList(state: LiveChatUiState) {
    val messages = (state.content as? LiveChatContent.Ready)?.messages.orEmpty()
    val list = rememberLazyListState()
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) list.scrollToItem(messages.size - 1) }
    when {
        state.content is LiveChatContent.Failed -> Text("Couldn't load the chat.", color = TvColors.Muted)
        state.content is LiveChatContent.Loading -> Text("Loading…", color = TvColors.Muted)
        messages.isEmpty() -> Text(if (state.ended) "The chat has ended." else "No messages yet.", color = TvColors.Muted)
        else -> LazyColumn(state = list, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(messages.size, key = { messages[it].id }) { index ->
                val message = messages[index]
                Row(verticalAlignment = Alignment.Top) {
                    if (message.photo.isNotBlank()) {
                        Artwork(message.photo, Modifier.size(28.dp).clip(CircleShape))
                        Spacer(Modifier.width(10.dp))
                    }
                    Column {
                        Text(message.author, style = MaterialTheme.typography.labelLarge, color = TvColors.Muted, maxLines = 1)
                        Text(message.text, style = MaterialTheme.typography.bodyMedium, color = TvColors.Text)
                    }
                }
            }
        }
    }
}

private const val COMMENT_LINES = 6
