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
import com.hhst.youtubelite.comments.domain.CommentThread
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import com.hhst.youtubelite.ui.design.Artwork
import androidx.compose.ui.unit.sp
import com.hhst.youtubelite.player.SleepChoice
import com.hhst.youtubelite.watch.chat.LiveChatContent
import com.hhst.youtubelite.watch.chat.LiveChatUiState
import com.hhst.youtubelite.player.engine.PlaybackApi
import com.hhst.youtubelite.player.engine.PlaybackSnapshot
import com.hhst.youtubelite.player.engine.SubtitleTrack
import com.hhst.youtubelite.player.surface.PlayerUi
import com.hhst.youtubelite.ui.design.AppIcon

/** The player's side panels, one at a time, opened from its buttons. */
internal enum class TvPanel { Description, Comments, Chat, Channel, Save, Settings, Quality, Speed, Captions, Audio, Sleep }

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
internal fun TvSidePanel(title: String, modifier: Modifier = Modifier, wide: Boolean = false, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
        Column(
            Modifier
                .width(if (wide) 560.dp else 460.dp)
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

/** Top comments, one focusable card each so the D-pad scrolls; pressing one opens its replies. */
@Composable
internal fun TvCommentList(comments: TvComments, onNearEnd: () -> Unit, onReplies: (CommentThread) -> Unit, onMoreReplies: () -> Unit) {
    val thread = comments.replyThread
    if (thread != null) {
        TvReplies(thread, comments, onMoreReplies)
        return
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(comments.threads.isNotEmpty()) { if (comments.threads.isNotEmpty()) requestFocusSoon(first) }
    when {
        comments.turnedOff -> Text("Comments are turned off.", color = TvColors.Muted)
        comments.threads.isEmpty() && comments.failed -> Text("Couldn't load comments.", color = TvColors.Muted)
        comments.threads.isEmpty() && comments.loading -> Text("Loading…", color = TvColors.Muted)
        comments.threads.isEmpty() -> Text("No comments yet.", color = TvColors.Muted)
        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(comments.threads.size, key = { comments.threads[it].id }) { index ->
                val item = comments.threads[index]
                CommentCard(
                    author = item.author.displayName.ifBlank { item.author.handle },
                    avatar = item.author.artwork,
                    age = item.age, body = item.body, likes = item.likes,
                    replies = item.replyCount,
                    onClick = { if (item.replyCount > 0) onReplies(item) },
                    modifier = Modifier
                        .then(if (index == 0) Modifier.focusRequester(first) else Modifier)
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
