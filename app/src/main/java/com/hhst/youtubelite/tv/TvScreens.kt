package com.hhst.youtubelite.tv

import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.hhst.youtubelite.R
import com.hhst.youtubelite.player.engine.PlaybackApi
import com.hhst.youtubelite.player.engine.PlaybackSnapshot
import com.hhst.youtubelite.player.surface.PlayerUi
import com.hhst.youtubelite.tv.browse.RelatedRow
import com.hhst.youtubelite.tv.browse.TvBrowse
import com.hhst.youtubelite.tv.browse.TvBrowseModels
import com.hhst.youtubelite.tv.browse.TvCard
import com.hhst.youtubelite.tv.browse.TvListModel
import kotlinx.coroutines.delay

@Composable
internal fun TvApp(receiver: TvReceiver, engine: PlaybackApi, browse: TvBrowseModels, account: String?, onSignOut: () -> Unit, onExit: () -> Unit) {
    val snapshot by receiver.snapshot.collectAsStateWithLifecycle()
    val link by receiver.server.state.collectAsStateWithLifecycle()
    val nowPlaying by receiver.nowPlaying.collectAsStateWithLifecycle()
    val play: (TvCard) -> Unit = { card -> card.videoId?.let { receiver.playLocal(it, card.title, card.author) } }
    Box(Modifier.fillMaxSize().background(TvColors.Background)) {
        val videoId = snapshot.videoId
        if (videoId != null) {
            val hasPhone = link.connectedPhone != null
            val related = browse.related(videoId)
            // Watching on the TV alone, the next recommended video follows, like YouTube's autoplay.
            LaunchedEffect(videoId, snapshot.ended, hasPhone) {
                if (snapshot.ended && !hasPhone) {
                    related.state.value.cards.firstOrNull { it.videoId != null && it.videoId != videoId }?.let(play)
                }
            }
            PlayerScreen(
                snapshot, nowPlaying, engine,
                canSkip = hasPhone,
                related = related,
                onSkip = receiver::skip,
                onPlayCard = play,
                onStop = receiver::stopPlayback,
            )
        } else {
            TvBrowse(browse, link, account, onPlay = play, onForgetPhones = receiver.server::forgetPhones, onSignOut = onSignOut, onExit = onExit)
        }
    }
}

@Composable
private fun PlayerScreen(
    snapshot: PlaybackSnapshot,
    nowPlaying: TvNowPlaying?,
    engine: PlaybackApi,
    canSkip: Boolean,
    related: TvListModel,
    onSkip: (Boolean) -> Unit,
    onPlayCard: (TvCard) -> Unit,
    onStop: () -> Unit,
) {
    var controlsVisible by remember { mutableStateOf(true) }
    var statsVisible by remember { mutableStateOf(false) }
    // Any key press restarts the hide timer.
    var activity by remember { mutableIntStateOf(0) }
    val screenFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }

    LaunchedEffect(controlsVisible, activity, snapshot.isPlaying) {
        if (controlsVisible && snapshot.isPlaying) {
            delay(CONTROLS_HIDE_MS)
            controlsVisible = false
        }
    }
    LaunchedEffect(controlsVisible) {
        runCatching { if (controlsVisible) playFocus.requestFocus() else screenFocus.requestFocus() }
    }
    BackHandler {
        if (controlsVisible) controlsVisible = false else onStop()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(screenFocus)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                activity++
                when (event.key) {
                    Key.MediaPlayPause -> { engine.playOrPause(); true }
                    Key.MediaPlay -> { engine.play(); true }
                    Key.MediaPause -> { engine.pause(); true }
                    Key.MediaFastForward -> { engine.seekRelative(SEEK_STEP_MS); true }
                    Key.MediaRewind -> { engine.seekRelative(-SEEK_STEP_MS); true }
                    Key.MediaNext -> { if (canSkip) onSkip(true); true }
                    Key.MediaPrevious -> { if (canSkip) onSkip(false); true }
                    // With the controls up, the D-pad moves between buttons.
                    else -> if (controlsVisible) false else when (event.key) {
                        Key.DirectionLeft -> { engine.seekRelative(-SEEK_STEP_MS); controlsVisible = true; true }
                        Key.DirectionRight -> { engine.seekRelative(SEEK_STEP_MS); controlsVisible = true; true }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { engine.playOrPause(); controlsVisible = true; true }
                        Key.DirectionUp, Key.DirectionDown -> { controlsVisible = true; true }
                        else -> false
                    }
                }
            }
            .focusable(),
    ) {
        VideoSurface(engine)
        if (statsVisible) StatsOverlay(snapshot, Modifier.align(Alignment.TopStart).padding(32.dp))
        if (snapshot.isBuffering && !controlsVisible) {
            Text("Loading…", color = TvColors.Text, modifier = Modifier.align(Alignment.Center))
        }
        if (controlsVisible) {
            PlayerControls(
                snapshot = snapshot,
                title = nowPlaying?.title?.takeIf { it.isNotBlank() } ?: snapshot.title,
                author = nowPlaying?.author ?: snapshot.author,
                playFocus = playFocus,
                canSkip = canSkip,
                onPlayPause = engine::playOrPause,
                onPrevious = { onSkip(false) },
                onNext = { onSkip(true) },
                onStats = { statsVisible = !statsVisible },
                onStop = onStop,
                related = related,
                onPlayCard = onPlayCard,
            )
        }
    }
}

/**
 * The engine draws into a SurfaceView it already owns on the phone, so the TV reuses that
 * output rather than a second render path.
 */
@Composable
private fun VideoSurface(engine: PlaybackApi) {
    val holder = remember { arrayOfNulls<SurfaceView>(1) }
    AndroidView(
        factory = { context -> SurfaceView(context).also { holder[0] = it; engine.setVideoSurface(it) } },
        modifier = Modifier.fillMaxSize(),
    )
    DisposableEffect(Unit) {
        onDispose { if (holder[0] != null) engine.setVideoSurface(null) }
    }
}

/**
 * Laid out like the YouTube TV player: title and channel at the top left; at the bottom the
 * time and chapter, a chapter-split progress bar, then previous, play and next in the
 * middle with the action pills on the right.
 */
@Composable
private fun PlayerControls(
    snapshot: PlaybackSnapshot,
    title: String,
    author: String?,
    playFocus: FocusRequester,
    canSkip: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onStats: () -> Unit,
    onStop: () -> Unit,
    related: TvListModel,
    onPlayCard: (TvCard) -> Unit,
) {
    // Down from the buttons slides the recommended row up from below, as on YouTube TV.
    var relatedOpen by remember { mutableStateOf(false) }
    val lift by animateDpAsState(if (relatedOpen) 0.dp else RELATED_HIDDEN, label = "relatedLift")
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                .padding(start = 58.dp, end = 58.dp, top = 40.dp, bottom = 48.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                color = TvColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            author?.let {
                Text(it, style = MaterialTheme.typography.titleMedium, color = TvColors.Text.copy(alpha = 0.85f), maxLines = 1)
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .offset(y = lift)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                .padding(top = 64.dp, bottom = 24.dp),
        ) {
          Column(Modifier.padding(horizontal = 48.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
                val chapter = currentChapter(snapshot)
                Text(
                    PlayerUi.formatTime(snapshot.positionMs) + (chapter?.let { " • $it" } ?: ""),
                    color = TvColors.Text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                Text(
                    if (snapshot.isLive) "LIVE" else PlayerUi.formatTime(snapshot.durationMs),
                    color = if (snapshot.isLive) TvColors.Red else TvColors.Text,
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                )
            }
            ProgressBar(snapshot)
            Spacer(Modifier.height(24.dp))
            Box(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (canSkip) FrostButton(R.drawable.ic_player_previous, "Previous", onPrevious, size = 52.dp)
                    FrostButton(
                        if (snapshot.userWantsPlay) R.drawable.ic_player_pause else R.drawable.ic_player_play,
                        if (snapshot.userWantsPlay) "Pause" else "Play",
                        onPlayPause,
                        modifier = Modifier.focusRequester(playFocus),
                        size = 68.dp,
                    )
                    if (canSkip) FrostButton(R.drawable.ic_player_next, "Next", onNext, size = 52.dp)
                }
                FrostGroup(Modifier.align(Alignment.CenterEnd)) {
                    FrostButton(R.drawable.ic_tv_stats, "Stats for nerds", onStats, size = 48.dp, glass = false)
                    FrostButton(R.drawable.ic_tv_stop, "Stop", onStop, size = 48.dp, glass = false)
                }
            }
            snapshot.error?.let {
                Text(it, color = TvColors.Red, modifier = Modifier.padding(top = 12.dp), maxLines = 2)
            }
          }
            Text(
                "Recommended",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = TvColors.Text,
                modifier = Modifier.padding(start = 48.dp, top = 22.dp, bottom = 12.dp),
            )
            RelatedRow(related, onPlay = onPlayCard, modifier = Modifier.onFocusChanged { relatedOpen = it.hasFocus })
        }
    }
}

private fun currentChapter(snapshot: PlaybackSnapshot): String? =
    snapshot.chapters.lastOrNull { it.startSeconds * 1000L <= snapshot.positionMs }?.title?.takeIf { it.isNotBlank() }

/** Played in white, buffered in grey, split at chapters, with skipped sponsor parts in yellow. */
@Composable
private fun ProgressBar(snapshot: PlaybackSnapshot) {
    val duration = snapshot.durationMs.coerceAtLeast(1)
    val played = (snapshot.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val buffered = (snapshot.bufferedPositionMs.toFloat() / duration).coerceIn(played, 1f)
    val chapterStarts = snapshot.chapters.map { it.startSeconds * 1000f / duration }.filter { it > 0f && it < 1f }
    val sponsors = snapshot.sponsorSegments.map { (it.startMs.toFloat() / duration).coerceIn(0f, 1f) to (it.endMs.toFloat() / duration).coerceIn(0f, 1f) }
    Canvas(Modifier.fillMaxWidth().height(6.dp)) {
        val h = size.height
        val corner = CornerRadius(h / 2, h / 2)
        drawRoundRect(Color.White.copy(alpha = 0.3f), size = size, cornerRadius = corner)
        drawRoundRect(Color.White.copy(alpha = 0.5f), size = Size(size.width * buffered, h), cornerRadius = corner)
        sponsors.forEach { (start, end) ->
            drawRect(TvColors.Sponsor, topLeft = Offset(size.width * start, 0f), size = Size(size.width * (end - start), h))
        }
        drawRoundRect(TvColors.Text, size = Size(size.width * played, h), cornerRadius = corner)
        // Chapter gaps, as in the YouTube TV bar.
        chapterStarts.forEach { start ->
            drawRect(Color.Black, topLeft = Offset(size.width * start - 2f, 0f), size = Size(4f, h))
        }
    }
}

/** What the TV is actually doing, to compare buffering against other apps on the same TV. */
@Composable
private fun StatsOverlay(snapshot: PlaybackSnapshot, modifier: Modifier = Modifier) {
    val lines = statsLines(snapshot)
    Column(
        modifier
            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        lines.forEach { line ->
            Text(line, color = TvColors.Text, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
        }
    }
}

internal fun statsLines(snapshot: PlaybackSnapshot): List<String> {
    val d = snapshot.diagnostics
    val fps = d.renderedFps ?: d.nominalFps
    val size = if (d.width != null && d.height != null) "${d.width}x${d.height}" else "?"
    val ahead = ((snapshot.bufferedPositionMs - snapshot.positionMs) / 1000).coerceAtLeast(0)
    return listOfNotNull(
        "Video   $size" + (fps?.let { " @ %.0f fps".format(it) } ?: "") + (snapshot.activeQuality?.let { "  ($it)" } ?: ""),
        "Codec   ${d.videoCodec ?: "?"} / ${d.audioCodec ?: "?"}",
        d.videoDecoder?.let { "Decoder $it" },
        "Bitrate " + listOfNotNull(d.videoBitrate?.let { "%.1f Mbps".format(it / 1_000_000f) },
            d.audioBitrate?.let { "${it / 1000} kbps" }).joinToString(" + ").ifEmpty { "?" },
        "Buffer  ${ahead}s ahead",
        "Stalls  ${d.bufferCount} (${"%.1f".format(d.bufferingMs / 1000f)}s)",
        "Frames  ${d.droppedFrames} dropped of ${d.renderedFrames}",
        d.firstFrameMs?.let { "Start   ${it}ms to first frame" },
    )
}

private val RELATED_HIDDEN = 236.dp
private const val CONTROLS_HIDE_MS = 5_000L
private const val SEEK_STEP_MS = 10_000L
