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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.input.key.onKeyEvent
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
import com.hhst.youtubelite.player.SleepChoice
import com.hhst.youtubelite.player.surface.PlayerUi
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.ui.design.Artwork
import com.hhst.youtubelite.watch.domain.VideoRating
import com.hhst.youtubelite.tv.browse.RelatedRow
import com.hhst.youtubelite.tv.browse.TvBrowse
import com.hhst.youtubelite.tv.browse.TvBrowseModels
import com.hhst.youtubelite.tv.browse.TvCard
import com.hhst.youtubelite.tv.browse.TvListModel
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay

@Composable
internal fun TvApp(receiver: TvReceiver, engine: PlaybackApi, browse: TvBrowseModels, player: TvPlayerSession,
    account: String?, onSignOut: () -> Unit,
    openSignIn: () -> TvSignInSession, onExit: () -> Unit) {
    var signingIn by remember { mutableStateOf(false) }
    val snapshot by receiver.snapshot.collectAsStateWithLifecycle()
    val link by receiver.server.state.collectAsStateWithLifecycle()
    val nowPlaying by receiver.nowPlaying.collectAsStateWithLifecycle()
    val play: (TvCard) -> Unit = { card -> card.videoId?.let { receiver.playLocal(it, card.title, card.author) } }
    Box(Modifier.fillMaxSize().background(TvColors.Background)) {
        val videoId = snapshot.videoId
        if (videoId != null) {
            val hasPhone = link.connectedPhone != null
            val related = browse.related(videoId)
            LaunchedEffect(videoId) { player.video.load(videoId) }
            // Watching on the TV alone, the next recommended video follows, like YouTube's autoplay.
            LaunchedEffect(videoId, snapshot.ended, hasPhone) {
                if (snapshot.ended && !hasPhone && player.sleptOn != videoId) {
                    related.state.value.cards.firstOrNull { it.videoId != null && it.videoId != videoId }?.let(play)
                }
            }
            PlayerScreen(
                snapshot, nowPlaying, engine, player,
                canSkip = hasPhone,
                related = related,
                onSkip = receiver::skip,
                onPlayCard = play,
                onStop = receiver::stopPlayback,
            )
        } else if (signingIn) {
            TvSignInScreen(openSignIn, onClose = { signingIn = false })
        } else {
            TvBrowse(browse, link, account, onPlay = play, onForgetPhones = receiver.server::forgetPhones,
                onSignOut = onSignOut, onSignIn = { signingIn = true }, onExit = onExit)
        }
    }
}

@Composable
private fun PlayerScreen(
    snapshot: PlaybackSnapshot,
    nowPlaying: TvNowPlaying?,
    engine: PlaybackApi,
    player: TvPlayerSession,
    canSkip: Boolean,
    related: TvListModel,
    onSkip: (Boolean) -> Unit,
    onPlayCard: (TvCard) -> Unit,
    onStop: () -> Unit,
) {
    val video by player.video.state.collectAsStateWithLifecycle()
    val sleep by player.sleep.collectAsStateWithLifecycle()
    var panel by remember { mutableStateOf<TvPanel?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    var statsVisible by remember { mutableStateOf(false) }
    // Any key press restarts the hide timer.
    var activity by remember { mutableIntStateOf(0) }
    val screenFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    /** The key that brought the controls up; its release must not also press what got focus. */
    var swallowRelease by remember { mutableStateOf<Key?>(null) }

    LaunchedEffect(controlsVisible, activity, snapshot.isPlaying, panel) {
        if (controlsVisible && snapshot.isPlaying && panel == null) {
            delay(CONTROLS_HIDE_MS)
            controlsVisible = false
        }
    }
    LaunchedEffect(controlsVisible, panel) {
        // The controls compose a frame later than this effect runs: retry until focus lands.
        if (panel == null) requestFocusSoon(if (controlsVisible) playFocus else screenFocus)
    }
    LaunchedEffect(video.notice) {
        if (video.notice != null) {
            delay(NOTICE_MS)
            player.video.clearNotice()
        }
    }
    BackHandler {
        when {
            panel in SETTINGS_CHILDREN -> panel = TvPanel.Settings
            panel != null -> panel = null
            controlsVisible -> controlsVisible = false
            else -> onStop()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(screenFocus)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp) {
                    if (event.key != swallowRelease) return@onPreviewKeyEvent false
                    swallowRelease = null
                    return@onPreviewKeyEvent true
                }
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
                    // With the controls up, the D-pad moves between them; the scrubber seeks.
                    else -> if (controlsVisible || panel != null) false else {
                        val handled = when (event.key) {
                            Key.DirectionLeft -> { engine.seekRelative(-seekStep(event.nativeKeyEvent.repeatCount)); true }
                            Key.DirectionRight -> { engine.seekRelative(seekStep(event.nativeKeyEvent.repeatCount)); true }
                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { engine.playOrPause(); true }
                            Key.DirectionUp, Key.DirectionDown -> true
                            else -> false
                        }
                        if (handled) {
                            controlsVisible = true
                            swallowRelease = event.key
                        }
                        handled
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
        if (controlsVisible && panel == null) {
            PlayerControls(
                snapshot = snapshot,
                engine = engine,
                video = video,
                onPanel = { panel = it },
                onLike = player.video::like,
                onDislike = player.video::dislike,
                onSubscribe = player.video::toggleSubscribe,
                title = nowPlaying?.title?.takeIf { it.isNotBlank() } ?: snapshot.title,
                author = nowPlaying?.author ?: snapshot.author,
                playFocus = playFocus,
                canSkip = canSkip,
                onPlayPause = engine::playOrPause,
                onPrevious = { onSkip(false) },
                onNext = { onSkip(true) },
                related = related,
                onPlayCard = onPlayCard,
            )
        }
        panel?.let { open ->
            PlayerPanel(open, snapshot, video, sleep, engine, player, statsVisible,
                onStats = { statsVisible = !statsVisible; panel = null }, onPanel = { panel = it })
        }
        video.notice?.let {
            Text(
                it,
                color = TvColors.Text,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 32.dp)
                    .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 24.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun PlayerPanel(
    panel: TvPanel,
    snapshot: PlaybackSnapshot,
    video: TvVideoState,
    sleep: SleepChoice,
    engine: PlaybackApi,
    player: TvPlayerSession,
    statsVisible: Boolean,
    onStats: () -> Unit,
    onPanel: (TvPanel?) -> Unit,
) {
    val pick: (TvPanelRow) -> Unit = { row ->
        applyPick(panel, row.key, engine, player::setSleep)
        onPanel(TvPanel.Settings)
    }
    when (panel) {
        TvPanel.Description -> TvSidePanel(video.video?.title ?: snapshot.title) {
            val page = video.video
            TvDescription(
                meta = listOfNotNull(page?.views, page?.age, page?.likes?.let { "$it likes" }).filter(String::isNotBlank).joinToString(" • "),
                text = page?.description.orEmpty(),
            )
        }
        TvPanel.Save -> TvSidePanel("Save to") {
            val choices = video.saveChoices
            when {
                video.savesFailed -> Text("Couldn't load your playlists.", color = TvColors.Muted)
                choices == null -> Text("Loading…", color = TvColors.Muted)
                choices.isEmpty() -> Text("No playlists yet.", color = TvColors.Muted)
                else -> TvPanelList(choices.map { TvPanelRow(it.id, it.title, checked = it.contains) }) { row ->
                    choices.firstOrNull { it.id == row.key }?.let(player.video::toggleSave)
                }
            }
        }
        TvPanel.Settings -> TvSidePanel("Settings") {
            TvPanelList(settingsRows(snapshot, sleep) + TvPanelRow(STATS, "Stats for nerds", if (statsVisible) "On" else "Off")) { row ->
                if (row.key == STATS) onStats() else onPanel(TvPanel.valueOf(row.key))
            }
        }
        TvPanel.Quality -> TvSidePanel("Quality") { TvPanelList(qualityRows(snapshot), pick) }
        TvPanel.Speed -> TvSidePanel("Playback speed") { TvPanelList(speedRows(snapshot), pick) }
        TvPanel.Captions -> TvSidePanel("Captions") { TvPanelList(captionRows(snapshot), pick) }
        TvPanel.Audio -> TvSidePanel("Audio track") { TvPanelList(audioRows(snapshot), pick) }
        TvPanel.Sleep -> TvSidePanel("Sleep timer") { TvPanelList(sleepRows(sleep), pick) }
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
    engine: PlaybackApi,
    video: TvVideoState,
    onPanel: (TvPanel) -> Unit,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onSubscribe: () -> Unit,
    title: String,
    author: String?,
    playFocus: FocusRequester,
    canSkip: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    related: TvListModel,
    onPlayCard: (TvCard) -> Unit,
) {
    // Down from the buttons slides the recommended row up from below, as on YouTube TV.
    var relatedOpen by remember { mutableStateOf(false) }
    val lift by animateDpAsState(if (relatedOpen) 0.dp else RELATED_HIDDEN, label = "relatedLift")
    Box(Modifier.fillMaxSize()) {
        // With the recommended row up, the title gives it the room, as on YouTube TV.
        if (!relatedOpen) Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                .padding(start = 58.dp, end = 58.dp, top = 40.dp, bottom = 48.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                color = TvColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                video.video?.avatar?.takeIf(String::isNotBlank)?.let {
                    Artwork(it, Modifier.size(40.dp).clip(CircleShape))
                    Spacer(Modifier.width(12.dp))
                }
                (video.video?.channel?.takeIf(String::isNotBlank) ?: author)?.let {
                    Text(it, style = MaterialTheme.typography.titleMedium, color = TvColors.Text.copy(alpha = 0.9f), maxLines = 1)
                }
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
            Scrubber(snapshot, engine, Modifier.focusRequester(playFocus))
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
                        size = 68.dp,
                    )
                    if (canSkip) FrostButton(R.drawable.ic_player_next, "Next", onNext, size = 52.dp)
                }
                // Channel side, as on YouTube TV: subscribe and the description.
                Row(Modifier.align(Alignment.CenterStart), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (video.video?.channelId?.isNotBlank() == true) {
                        FrostPill(if (video.subscribed) "Subscribed" else "Subscribe", onSubscribe, active = video.subscribed)
                    }
                    FrostPill("Description", { onPanel(TvPanel.Description) }, active = true)
                }
                FrostGroup(Modifier.align(Alignment.CenterEnd)) {
                    FrostButton(AppIcon.Like, "Like", onLike, size = 48.dp, glass = false, active = video.rating == VideoRating.Like)
                    FrostButton(AppIcon.Dislike, "Dislike", onDislike, size = 48.dp, glass = false, active = video.rating == VideoRating.Dislike)
                    FrostButton(AppIcon.Bookmark, "Save", { onPanel(TvPanel.Save) }, size = 48.dp, glass = false)
                    if (snapshot.subtitleTracks.isNotEmpty()) {
                        FrostButton(
                            if (snapshot.subtitleEnabled) R.drawable.ic_subtitles_on else R.drawable.ic_subtitles_off,
                            "Captions", { onPanel(TvPanel.Captions) }, size = 48.dp, glass = false,
                        )
                    }
                    FrostButton(AppIcon.Settings, "Settings", { onPanel(TvPanel.Settings) }, size = 48.dp, glass = false)
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

/**
 * The progress bar takes focus first when the controls come up, as on YouTube TV: left and
 * right seek (further the longer they are held), OK plays or pauses, down reaches the buttons.
 */
@Composable
private fun Scrubber(snapshot: PlaybackSnapshot, engine: PlaybackApi, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .fillMaxWidth()
            .height(24.dp)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> { engine.seekRelative(-seekStep(event.nativeKeyEvent.repeatCount)); true }
                    Key.DirectionRight -> { engine.seekRelative(seekStep(event.nativeKeyEvent.repeatCount)); true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { engine.playOrPause(); true }
                    else -> false
                }
            }
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        ProgressBar(snapshot, thick = focused)
    }
}

/** 10 s a press; held down, the steps grow so a long video can be crossed quickly. */
private fun seekStep(repeatCount: Int): Long = when {
    repeatCount >= 20 -> 60_000L
    repeatCount >= 8 -> 30_000L
    else -> SEEK_STEP_MS
}

/** Moves focus to [requester] once its target is on screen, trying for about a second. */
internal suspend fun requestFocusSoon(requester: FocusRequester) {
    repeat(FOCUS_TRIES) {
        if (runCatching { requester.requestFocus() }.isSuccess) return
        withFrameNanos { }
    }
}

/** Played in white, buffered in grey, split at chapters, with skipped sponsor parts in yellow. */
@Composable
private fun ProgressBar(snapshot: PlaybackSnapshot, thick: Boolean = false) {
    val duration = snapshot.durationMs.coerceAtLeast(1)
    val played = (snapshot.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val buffered = (snapshot.bufferedPositionMs.toFloat() / duration).coerceIn(played, 1f)
    val chapterStarts = snapshot.chapters.map { it.startSeconds * 1000f / duration }.filter { it > 0f && it < 1f }
    val sponsors = snapshot.sponsorSegments.map { (it.startMs.toFloat() / duration).coerceIn(0f, 1f) to (it.endMs.toFloat() / duration).coerceIn(0f, 1f) }
    Canvas(Modifier.fillMaxWidth().height(if (thick) 10.dp else 5.dp)) {
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
        // The playhead, shown while the bar has focus.
        if (thick) drawCircle(TvColors.Text, radius = h * 1.3f, center = Offset(size.width * played, h / 2))
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

private val SETTINGS_CHILDREN = setOf(TvPanel.Quality, TvPanel.Speed, TvPanel.Captions, TvPanel.Audio, TvPanel.Sleep)
private const val NOTICE_MS = 3_000L
private val RELATED_HIDDEN = 236.dp
private const val STATS = "stats"
private const val FOCUS_TRIES = 60
private const val CONTROLS_HIDE_MS = 5_000L
private const val SEEK_STEP_MS = 10_000L
