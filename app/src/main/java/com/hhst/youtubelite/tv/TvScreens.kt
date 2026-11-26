package com.hhst.youtubelite.tv

import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.focus.FocusDirection
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
import com.hhst.youtubelite.player.engine.PlaybackDiagnostics
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.draw.alpha
import com.hhst.youtubelite.player.SleepChoice
import com.hhst.youtubelite.player.surface.PlayerUi
import com.hhst.youtubelite.player.surface.SubtitleStyle
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.ui.design.Artwork
import com.hhst.youtubelite.watch.domain.VideoRating
import com.hhst.youtubelite.tv.browse.RelatedRow
import com.hhst.youtubelite.tv.browse.TvBrowse
import com.hhst.youtubelite.tv.browse.TvBrowseModels
import com.hhst.youtubelite.tv.browse.TvCard
import com.hhst.youtubelite.tv.browse.TvListModel
import com.hhst.youtubelite.tv.browse.TvPage
import com.hhst.youtubelite.tv.browse.RepositoryTvBrowseSource
import com.hhst.youtubelite.tv.browse.TvSettingsStore
import com.hhst.youtubelite.extension.PreferenceKeys
import com.hhst.youtubelite.watch.chat.LiveChatUiState
import com.hhst.youtubelite.watch.chat.LiveChatViewModel
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay

@Composable
internal fun TvApp(receiver: TvReceiver, engine: PlaybackApi, browse: TvBrowseModels, player: TvPlayerSession,
    account: String?, accountPhoto: String?, settings: TvSettingsStore, version: String, chat: LiveChatViewModel,
    captions: TvCaptionStyleStore,
    onSignOut: () -> Unit, openSignIn: () -> TvSignInSession, onOpenDownloads: () -> Unit,
    onDownload: (videoId: String, title: String, author: String?, artwork: String?) -> Unit, onExit: () -> Unit) {
    var signingIn by remember { mutableStateOf(false) }
    // The player reads position (four times a second) only where it shows it; everything
    // else sees a copy without it, so the whole overlay doesn't redraw on every tick.
    val snapshotState = receiver.snapshot.collectAsStateWithLifecycle()
    val snapshot by remember { derivedStateOf(structuralEqualityPolicy()) { withoutPosition(snapshotState.value) } }
    val live: () -> PlaybackSnapshot = remember { { snapshotState.value } }
    val link by receiver.server.state.collectAsStateWithLifecycle()
    val nowPlaying by receiver.nowPlaying.collectAsStateWithLifecycle()
    val values by settings.values.collectAsStateWithLifecycle()
    /** The list the current video was started from, when it plays in order (a playlist, or Shorts). */
    var queue by remember { mutableStateOf<List<TvCard>>(emptyList()) }
    /** The paged list behind [queue], so a long playlist keeps reading pages as it plays. */
    var queueSource by remember { mutableStateOf<TvListModel?>(null) }
    var queueShorts by remember { mutableStateOf(false) }
    /** Cards put first with "Play next" from a tile's menu. */
    var playNext by remember { mutableStateOf<List<TvCard>>(emptyList()) }
    val play: (TvCard, List<TvCard>) -> Unit = { card, list ->
        queue = playQueue(card, list)
        queueSource = browse.sourceOf(list).takeIf { queue.isNotEmpty() }
        queueShorts = card.short
        playNext = playNext.filterNot { it.videoId == card.videoId }
        card.videoId?.let { receiver.playLocal(it, card.title, card.author) }
    }
    val currentQueue: () -> List<TvCard> = {
        queueSource?.state?.value?.cards?.let { cards -> playQueue(cards.firstOrNull { it.short == queueShorts } ?: return@let null, cards) }
            ?.takeIf { it.isNotEmpty() } ?: queue
    }
    Box(Modifier.fillMaxSize().background(TvColors.Background)) {
        val videoId = snapshot.videoId
        if (videoId != null) {
            val hasPhone = link.connectedPhone != null
            val related = if (values[PreferenceKeys.ENABLE_HIDE_RELATED] == true) NO_RELATED else browse.related(videoId)
            val relatedState by related.state.collectAsStateWithLifecycle()
            val following: (Boolean) -> TvCard? = { forward ->
                val list = currentQueue()
                val index = list.indexOfFirst { it.videoId == videoId }
                list.getOrNull(if (forward) index + 1 else index - 1).takeIf { index >= 0 }
            }
            LaunchedEffect(videoId) { player.video.load(videoId) }
            // The next video comes from here even when the overlay (and its row) never opened.
            LaunchedEffect(related) { related.loadFirst() }
            // Near the end of what's loaded of a playlist or Shorts, read its next page.
            LaunchedEffect(videoId, queueSource) {
                val list = currentQueue()
                val index = list.indexOfFirst { it.videoId == videoId }
                if (index >= 0 && index >= list.size - QUEUE_PREFETCH) queueSource?.loadMore()
            }
            // Watching on the TV alone, the next video follows, like YouTube's autoplay: a
            // "Play next" pick, the next one in the playlist or Shorts, else the top recommendation.
            val autoplay = !hasPhone && player.sleptOn != videoId && values[PreferenceKeys.ENABLE_AUTOPLAY] != false
            val nextUp = if (!snapshot.ended || !autoplay) null else
                playNext.firstOrNull() ?: following(true)
                    ?: relatedState.cards.firstOrNull { it.videoId != null && it.videoId != videoId }
            // About 20 s before the end, get the next video's streams so it starts at once.
            if (autoplay) LaunchedEffect(videoId) {
                androidx.compose.runtime.snapshotFlow { live().let { it.durationMs > 0 && it.durationMs - it.positionMs in 1..WARM_NEXT_MS } }
                    .first { it }
                val next = playNext.firstOrNull() ?: following(true)
                    ?: relatedState.cards.firstOrNull { it.videoId != null && it.videoId != videoId }
                TvWarmup.now(next?.videoId)
            }
            PlayerScreen(
                snapshot, live, nowPlaying, engine, player, chat, captions,
                canSkip = hasPhone || currentQueue().isNotEmpty() || playNext.isNotEmpty(),
                related = related,
                nextUp = nextUp,
                waitingForNext = snapshot.ended && autoplay && nextUp == null && relatedState.loading,
                onPlayNext = { card -> play(card, if (card in playNext) emptyList() else currentQueue()) },
                onSkip = { forward ->
                    when {
                        hasPhone -> receiver.skip(forward)
                        forward && playNext.isNotEmpty() -> play(playNext.first(), emptyList())
                        else -> following(forward)?.let { play(it, currentQueue()) }
                    }
                },
                onPlayCard = play,
                onOpenChannel = { channelId ->
                    // Only leave the video for a channel that exists; before the page loads there's none.
                    if (channelId.isNotBlank()) {
                        receiver.stopPlayback()
                        browse.openChannel(channelId)
                    }
                },
                onDownload = onDownload,
                onStop = receiver::stopPlayback,
            )
        } else if (signingIn) {
            TvSignInScreen(openSignIn, onClose = { signingIn = false })
        } else {
            TvBrowse(browse, link, account, accountPhoto, settings, version, onPlay = play,
                onPlayNext = { card -> playNext = playNext + card },
                onForgetPhones = receiver.server::forgetPhones,
                onSignOut = onSignOut, onSignIn = { signingIn = true }, onOpenDownloads = onOpenDownloads, onExit = onExit)
            if (tvUpdateWaiting()) TvUpdatePanel()
        }
    }
}

/** The snapshot without the fields that change on every tick, for screens that don't show them. */
internal fun withoutPosition(snapshot: PlaybackSnapshot): PlaybackSnapshot =
    snapshot.copy(positionMs = 0L, bufferedPositionMs = 0L, diagnostics = PlaybackDiagnostics(), discontinuityEpoch = 0L,
        subtitleCues = emptyList())

/** Playlists and Shorts play in order; anything else is followed by its recommendations. */
internal fun playQueue(card: TvCard, list: List<TvCard>): List<TvCard> = when {
    card.short -> list.filter { it.short && it.videoId != null }
    list.isNotEmpty() && list.all { it.key.startsWith(RepositoryTvBrowseSource.ENTRY_PREFIX) } -> list.filter { it.videoId != null }
    else -> emptyList()
}

/** The recommended row when Hide recommended videos is on: never any tiles. */
private val NO_RELATED by lazy {
    TvListModel(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate)) { TvPage(emptyList()) }
}

@Composable
private fun PlayerScreen(
    snapshot: PlaybackSnapshot,
    live: () -> PlaybackSnapshot,
    nowPlaying: TvNowPlaying?,
    engine: PlaybackApi,
    player: TvPlayerSession,
    chat: LiveChatViewModel,
    captions: TvCaptionStyleStore,
    canSkip: Boolean,
    related: TvListModel,
    nextUp: TvCard?,
    waitingForNext: Boolean,
    onPlayNext: (TvCard) -> Unit,
    onSkip: (Boolean) -> Unit,
    onPlayCard: (TvCard, List<TvCard>) -> Unit,
    onOpenChannel: (String) -> Unit,
    onDownload: (videoId: String, title: String, author: String?, artwork: String?) -> Unit,
    onStop: () -> Unit,
) {
    val video by player.video.state.collectAsStateWithLifecycle()
    val chatState by chat.uiState.collectAsStateWithLifecycle()
    val sleep by player.sleep.collectAsStateWithLifecycle()
    val captionStyle by captions.style.collectAsStateWithLifecycle()
    var panel by remember { mutableStateOf<TvPanel?>(null) }
    /** The settings row last opened, so going back lands on it again. */
    var settingsReturn by remember { mutableStateOf<String?>(null) }
    val openPanel: (TvPanel?) -> Unit = { next ->
        if (next in SETTINGS_CHILDREN) settingsReturn = next?.name
        if (next == null) settingsReturn = null
        panel = next
    }
    var controlsVisible by remember { mutableStateOf(true) }
    var statsVisible by remember { mutableStateOf(false) }
    // Any key press restarts the hide timer.
    var activity by remember { mutableIntStateOf(0) }
    val screenFocus = remember { FocusRequester() }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
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
    // A failed video brings the controls up, where the error and Try again are.
    LaunchedEffect(snapshot.error) { if (snapshot.error != null) controlsVisible = true }
    // Up next, as on YouTube TV: a countdown that Back or Cancel stops.
    var upNextCancelled by remember(snapshot.videoId) { mutableStateOf(false) }
    val showUpNext = nextUp != null && !upNextCancelled
    val showEnd = snapshot.ended && !showUpNext && !waitingForNext
    LaunchedEffect(showUpNext, showEnd) { if (showUpNext || showEnd) { controlsVisible = false; panel = null } }
    // Left and right with the controls hidden add up, and seek once the presses stop.
    var pendingSeek by remember { mutableStateOf(0L) }
    var seekPresses by remember { mutableIntStateOf(0) }
    LaunchedEffect(seekPresses) {
        if (pendingSeek == 0L) return@LaunchedEffect
        delay(HIDDEN_SEEK_SETTLE_MS)
        engine.seekRelative(pendingSeek)
        pendingSeek = 0L
    }
    LaunchedEffect(video.notice) {
        if (video.notice != null) {
            delay(NOTICE_MS)
            player.video.clearNotice()
        }
    }
    // The chat streams only while its panel is open.
    val page = video.video
    val chatKey = page?.chatKey?.takeIf { (page.live || snapshot.isLive) && it.isNotBlank() }
    DisposableEffect(panel == TvPanel.Chat, chatKey) {
        if (panel == TvPanel.Chat && chatKey != null && page != null) {
            chat.open(chatKey, com.liskovsoft.youtubeapi.videoinfo.V2.chatMessageParameters(page.id, page.channelId))
        }
        onDispose { chat.close() }
    }
    BackHandler {
        when {
            showUpNext -> upNextCancelled = true
            panel == TvPanel.Comments && player.video.closeReplies() -> {}
            panel == TvPanel.CaptionStyle -> openPanel(TvPanel.Captions)
            panel in SETTINGS_CHILDREN -> openPanel(TvPanel.Settings)
            panel != null -> openPanel(null)
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
                    else -> if (controlsVisible || panel != null || showUpNext || showEnd) false else {
                        val handled = when (event.key) {
                            Key.DirectionLeft, Key.DirectionRight -> {
                                val step = seekStep(event.nativeKeyEvent.repeatCount)
                                pendingSeek += if (event.key == Key.DirectionLeft) -step else step
                                seekPresses++
                                true
                            }
                            // OK brings the controls up and leaves the video playing, as on YouTube TV.
                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> true
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
            // Up with nothing above the focused control (the top row): the overlay goes away.
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || event.key != Key.DirectionUp) return@onKeyEvent false
                if (!controlsVisible || panel != null) return@onKeyEvent false
                if (!focusManager.moveFocus(FocusDirection.Up)) {
                    controlsVisible = false
                    swallowRelease = Key.DirectionUp
                }
                true
            }
            .focusable(),
    ) {
        VideoSurface(engine)
        TvCaptionLayer(live, captionStyle, lifted = controlsVisible || panel != null)
        if (statsVisible) StatsOverlay(live, Modifier.align(Alignment.TopStart).padding(32.dp))
        // A spinner whenever the video is loading or catching up, controls or not.
        if (snapshot.isBuffering && snapshot.error == null) {
            androidx.compose.material3.CircularProgressIndicator(
                color = TvColors.Text, strokeWidth = 4.dp,
                modifier = Modifier.align(Alignment.Center).size(56.dp),
            )
        }
        if (pendingSeek != 0L) {
            Text((if (pendingSeek > 0) "+" else "−") + PlayerUi.formatTime(kotlin.math.abs(pendingSeek)),
                color = TvColors.Text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 18.dp, vertical = 8.dp))
        }
        if (showUpNext && nextUp != null) {
            UpNextCard(nextUp, onPlay = { onPlayNext(nextUp) }, onCancel = { upNextCancelled = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(48.dp))
        } else if (showEnd) {
            EndCard(onReplay = { engine.seekTo(0L); engine.playOrPause() }, onBack = onStop, modifier = Modifier.align(Alignment.Center))
        }
        AnimatedVisibility(
            visible = controlsVisible && panel == null,
            enter = fadeIn(tween(FADE_IN_MS)),
            exit = fadeOut(tween(FADE_OUT_MS)),
        ) {
            PlayerControls(
                snapshot = snapshot,
                live = live,
                engine = engine,
                video = video,
                onPanel = openPanel,
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
                hasChat = chatKey != null,
            )
        }
        // The last panel stays drawn while it fades out.
        var shownPanel by remember { mutableStateOf<TvPanel?>(null) }
        if (panel != null) shownPanel = panel
        AnimatedVisibility(
            visible = panel != null,
            enter = fadeIn(tween(FADE_IN_MS)),
            exit = fadeOut(tween(FADE_OUT_MS)),
        ) {
          shownPanel?.let { open ->
            PlayerPanel(open, snapshot, video, sleep, engine, player, statsVisible, settingsReturn, chatState, captionStyle, captions::set,
                onStats = { statsVisible = !statsVisible; openPanel(null) }, onPanel = openPanel,
                onOpenChannel = onOpenChannel,
                onDownload = {
                    val id = video.videoId ?: snapshot.videoId
                    if (id != null) onDownload(id, page?.title ?: snapshot.title, page?.channel ?: snapshot.author, page?.artwork)
                    openPanel(null)
                })
          }
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
    settingsReturn: String?,
    chat: LiveChatUiState,
    captionStyle: SubtitleStyle,
    onCaptionStyle: (SubtitleStyle) -> Unit,
    onStats: () -> Unit,
    onPanel: (TvPanel?) -> Unit,
    onOpenChannel: (String) -> Unit,
    onDownload: () -> Unit,
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
        TvPanel.Comments -> TvSidePanel(listOf("Comments", video.comments.total).filter(String::isNotBlank).joinToString(" • "), wide = true) {
            LaunchedEffect(video.videoId) { if (!video.comments.started) player.video.loadComments() }
            TvCommentList(video.comments, onNearEnd = player.video::loadComments,
                onReplies = player.video::loadReplies,
                onMoreReplies = { video.comments.replyThread?.let(player.video::loadReplies) })
        }
        TvPanel.Chat -> TvSidePanel("Live chat", wide = true) { TvChatList(chat) }
        TvPanel.Channel -> TvSidePanel(video.video?.channel.orEmpty()) {
            val channelId = video.video?.channelId.orEmpty()
            // Before the video's page is in there's no channel to open yet: read it again.
            if (channelId.isBlank()) {
                LaunchedEffect(Unit) { player.video.retry() }
                Text(if (video.loadFailed) "Couldn't load this channel." else "Loading the channel…", color = TvColors.Muted)
                return@TvSidePanel
            }
            TvPanelList(listOf(
                TvPanelRow(GO_TO_CHANNEL, "Go to channel"),
                TvPanelRow(SUBSCRIBE, if (video.subscribed) "Unsubscribe" else "Subscribe"),
            )) { row ->
                when (row.key) {
                    GO_TO_CHANNEL -> onOpenChannel(channelId)
                    SUBSCRIBE -> { player.video.toggleSubscribe(); onPanel(null) }
                }
            }
        }
        TvPanel.Save -> TvSidePanel("Save to") {
            LaunchedEffect(video.videoId) { player.video.openSaves() }
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
            TvPanelList(settingsRows(snapshot, sleep) + TvPanelRow(DOWNLOAD, "Download") +
                TvPanelRow(STATS, "Stats for nerds", if (statsVisible) "On" else "Off"),
                focusKey = settingsReturn) { row ->
                when (row.key) {
                    STATS -> onStats()
                    DOWNLOAD -> onDownload()
                    LOOP -> engine.setLoopMode(toggledLoop(snapshot.loopMode))
                    else -> onPanel(TvPanel.valueOf(row.key))
                }
            }
        }
        TvPanel.Quality -> TvSidePanel("Quality") { TvPanelList(qualityRows(snapshot), onPick = pick) }
        TvPanel.Speed -> TvSidePanel("Playback speed") { TvPanelList(speedRows(snapshot), onPick = pick) }
        TvPanel.Captions -> TvSidePanel("Captions") {
            TvPanelList(captionRows(snapshot) + TvPanelRow(CAPTION_STYLE, "Caption style", captionSummary(captionStyle))) { row ->
                if (row.key == CAPTION_STYLE) onPanel(TvPanel.CaptionStyle) else pick(row)
            }
        }
        TvPanel.CaptionStyle -> TvSidePanel("Caption style") {
            TvPanelList(captionStyleRows(captionStyle), focusKey = CaptionSetting.Size.name) { row ->
                onCaptionStyle(stepCaptionStyle(captionStyle, CaptionSetting.valueOf(row.key)))
            }
        }
        TvPanel.Audio -> TvSidePanel("Audio track") { TvPanelList(audioRows(snapshot), onPick = pick) }
        TvPanel.Sleep -> TvSidePanel("Sleep timer") { TvPanelList(sleepRows(sleep), onPick = pick) }
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
    live: () -> PlaybackSnapshot,
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
    onPlayCard: (TvCard, List<TvCard>) -> Unit,
    hasChat: Boolean,
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
                .padding(start = 48.dp, end = 48.dp, top = 26.dp, bottom = 36.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                color = TvColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            // "Channel • views • age", one line under the title, as on YouTube TV.
            val meta = listOfNotNull(video.video?.channel?.takeIf(String::isNotBlank) ?: author,
                video.video?.views, video.video?.age).filter(String::isNotBlank).joinToString(" • ")
            if (meta.isNotBlank()) {
                Text(meta, style = MaterialTheme.typography.titleSmall, color = TvColors.Text.copy(alpha = 0.9f), maxLines = 1)
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .offset(y = lift)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                .padding(top = 44.dp, bottom = 14.dp),
        ) {
          Column(Modifier.padding(horizontal = 48.dp)) {
            TimeRow(live)
            Scrubber(live, engine, Modifier.focusRequester(playFocus))
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (canSkip) FrostButton(R.drawable.ic_player_previous, "Previous", onPrevious, size = 48.dp)
                    FrostButton(
                        if (snapshot.userWantsPlay) R.drawable.ic_player_pause else R.drawable.ic_player_play,
                        if (snapshot.userWantsPlay) "Pause" else "Play",
                        onPlayPause,
                        size = 64.dp,
                    )
                    if (canSkip) FrostButton(R.drawable.ic_player_next, "Next", onNext, size = 48.dp)
                }
                // Channel side, as on YouTube TV: the channel's picture, Description, then
                // Subscribe, or the bell once subscribed.
                Row(
                    Modifier.align(Alignment.CenterStart),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FrostAvatar(video.video?.avatar, video.video?.channel ?: author.orEmpty(), { onPanel(TvPanel.Channel) })
                    FrostPill("Description", { onPanel(TvPanel.Description) })
                    if (video.video?.channelId?.isNotBlank() == true) {
                        if (video.subscribed) {
                            FrostButton(AppIcon.Bell, "Subscribed", { onPanel(TvPanel.Channel) }, size = 48.dp)
                        } else {
                            FrostPill("Subscribe", onSubscribe)
                        }
                    }
                }
                Row(
                    Modifier.align(Alignment.CenterEnd),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FrostGroup(Modifier.alpha(if (video.busy) 0.55f else 1f)) {
                        // Pressed thumbs fill in white, as on YouTube, instead of turning red;
                        // dimmed while YouTube is still answering.
                        FrostButton(if (video.rating == VideoRating.Like) AppIcon.LikeFilled else AppIcon.Like, "Like", onLike, size = 44.dp, glass = false)
                        FrostButton(if (video.rating == VideoRating.Dislike) AppIcon.DislikeFilled else AppIcon.Dislike, "Dislike", onDislike, size = 44.dp, glass = false)
                        FrostButton(AppIcon.Comment, if (hasChat) "Live chat" else "Comments",
                            { onPanel(if (hasChat) TvPanel.Chat else TvPanel.Comments) }, size = 44.dp, glass = false)
                        FrostButton(AppIcon.Bookmark, "Save", { onPanel(TvPanel.Save) }, size = 44.dp, glass = false)
                    }
                    FrostGroup {
                        if (snapshot.subtitleTracks.isNotEmpty()) {
                            FrostButton(
                                if (snapshot.subtitleEnabled) R.drawable.ic_subtitles_on else R.drawable.ic_subtitles_off,
                                "Captions", { onPanel(TvPanel.Captions) }, size = 44.dp, glass = false,
                            )
                        }
                        FrostButton(AppIcon.Settings, "Settings", { onPanel(TvPanel.Settings) }, size = 44.dp, glass = false)
                    }
                }
            }
            snapshot.error?.let {
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(it, color = TvColors.Red, maxLines = 2, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(14.dp))
                    FrostPill("Try again", engine::retry)
                }
            }
          }
            Text(
                "Recommended",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = TvColors.Text,
                modifier = Modifier.padding(start = 48.dp, top = 14.dp, bottom = 8.dp),
            )
            RelatedRow(related, onPlay = onPlayCard, modifier = Modifier.onFocusChanged { relatedOpen = it.hasFocus })
        }
    }
}

/** Time and chapter over the bar: the only text that changes as the video plays. */
@Composable
private fun TimeRow(live: () -> PlaybackSnapshot) {
    val snapshot = live()
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
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
}

/** Up next with a countdown, as on YouTube TV; it plays when the count runs out. */
@Composable
private fun UpNextCard(card: TvCard, onPlay: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    var left by remember(card.key) { mutableIntStateOf(UP_NEXT_SECONDS) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(card.key) {
        requestFocusSoon(focus)
        while (left > 0) { delay(1_000L); left-- }
        onPlay()
    }
    Column(modifier.width(380.dp).background(Color(0xE6181818), RoundedCornerShape(18.dp)).padding(18.dp)) {
        Text("Up next in $left", style = MaterialTheme.typography.titleSmall, color = TvColors.Muted)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Artwork(card.artwork, Modifier.width(140.dp).height(79.dp).clip(RoundedCornerShape(8.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(card.title, style = MaterialTheme.typography.titleSmall, color = TvColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                card.author?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = TvColors.Muted, maxLines = 1) }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FrostPill("Play now", onPlay, Modifier.focusRequester(focus))
            FrostPill("Cancel", onCancel)
        }
    }
}

/** The end of a video with nothing to play next. */
@Composable
private fun EndCard(onReplay: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { requestFocusSoon(focus) }
    Row(modifier.background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(18.dp)).padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FrostPill("Replay", onReplay, Modifier.focusRequester(focus))
        FrostPill("Back", onBack)
    }
}

private fun currentChapter(snapshot: PlaybackSnapshot): String? =
    snapshot.chapters.lastOrNull { it.startSeconds * 1000L <= snapshot.positionMs }?.title?.takeIf { it.isNotBlank() }

/**
 * The progress bar takes focus first when the controls come up, as on YouTube TV: left and
 * right seek (further the longer they are held), OK plays or pauses, down reaches the buttons.
 */
@Composable
private fun Scrubber(live: () -> PlaybackSnapshot, engine: PlaybackApi, modifier: Modifier = Modifier) {
    val snapshot = live()
    var focused by remember { mutableStateOf(false) }
    // Left and right move a target with a preview frame over it, as on YouTube TV; the video
    // jumps there once the presses stop, instead of seeking on every press.
    var target by remember { mutableStateOf<Long?>(null) }
    var presses by remember { mutableIntStateOf(0) }
    LaunchedEffect(presses) {
        val pending = target ?: return@LaunchedEffect
        delay(SCRUB_SETTLE_MS)
        engine.seekTo(pending)
        // Keep the playhead on the target until the player reports the new spot.
        delay(SCRUB_HOLD_MS)
        target = null
    }
    val duration = snapshot.durationMs.coerceAtLeast(1)
    Column(modifier.fillMaxWidth()) {
        val shown = target
        if (shown != null && !snapshot.isLive) {
            ScrubPreview(snapshot, shown, duration)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(24.dp)
                .onFocusChanged { focused = it.isFocused }
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val step = seekStep(event.nativeKeyEvent.repeatCount)
                    when (event.key) {
                        Key.DirectionLeft, Key.DirectionRight -> {
                            val from = target ?: snapshot.positionMs
                            val delta = if (event.key == Key.DirectionLeft) -step else step
                            target = (from + delta).coerceIn(0L, if (snapshot.durationMs > 0) snapshot.durationMs else Long.MAX_VALUE)
                            presses++
                            true
                        }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            // Seeking now also restarts the settle wait, which then finds no target.
                            target?.let { engine.seekTo(it); target = null; presses++ } ?: engine.playOrPause()
                            true
                        }
                        else -> false
                    }
                }
                .focusable(),
            contentAlignment = Alignment.Center,
        ) {
            ProgressBar(snapshot, thick = focused, headMs = target)
        }
    }
}

/** The frame at the scrub target and its time, floating over that spot on the bar. */
@Composable
private fun ScrubPreview(snapshot: PlaybackSnapshot, targetMs: Long, durationMs: Long) {
    val fraction = (targetMs.toFloat() / durationMs).coerceIn(0f, 1f)
    val frame = snapshot.storyboard?.frame(targetMs, durationMs)
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        val width = PREVIEW_WIDTH
        val left = (maxWidth * fraction - width / 2).coerceIn(0.dp, (maxWidth - width).coerceAtLeast(0.dp))
        Column(Modifier.offset(x = left).width(width), horizontalAlignment = Alignment.CenterHorizontally) {
            frame?.let { StoryboardFrameImage(it, width) }
            Text(
                PlayerUi.formatTime(targetMs),
                color = TvColors.Text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

/** One cell of YouTube's preview sheet; the last sheet stays up while the next one loads. */
@Composable
private fun StoryboardFrameImage(frame: com.hhst.youtubelite.extractor.StoryboardFrame, width: androidx.compose.ui.unit.Dp) {
    var sheet by remember { mutableStateOf<Pair<String, android.graphics.Bitmap>?>(null) }
    LaunchedEffect(frame.url) {
        com.hhst.youtubelite.core.YoutubeThumbnail.await(frame.url)?.let { sheet = frame.url to it }
    }
    val height = width * frame.height / frame.width.coerceAtLeast(1)
    val loaded = sheet?.second
    Box(Modifier.size(width, height).clip(RoundedCornerShape(10.dp)).background(Color.Black)) {
        if (loaded != null) {
            val image = remember(loaded) { loaded.asImageBitmap() }
            // The frame from the newest sheet when it's in, else the same cell of the last one.
            Canvas(Modifier.fillMaxSize()) {
                val cellW = image.width / frame.columns.coerceAtLeast(1)
                val cellH = image.height / frame.rows.coerceAtLeast(1)
                drawImage(
                    image,
                    srcOffset = androidx.compose.ui.unit.IntOffset(frame.column * cellW, frame.row * cellH),
                    srcSize = androidx.compose.ui.unit.IntSize(cellW, cellH),
                    dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()),
                )
            }
        }
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

/** Played in white, buffered in grey, split at chapters, with SponsorBlock parts coloured by kind. */
@Composable
private fun ProgressBar(snapshot: PlaybackSnapshot, thick: Boolean = false, headMs: Long? = null) {
    val duration = snapshot.durationMs.coerceAtLeast(1)
    val played = ((headMs ?: snapshot.positionMs).toFloat() / duration).coerceIn(0f, 1f)
    val buffered = (snapshot.bufferedPositionMs.toFloat() / duration).coerceIn(played, 1f)
    val chapterStarts = snapshot.chapters.map { it.startSeconds * 1000f / duration }.filter { it > 0f && it < 1f }
    val sponsors = snapshot.sponsorSegments.map { it to ((it.startMs.toFloat() / duration).coerceIn(0f, 1f) to (it.endMs.toFloat() / duration).coerceIn(0f, 1f)) }
    Canvas(Modifier.fillMaxWidth().height(if (thick) 10.dp else 5.dp)) {
        val h = size.height
        val corner = CornerRadius(h / 2, h / 2)
        drawRoundRect(Color.White.copy(alpha = 0.3f), size = size, cornerRadius = corner)
        drawRoundRect(Color.White.copy(alpha = 0.5f), size = Size(size.width * buffered, h), cornerRadius = corner)
        drawRoundRect(TvColors.Text, size = Size(size.width * played, h), cornerRadius = corner)
        // SponsorBlock parts in their usual colours, over the played part too, as on the phone.
        sponsors.forEach { (segment, range) ->
            val (start, end) = range
            drawRect(PlayerUi.segmentColor(segment.category), topLeft = Offset(size.width * start, 0f), size = Size(size.width * (end - start), h))
        }
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
private fun StatsOverlay(live: () -> PlaybackSnapshot, modifier: Modifier = Modifier) {
    val lines = statsLines(live())
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

private const val WARM_NEXT_MS = 20_000L
private const val CAPTION_STYLE = "caption-style"
private val SETTINGS_CHILDREN = setOf(TvPanel.Quality, TvPanel.Speed, TvPanel.Captions, TvPanel.Audio, TvPanel.Sleep)
private const val NOTICE_MS = 3_000L
/** How far the recommended row sits below: its label and the top of its thumbnails still show. */
private val RELATED_HIDDEN = 150.dp
private const val STATS = "stats"
private const val FADE_IN_MS = 220
private const val SCRUB_SETTLE_MS = 650L
private const val SCRUB_HOLD_MS = 400L
private val PREVIEW_WIDTH = 240.dp
private const val FADE_OUT_MS = 320
private const val DOWNLOAD = "download"
private const val GO_TO_CHANNEL = "channel"
private const val SUBSCRIBE = "subscribe"
private const val FOCUS_TRIES = 60
private const val CONTROLS_HIDE_MS = 5_000L
private const val SEEK_STEP_MS = 10_000L
private const val HIDDEN_SEEK_SETTLE_MS = 350L
private const val UP_NEXT_SECONDS = 8
private const val QUEUE_PREFETCH = 3
