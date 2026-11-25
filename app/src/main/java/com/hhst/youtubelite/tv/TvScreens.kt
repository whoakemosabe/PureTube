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
import com.hhst.youtubelite.tv.browse.TvPage
import com.hhst.youtubelite.tv.browse.RepositoryTvBrowseSource
import com.hhst.youtubelite.tv.browse.TvSettingsStore
import com.hhst.youtubelite.extension.PreferenceKeys
import com.hhst.youtubelite.watch.chat.LiveChatUiState
import com.hhst.youtubelite.watch.chat.LiveChatViewModel
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay

@Composable
internal fun TvApp(receiver: TvReceiver, engine: PlaybackApi, browse: TvBrowseModels, player: TvPlayerSession,
    account: String?, settings: TvSettingsStore, version: String, chat: LiveChatViewModel,
    onSignOut: () -> Unit, openSignIn: () -> TvSignInSession, onOpenDownloads: () -> Unit,
    onDownload: (videoId: String, title: String, author: String?, artwork: String?) -> Unit, onExit: () -> Unit) {
    var signingIn by remember { mutableStateOf(false) }
    val snapshot by receiver.snapshot.collectAsStateWithLifecycle()
    val link by receiver.server.state.collectAsStateWithLifecycle()
    val nowPlaying by receiver.nowPlaying.collectAsStateWithLifecycle()
    val values by settings.values.collectAsStateWithLifecycle()
    /** The list the current video was started from, when it plays in order (a playlist, or Shorts). */
    var queue by remember { mutableStateOf<List<TvCard>>(emptyList()) }
    val play: (TvCard, List<TvCard>) -> Unit = { card, list ->
        queue = playQueue(card, list)
        card.videoId?.let { receiver.playLocal(it, card.title, card.author) }
    }
    Box(Modifier.fillMaxSize().background(TvColors.Background)) {
        val videoId = snapshot.videoId
        if (videoId != null) {
            val hasPhone = link.connectedPhone != null
            val related = if (values[PreferenceKeys.ENABLE_HIDE_RELATED] == true) NO_RELATED else browse.related(videoId)
            val following: (Boolean) -> TvCard? = { forward ->
                val index = queue.indexOfFirst { it.videoId == videoId }
                queue.getOrNull(if (forward) index + 1 else index - 1).takeIf { index >= 0 }
            }
            LaunchedEffect(videoId) { player.video.load(videoId) }
            // Watching on the TV alone, the next video follows, like YouTube's autoplay: the next
            // one in the playlist or Shorts it came from, else the top recommendation.
            LaunchedEffect(videoId, snapshot.ended, hasPhone) {
                if (snapshot.ended && !hasPhone && player.sleptOn != videoId && values[PreferenceKeys.ENABLE_AUTOPLAY] != false) {
                    following(true)?.let { play(it, queue) }
                        ?: related.state.value.cards.firstOrNull { it.videoId != null && it.videoId != videoId }?.let { play(it, emptyList()) }
                }
            }
            PlayerScreen(
                snapshot, nowPlaying, engine, player, chat,
                canSkip = hasPhone || queue.isNotEmpty(),
                related = related,
                onSkip = { forward ->
                    if (hasPhone) receiver.skip(forward) else following(forward)?.let { play(it, queue) }
                },
                onPlayCard = play,
                onOpenChannel = { channelId ->
                    receiver.stopPlayback()
                    browse.openChannel(channelId)
                },
                onDownload = onDownload,
                onStop = receiver::stopPlayback,
            )
        } else if (signingIn) {
            TvSignInScreen(openSignIn, onClose = { signingIn = false })
        } else {
            TvBrowse(browse, link, account, settings, version, onPlay = play, onForgetPhones = receiver.server::forgetPhones,
                onSignOut = onSignOut, onSignIn = { signingIn = true }, onOpenDownloads = onOpenDownloads, onExit = onExit)
            if (tvUpdateWaiting()) TvUpdatePanel()
        }
    }
}

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
    nowPlaying: TvNowPlaying?,
    engine: PlaybackApi,
    player: TvPlayerSession,
    chat: LiveChatViewModel,
    canSkip: Boolean,
    related: TvListModel,
    onSkip: (Boolean) -> Unit,
    onPlayCard: (TvCard, List<TvCard>) -> Unit,
    onOpenChannel: (String) -> Unit,
    onDownload: (videoId: String, title: String, author: String?, artwork: String?) -> Unit,
    onStop: () -> Unit,
) {
    val video by player.video.state.collectAsStateWithLifecycle()
    val chatState by chat.uiState.collectAsStateWithLifecycle()
    val sleep by player.sleep.collectAsStateWithLifecycle()
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
            panel == TvPanel.Comments && player.video.closeReplies() -> {}
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
        panel?.let { open ->
            PlayerPanel(open, snapshot, video, sleep, engine, player, statsVisible, settingsReturn, chatState,
                onStats = { statsVisible = !statsVisible; openPanel(null) }, onPanel = openPanel,
                onOpenChannel = onOpenChannel,
                onDownload = {
                    val id = video.videoId ?: snapshot.videoId
                    if (id != null) onDownload(id, page?.title ?: snapshot.title, page?.channel ?: snapshot.author, page?.artwork)
                    openPanel(null)
                })
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
        TvPanel.Comments -> TvSidePanel(listOf("Comments", video.comments.total).filter(String::isNotBlank).joinToString(" • ")) {
            LaunchedEffect(video.videoId) { if (!video.comments.started) player.video.loadComments() }
            TvCommentList(video.comments, onNearEnd = player.video::loadComments,
                onReplies = player.video::loadReplies,
                onMoreReplies = { video.comments.replyThread?.let(player.video::loadReplies) })
        }
        TvPanel.Chat -> TvSidePanel("Live chat") { TvChatList(chat) }
        TvPanel.Channel -> TvSidePanel(video.video?.channel.orEmpty()) {
            val channelId = video.video?.channelId.orEmpty()
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
                    else -> onPanel(TvPanel.valueOf(row.key))
                }
            }
        }
        TvPanel.Quality -> TvSidePanel("Quality") { TvPanelList(qualityRows(snapshot), onPick = pick) }
        TvPanel.Speed -> TvSidePanel("Playback speed") { TvPanelList(speedRows(snapshot), onPick = pick) }
        TvPanel.Captions -> TvSidePanel("Captions") { TvPanelList(captionRows(snapshot), onPick = pick) }
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
                .padding(start = 58.dp, end = 58.dp, top = 40.dp, bottom = 48.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
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
                    FrostGroup {
                        FrostButton(AppIcon.Like, "Like", onLike, size = 44.dp, glass = false, active = video.rating == VideoRating.Like)
                        FrostButton(AppIcon.Dislike, "Dislike", onDislike, size = 44.dp, glass = false, active = video.rating == VideoRating.Dislike)
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
/** How far the recommended row sits below: its label and the top of its thumbnails still show. */
private val RELATED_HIDDEN = 150.dp
private const val STATS = "stats"
private const val DOWNLOAD = "download"
private const val GO_TO_CHANNEL = "channel"
private const val SUBSCRIBE = "subscribe"
private const val FOCUS_TRIES = 60
private const val CONTROLS_HIDE_MS = 5_000L
private const val SEEK_STEP_MS = 10_000L
