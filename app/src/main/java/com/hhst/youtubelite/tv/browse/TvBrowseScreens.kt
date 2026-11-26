package com.hhst.youtubelite.tv.browse

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.compose.foundation.shape.CircleShape
import com.hhst.youtubelite.dearrow.rememberDeArrowArtwork
import com.hhst.youtubelite.dearrow.rememberDeArrowTitle
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.launch
import com.hhst.youtubelite.tv.FrostPill
import com.hhst.youtubelite.tv.TvColors
import com.hhst.youtubelite.tv.TvLinkUiState
import com.hhst.youtubelite.tv.requestFocusSoon
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.ui.design.Artwork

private val TvSection.icon: AppIcon get() = when (this) {
    TvSection.Search -> AppIcon.Search
    TvSection.Home -> AppIcon.Home
    TvSection.Shorts -> AppIcon.Shorts
    TvSection.Subscriptions -> AppIcon.Subscriptions
    TvSection.Channels -> AppIcon.ChannelGrid
    TvSection.History -> AppIcon.Clock
    TvSection.Playlists -> AppIcon.Playlist
    TvSection.WatchLater -> AppIcon.Bookmark
    TvSection.Music -> AppIcon.Mix
    TvSection.Gaming -> AppIcon.Sparkle
    TvSection.News -> AppIcon.Globe
    TvSection.Sports -> AppIcon.Trending
    TvSection.Downloads -> AppIcon.Download
    TvSection.Phone -> AppIcon.Device
    TvSection.Settings -> AppIcon.Settings
}

/**
 * The TV's browse screens: a sidebar on the left in SmartTube's order, the section's tiles on
 * the right. Going all the way left opens the sidebar over the blurred, dimmed section.
 */
@Composable
internal fun TvBrowse(
    browse: TvBrowseModels,
    link: TvLinkUiState,
    account: String?,
    accountPhoto: String?,
    settings: TvSettingsStore,
    version: String,
    onPlay: (TvCard, List<TvCard>) -> Unit,
    onPlayNext: (TvCard) -> Unit,
    onForgetPhones: () -> Unit,
    onSignOut: () -> Unit,
    onSignIn: () -> Unit,
    onOpenDownloads: () -> Unit,
    onExit: () -> Unit,
) {
    val identity by browse.identity.collectAsStateWithLifecycle()
    val section by browse.section.collectAsStateWithLifecycle()
    val opened by browse.opened.collectAsStateWithLifecycle()
    // The sidebar is open while it holds focus, except just after a pick: it closes at once,
    // even if the new section has nothing to focus yet.
    var sidebarFocused by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf(false) }
    val sidebarOpen = sidebarFocused && !picked
    // The section now on screen, so focus moves only once the new tiles exist.
    var shown by remember { mutableStateOf<TvSection?>(null) }
    var typeSearch by remember { mutableStateOf(false) }
    val sidebarFocus = remember { FocusRequester() }
    val contentFocus = remember { FocusRequester() }
    val values by settings.values.collectAsStateWithLifecycle()
    val hidden = if (values[com.hhst.youtubelite.extension.PreferenceKeys.ENABLE_HIDE_SHORTS] == true) setOf(TvSection.Shorts) else emptySet()
    val notice by browse.notice.collectAsStateWithLifecycle()
    var menuCard by remember { mutableStateOf<TvCard?>(null) }
    var sectionMenu by remember { mutableStateOf<TvSection?>(null) }
    // Back at the top asks once more before leaving, as on YouTube TV.
    var exitArmedAt by remember { mutableStateOf(0L) }
    var exitHint by remember { mutableStateOf<String?>(null) }

    // Start in the section's tiles, like YouTube TV, once they are on screen.
    LaunchedEffect(identity) { requestFocusSoon(contentFocus) }
    BackHandler {
        when {
            opened.isNotEmpty() -> browse.closeTop()
            !sidebarOpen -> {
                picked = false
                runCatching { sidebarFocus.requestFocus() }
            }
            android.os.SystemClock.elapsedRealtime() - exitArmedAt < EXIT_WINDOW_MS -> onExit()
            else -> {
                exitArmedAt = android.os.SystemClock.elapsedRealtime()
                exitHint = "Press Back again to exit"
            }
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalTileMenu provides { card -> menuCard = card }, LocalTvPlaces provides browse.places) {
    Box(Modifier.fillMaxSize().background(TvColors.Background)) {
        // No real blur behind the open sidebar: it is too heavy for a TV's chip; the dimming does the job.
        Box(
            Modifier
                .fillMaxSize()
                .padding(start = SIDEBAR_COLLAPSED)
                .focusRequester(contentFocus),
        ) {
          key(identity) {
            androidx.compose.runtime.SideEffect { shown = section }
            when (val top = opened.lastOrNull()) {
                is TvOpen.Playlist -> key(top) { CardGrid(top.title, top.list, onPlay = onPlay, onOpen = browse::open, placeKey = "p-${top.id}") }
                is TvOpen.Channel -> key(top) { ChannelScreen(top.model, onPlay = onPlay, onOpen = browse::open) }
                null -> when (section) {
                    TvSection.Search -> SearchSection(browse, onPlay, typeNow = typeSearch, onTyping = { typeSearch = false })
                    TvSection.Home -> HomeSection(browse, onPlay, browse::open, hideShorts = TvSection.Shorts in hidden,
                        dataSaver = values[com.hhst.youtubelite.extension.PreferenceKeys.DATA_SAVER] == true, onSearch = {
                        typeSearch = true
                        browse.select(TvSection.Search)
                    })
                    TvSection.Downloads -> DownloadsSection(onOpenDownloads)
                    TvSection.Channels -> if (browse.signedIn) ChannelsSection(browse) else NeedsAccount(section, onSignIn)
                    TvSection.Phone -> PhoneSection(link)
                    TvSection.Settings -> SettingsSection(link, account, settings, version, onForgetPhones, onSignOut, onSignIn)
                    else -> {
                        val list = browse.list(section)
                        if (section.needsAccount && !browse.signedIn) {
                            NeedsAccount(section, onSignIn)
                        } else {
                            val header: (@Composable () -> Unit)? = if (section == TvSection.Subscriptions) {
                                {
                                    val filter by browse.subscriptionsFilter.collectAsStateWithLifecycle()
                                    ChipRow(TV_SUBSCRIPTION_FILTERS, filter, { it.label() }, browse::filterSubscriptions,
                                        Modifier.padding(start = 40.dp, bottom = 4.dp))
                                }
                            } else null
                            key(list) {
                                CardGrid(section.label, list, onPlay = onPlay, onOpen = browse::open, tall = section == TvSection.Shorts,
                                    placeKey = section.name, header = header)
                            }
                        }
                    }
                }
            }
          }
        }
        if (sidebarOpen) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)))
        }
        val focusManager = LocalFocusManager.current
        val scope = rememberCoroutineScope()
        // Close the sidebar now, wait for the picked section to be on screen, then step into it.
        val pick: (TvSection) -> Unit = { target ->
            picked = true
            browse.select(target)
            scope.launch {
                repeat(ENTER_TRIES) {
                    withFrameNanos { }
                    if (shown == target) {
                        withFrameNanos { }
                        enterContent(focusManager) { !sidebarFocused }
                        return@launch
                    }
                }
            }
        }
        Sidebar(
            selected = section,
            hidden = hidden,
            open = sidebarOpen,
            focus = sidebarFocus,
            account = account,
            accountPhoto = accountPhoto,
            // Signed out, the picture signs in; signed in, it opens the account in Settings.
            onProfile = { if (account == null) onSignIn() else pick(TvSection.Settings) },
            // Moving through the sidebar only highlights; pressing picks the section and goes into it.
            onSelect = pick,
            onHold = { if (it in REFRESHABLE) sectionMenu = it },
            modifier = Modifier
                .onFocusChanged {
                    if (it.hasFocus != sidebarFocused) picked = false
                    // Coming in from the content (Left), land on the section on screen, not
                    // on whatever row was last highlighted.
                    if (it.hasFocus && !sidebarFocused) scope.launch { runCatching { sidebarFocus.requestFocus() } }
                    sidebarFocused = it.hasFocus
                }
                // Still in the closed sidebar after a pick (the section had nothing to focus):
                // up or down opens it again, right tries the section once more.
                .onPreviewKeyEvent { event ->
                    if (!picked || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionRight -> {
                            scope.launch { enterContent(focusManager) { !sidebarFocused } }
                            true
                        }
                        Key.DirectionUp, Key.DirectionDown -> { picked = false; true }
                        else -> false
                    }
                },
        )
        BrowseNotice(notice ?: exitHint, onDone = { browse.clearNotice(); exitHint = null }, Modifier.align(Alignment.TopCenter))
        sectionMenu?.let { held ->
            ActionMenu(held.label, listOf("Refresh"), onPick = {
                sectionMenu = null
                browse.refresh(held)
                pick(held)
                exitHint = "Refreshing ${held.label}…"
            }, onClose = { sectionMenu = null })
        }
        menuCard?.let { card ->
            TileMenu(card, tileActions(card, browse.signedIn), onPick = { action ->
                menuCard = null
                when (action) {
                    TileAction.Play -> if (card.videoId != null) onPlay(card, emptyList()) else browse.open(card)
                    TileAction.PlayNext -> { onPlayNext(card); exitHint = "Plays next" }
                    TileAction.WatchLater -> browse.saveToWatchLater(card)
                    TileAction.Channel -> card.ownerId?.let(browse::openChannel)
                    TileAction.NotInterested -> browse.notInterested(card)
                }
            }, onClose = { menuCard = null })
        }
    }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Sidebar(
    selected: TvSection,
    hidden: Set<TvSection>,
    open: Boolean,
    focus: FocusRequester,
    account: String?,
    accountPhoto: String?,
    onProfile: () -> Unit,
    onSelect: (TvSection) -> Unit,
    onHold: (TvSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val width by animateDpAsState(if (open) SIDEBAR_OPEN else SIDEBAR_COLLAPSED, label = "sidebarWidth")
    val glass = Brush.horizontalGradient(listOf(Color(0xF0161616), Color(0xC0161616)))
    Column(
        modifier = modifier
            .width(width)
            .fillMaxHeight()
            .then(if (open) Modifier.background(glass) else Modifier)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 20.dp, horizontal = 12.dp)
            .focusRestorer(focus),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        // Your picture with Search beside it, so Search isn't a row of the list.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ProfileButton(account, accountPhoto, onProfile)
            if (open || selected == TvSection.Search) {
                RoundSidebarButton(
                    onClick = { onSelect(TvSection.Search) },
                    modifier = if (selected == TvSection.Search) Modifier.focusRequester(focus) else Modifier,
                    highlighted = selected == TvSection.Search && !open,
                ) { AppIcon(AppIcon.Search, Modifier.size(22.dp), color = androidx.tv.material3.LocalContentColor.current) }
            }
        }
        if (open && account != null) {
            Text(account, style = MaterialTheme.typography.bodySmall, color = TvColors.Muted, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp, top = 6.dp))
        }
        Spacer(Modifier.height(10.dp))
        TvSection.entries.filter { it in SIDEBAR_LIST && it !in hidden }.forEach { section ->
            val isSelected = section == selected
            Surface(
                onClick = { onSelect(section) },
                // Holding OK on a section opens its small menu (Refresh).
                onLongClick = { onHold(section) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .then(if (isSelected) Modifier.focusRequester(focus) else Modifier),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(24.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (isSelected && open) TvColors.Frost else Color.Transparent,
                    contentColor = TvColors.Text,
                    focusedContainerColor = TvColors.Focused,
                    focusedContentColor = TvColors.OnFocused,
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            ) {
                Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(
                        section.icon,
                        Modifier.size(24.dp),
                        color = if (isSelected && !open) TvColors.Red else androidx.tv.material3.LocalContentColor.current,
                    )
                    if (open) {
                        Spacer(Modifier.width(16.dp))
                        Text(section.label, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** The top of the sidebar: your picture, or a silhouette when signed out (pressing it signs in). */
@Composable
private fun ProfileButton(account: String?, photo: String?, onClick: () -> Unit) {
    RoundSidebarButton(onClick) {
        if (account != null && !photo.isNullOrBlank()) {
            Artwork(photo, Modifier.fillMaxSize().clip(CircleShape))
        } else {
            AppIcon(AppIcon.You, Modifier.size(24.dp), color = androidx.tv.material3.LocalContentColor.current)
        }
    }
}

/** A round button at the top of the sidebar, ringed in white when focused. */
@Composable
private fun RoundSidebarButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(48.dp),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (highlighted) TvColors.Red else TvColors.Frost,
            contentColor = TvColors.Text,
            focusedContainerColor = TvColors.Focused,
            focusedContentColor = TvColors.OnFocused,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(3.dp, TvColors.Focused), inset = 3.dp, shape = CircleShape),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun CardGrid(
    title: String?,
    model: TvListModel,
    onPlay: (TvCard, List<TvCard>) -> Unit,
    onOpen: (TvCard) -> Unit,
    /** Shorts: six tall tiles a row instead of four wide ones. */
    tall: Boolean = false,
    /** Where this grid's scroll spot and focused tile are kept, to come back to after a video. */
    placeKey: String? = null,
    header: (@Composable () -> Unit)? = null,
) {
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(model) { model.loadFirst() }
    val columns = if (tall) TALL_COLUMNS else COLUMNS
    val places = LocalTvPlaces.current
    val saved = remember(placeKey) { placeKey?.let { places[it] } }
    val grid = rememberLazyGridState(saved?.index ?: 0, saved?.offset ?: 0)
    var focusedKey by remember { mutableStateOf(saved?.focusedKey) }
    androidx.compose.runtime.DisposableEffect(placeKey) {
        onDispose { placeKey?.let { places[it] = TvPlace(grid.firstVisibleItemIndex, grid.firstVisibleItemScrollOffset, focusedKey) } }
    }
    val restore = remember { FocusRequester() }
    val firstTile = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    // Back on a grid you came back to: the tile you left from gets focus again.
    LaunchedEffect(state.cards.isNotEmpty()) {
        val key = saved?.focusedKey ?: return@LaunchedEffect
        if (state.cards.none { it.key == key }) return@LaunchedEffect
        withFrameNanos { }
        requestFocusSoon(restore)
    }
    // Back deep in a grid goes to the top first, as on YouTube TV; Back at the top leaves.
    BackHandler(enabled = grid.firstVisibleItemIndex > 0) {
        scope.launch {
            grid.scrollToItem(0)
            requestFocusSoon(firstTile)
        }
    }
    Column(Modifier.fillMaxSize()) {
        if (title != null) Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = TvColors.Text,
            modifier = Modifier.padding(start = 40.dp, top = 28.dp, bottom = 8.dp),
        )
        header?.invoke()
        when {
            state.loading -> Message("Loading…")
            state.failed && state.needsAccount -> Message("Sign in from Settings to see this here.")
            state.failed -> RetryMessage("Couldn't load this.", model::reload)
            state.cards.isEmpty() -> Message("Nothing here yet.")
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = grid,
                contentPadding = PaddingValues(start = 40.dp, end = 48.dp, top = 12.dp, bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
                modifier = Modifier.fillMaxSize().focusRestorer(),
            ) {
                itemsIndexed(state.cards, key = { _, card -> card.key }) { index, card ->
                    val focused = {
                        focusedKey = card.key
                        if (index >= state.cards.size - PREFETCH_ROWS * columns) model.loadMore()
                    }
                    val click = { if (card.videoId != null) onPlay(card, state.cards) else onOpen(card) }
                    val target = when {
                        card.key == saved?.focusedKey -> Modifier.focusRequester(restore)
                        index == 0 -> Modifier.focusRequester(firstTile)
                        else -> Modifier
                    }
                    if (card.channelId != null && card.videoId == null) ChannelTile(card, click, focused, target)
                    else VideoTile(card, onClick = click, onFocused = focused, modifier = target, tall = tall)
                }
                listFooter(state, model::loadMore)
            }
        }
    }
}

/** The end of a paged grid: "Loading more…", or what failed with Try again. */
internal fun androidx.compose.foundation.lazy.grid.LazyGridScope.listFooter(state: TvListState, onRetry: () -> Unit) {
    if (!state.appending && !state.appendFailed) return
    item(key = "footer", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
        if (state.appendFailed) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Couldn't load more.", color = TvColors.Muted, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(14.dp))
                FrostPill("Try again", onRetry)
            }
        } else {
            Text("Loading more…", color = TvColors.Muted, style = MaterialTheme.typography.titleSmall)
        }
    }
}

/** A video or playlist tile: thumbnail, duration, watched bar, then title and channel. */
@Composable
internal fun VideoTile(card: TvCard, onClick: () -> Unit, onFocused: () -> Unit = {}, modifier: Modifier = Modifier, tall: Boolean = false) {
    val menu = LocalTileMenu.current
    // DeArrow's crowd-sourced title and thumbnail, when those settings are on.
    val title = rememberDeArrowTitle(card.videoId, card.title)
    val artwork = rememberDeArrowArtwork(card.videoId, card.artwork)
    Column(modifier) {
        Surface(
            onClick = onClick,
            // Holding OK opens the tile's menu: Play next, Watch later, Go to channel, Not interested.
            onLongClick = { menu(card) },
            modifier = Modifier.fillMaxWidth().aspectRatio(if (tall) 9f / 16f else 16f / 9f).onFocusChanged {
                if (it.isFocused) {
                    onFocused()
                    com.hhst.youtubelite.tv.TvWarmup.rest(card.videoId)
                }
            },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF272727), focusedContainerColor = Color(0xFF272727)),
            border = ClickableSurfaceDefaults.border(
                // A clean white ring just outside the thumbnail, never over the picture.
                focusedBorder = Border(BorderStroke(3.dp, TvColors.Focused), inset = 3.dp, shape = RoundedCornerShape(15.dp)),
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        ) {
            Box(Modifier.fillMaxSize()) {
                // A TV tile is about 500 px wide; a fixed size skips measuring each one first.
                Artwork(artwork, Modifier.fillMaxSize(), decodePx = TV_TILE_DECODE_PX)
                val badge = if (card.live) "LIVE" else card.duration.takeIf(String::isNotBlank)
                    ?: if (card.playlistId != null && card.videoId == null) "Playlist" else null
                badge?.let {
                    Text(
                        it,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .background(if (card.live) TvColors.Red else Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
                card.progress?.takeIf { it > 0f }?.let { progress ->
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = 0.35f))) {
                        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(TvColors.Red))
                    }
                }
            }
        }
        Text(title, color = TvColors.Text, style = MaterialTheme.typography.titleSmall, maxLines = 2,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
        if (card.subtitle.isNotBlank()) {
            Text(card.subtitle, color = TvColors.Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** A channel: its round picture, with the name and subscriber count under it. */
@Composable
private fun ChannelTile(card: TvCard, onClick: () -> Unit, onFocused: () -> Unit, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(0.62f).aspectRatio(1f).onFocusChanged { if (it.isFocused) onFocused() },
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF272727), focusedContainerColor = Color(0xFF272727)),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(3.dp, TvColors.Focused), inset = 3.dp, shape = CircleShape),
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        ) {
            Artwork(card.artwork, Modifier.fillMaxSize().clip(CircleShape))
        }
        if (card.fresh) {
            // A small blue dot, as YouTube marks channels with new videos.
            Box(Modifier.padding(top = 6.dp).size(8.dp).background(androidx.compose.ui.graphics.Color(0xFF3EA6FF), CircleShape))
        }
        Text(card.title, color = TvColors.Text, style = MaterialTheme.typography.titleSmall, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
        if (card.subtitle.isNotBlank()) {
            Text(card.subtitle, color = TvColors.Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** The recommended row under the player, reached by pressing down. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun RelatedRow(model: TvListModel, onPlay: (TvCard, List<TvCard>) -> Unit, modifier: Modifier = Modifier) {
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(model) { model.loadFirst() }
    if (state.cards.isEmpty()) return
    LazyRow(
        modifier = modifier.fillMaxWidth().focusRestorer(),
        contentPadding = PaddingValues(horizontal = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        itemsIndexed(state.cards, key = { _, card -> card.key }) { _, card ->
            VideoTile(card, onClick = { onPlay(card, emptyList()) }, modifier = Modifier.width(300.dp))
        }
    }
}

@Composable
private fun SearchSection(browse: TvBrowseModels, onPlay: (TvCard, List<TvCard>) -> Unit, typeNow: Boolean = false, onTyping: () -> Unit = {}) {
    val query by browse.query.collectAsStateWithLifecycle()
    var typed by remember { mutableStateOf(query) }
    val field = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    // From Home's search bar: straight into the field, keyboard up.
    LaunchedEffect(typeNow) {
        if (typeNow) {
            requestFocusSoon(field)
            onTyping()
        }
    }
    val filters by browse.searchFilters.collectAsStateWithLifecycle()
    var rows by remember { mutableStateOf(com.hhst.youtubelite.data.media.SearchRowSet(emptyList(), emptyList())) }
    // Recent searches and YouTube's suggestions for what's typed, a moment after typing stops.
    LaunchedEffect(typed) {
        kotlinx.coroutines.delay(if (typed.isBlank()) 0L else SUGGEST_DELAY_MS)
        rows = runCatching { browse.searchRows(typed) }.getOrNull() ?: rows
    }
    val runSearch: (String) -> Unit = { text ->
        typed = text
        browse.search(text)
        focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down)
    }
    Column(Modifier.fillMaxSize()) {
      Row(Modifier.padding(start = 40.dp, top = 28.dp, end = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            onClick = { runCatching { field.requestFocus() } },
            modifier = Modifier.widthIn(max = 720.dp).weight(1f, fill = false),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(28.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = TvColors.Frost, focusedContainerColor = TvColors.Frost),
            border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, TvColors.Focused), shape = RoundedCornerShape(28.dp))),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
        ) {
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                placeholder = { androidx.compose.material3.Text("Search YouTube", color = TvColors.Muted) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { runSearch(typed) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TvColors.Text, unfocusedTextColor = TvColors.Text,
                    focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
                    cursorColor = TvColors.Red,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(field)
                    .onPreviewKeyEvent { event ->
                        // Back leaves the field instead of leaving the screen.
                        if (event.type == KeyEventType.KeyUp && (event.key == Key.Back || event.key == Key.Escape)) {
                            focusManager.clearFocus()
                            true
                        } else false
                    },
            )
        }
        Spacer(Modifier.width(12.dp))
        VoiceButton(runSearch)
      }
        SearchFilterPills(filters, browse::filterSearch, Modifier.padding(start = 40.dp, top = 14.dp))
        val showRows = query.isBlank() || typed.trim() != query
        if (showRows && (rows.history.isNotEmpty() || rows.suggestions.isNotEmpty())) {
            SearchRows(rows.history, rows.suggestions, runSearch, Modifier.padding(start = 40.dp, top = 18.dp, end = 48.dp))
        } else if (query.isNotBlank()) {
            key(browse.searchResults) {
                CardGrid("Results for \"$query\"", browse.searchResults, onPlay = onPlay, onOpen = browse::open)
            }
        } else {
            Message("Type with the remote, press the mic, or use your phone's keyboard from the Google TV app.")
        }
    }
}

@Composable
private fun PhoneSection(link: TvLinkUiState) {
    Column(Modifier.fillMaxSize().padding(start = 40.dp, top = 28.dp, end = 48.dp)) {
        Text("Phone", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = TvColors.Text)
        Spacer(Modifier.height(16.dp))
        link.connectedPhone?.let {
            Text("Connected to $it.", style = MaterialTheme.typography.titleLarge, color = TvColors.Text)
            Spacer(Modifier.height(20.dp))
        }
        // A focusable card, so the remote can step into this section and back out of it.
        Surface(
            onClick = {},
            modifier = Modifier.widthIn(max = 640.dp),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = TvColors.Frost, focusedContainerColor = TvColors.Frost,
                contentColor = TvColors.Text, focusedContentColor = TvColors.Text),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(2.dp, TvColors.Focused), shape = RoundedCornerShape(20.dp)),
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        ) {
        Column(Modifier.padding(horizontal = 32.dp, vertical = 24.dp)) {
            Text("Pair a phone", style = MaterialTheme.typography.titleLarge, color = TvColors.Text)
            Spacer(Modifier.height(8.dp))
            Text(
                "On your phone, open a video in Puretube and tap Cast. Pick ${link.tvName} with the red Puretube tag, " +
                    "not the plain one (that's the TV's Chromecast). Then type this code:",
                style = MaterialTheme.typography.bodyLarge,
                color = TvColors.Muted,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                if (link.listening) link.code.chunked(3).joinToString(" ") else "Starting…",
                fontSize = 56.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = TvColors.Text,
            )
            if (link.address.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Not in the list? Tap \"Puretube TV not listed?\" under the devices and type ${link.address}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TvColors.Muted,
                )
            }
        }
        }
    }
}

/**
 * Your subscriptions: a name filter and A–Z, Recently active or New videos, over round
 * channel pictures. Every page is read so the order and filter cover all of them.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ChannelsSection(browse: TvBrowseModels) {
    val model = browse.list(TvSection.Channels)
    val state by model.state.collectAsStateWithLifecycle()
    val sort by browse.channelSort.collectAsStateWithLifecycle()
    val filter by browse.channelFilter.collectAsStateWithLifecycle()
    LaunchedEffect(model) { model.loadFirst() }
    // Keep reading pages until the list ends, so A–Z and the filter see every channel.
    LaunchedEffect(state.cards.size, state.done, state.appending, state.appendFailed) {
        if (!state.done && !state.appending && !state.appendFailed && state.cards.isNotEmpty()) model.loadMore()
    }
    val shown = remember(state.cards, sort, filter) { arrangeChannels(state.cards, sort, filter) }
    val focusManager = LocalFocusManager.current
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 40.dp, top = 28.dp, end = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Channels", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = TvColors.Text)
            if (state.cards.isNotEmpty()) {
                Text("  ${state.cards.size}${if (state.done) "" else "+"}", style = MaterialTheme.typography.titleMedium, color = TvColors.Muted)
            }
        }
        Row(
            Modifier.padding(start = 40.dp, top = 14.dp, end = 48.dp, bottom = 6.dp).focusRestorer(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = filter,
                onValueChange = browse::filterChannels,
                placeholder = { androidx.compose.material3.Text("Find a channel", color = TvColors.Muted) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TvColors.Text, unfocusedTextColor = TvColors.Text,
                    focusedBorderColor = TvColors.Focused, unfocusedBorderColor = TvColors.Frost,
                    cursorColor = TvColors.Red,
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.width(300.dp).onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyUp && (event.key == Key.Back || event.key == Key.Escape)) {
                        focusManager.clearFocus()
                        true
                    } else false
                },
            )
            Spacer(Modifier.width(6.dp))
            TvChannelSort.entries.forEach { option ->
                FrostPill(option.label, { browse.sortChannels(option) }, selected = option == sort)
            }
        }
        when {
            state.loading -> Message("Loading…")
            state.failed -> RetryMessage("Couldn't load your channels.", model::reload)
            shown.isEmpty() && filter.isNotBlank() -> Message("No channel matches \"$filter\".")
            shown.isEmpty() && sort == TvChannelSort.New -> Message("No new videos from your channels.")
            shown.isEmpty() -> Message("You don't subscribe to any channels yet.")
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(CHANNEL_COLUMNS),
                contentPadding = PaddingValues(start = 40.dp, end = 48.dp, top = 16.dp, bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.fillMaxSize().focusRestorer(),
            ) {
                itemsIndexed(shown, key = { _, card -> card.key }) { _, card ->
                    ChannelTile(card, { browse.open(card) }, {})
                }
                // A page that failed stops the read-all loop: say so, and let it try again.
                listFooter(state, model::loadMore)
            }
        }
    }
}

/** Downloads keep to the phone's download manager, which works with the remote too. */
@Composable
private fun DownloadsSection(onOpen: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(start = 40.dp, top = 28.dp, end = 48.dp)) {
        Text("Downloads", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = TvColors.Text)
        Message("Save videos to this TV from the player's Settings, then watch them here.")
        Spacer(Modifier.height(16.dp))
        FrostPill("Open downloads", onOpen, modifier = Modifier.padding(start = 40.dp))
    }
}

@Composable
private fun NeedsAccount(section: TvSection, onSignIn: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(start = 40.dp, top = 28.dp)) {
        Text(section.label, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = TvColors.Text)
        Message("Sign in to see your ${section.label.lowercase()} here.")
        Spacer(Modifier.height(16.dp))
        Button(onClick = onSignIn, modifier = Modifier.padding(start = 40.dp)) { Text("Sign in") }
    }
}

@Composable
internal fun Message(text: String) {
    Text(text, color = TvColors.Muted, style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 40.dp, top = 24.dp, end = 48.dp).widthIn(max = 720.dp))
}

@Composable
internal fun RetryMessage(text: String, onRetry: () -> Unit) {
    Column(Modifier.padding(start = 40.dp, top = 24.dp)) {
        Text(text, color = TvColors.Muted, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry) { Text("Try again") }
    }
}

/**
 * After a section is picked its tiles compose a frame or two later: keep stepping right
 * until focus leaves the sidebar, or give up and stay on it.
 */
private suspend fun enterContent(focusManager: androidx.compose.ui.focus.FocusManager, entered: () -> Boolean) {
    repeat(ENTER_TRIES) {
        if (entered()) return
        focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Right)
        withFrameNanos { }
    }
}

private const val ENTER_TRIES = 300
private const val EXIT_WINDOW_MS = 2_500L
/** Sections with a list to read again; the rest have nothing to refresh. */
private val REFRESHABLE = TvSection.entries.toSet() - setOf(TvSection.Search, TvSection.Downloads, TvSection.Phone, TvSection.Settings)
private const val SUGGEST_DELAY_MS = 300L
/** Search lives beside your picture, so the list starts at Home. */
private val SIDEBAR_LIST = TvSection.entries.toSet() - TvSection.Search
private val SIDEBAR_COLLAPSED = 72.dp
private val SIDEBAR_OPEN = 260.dp
private const val PREFETCH_ROWS = 2
private const val COLUMNS = 4
private const val TALL_COLUMNS = 6
private const val CHANNEL_COLUMNS = 6

private const val TV_TILE_DECODE_PX = 640
