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
    TvSection.Explore -> AppIcon.Explore
    TvSection.Subscriptions -> AppIcon.Subscriptions
    TvSection.Channels -> AppIcon.ChannelGrid
    TvSection.History -> AppIcon.Clock
    TvSection.Playlists -> AppIcon.Playlist
    TvSection.WatchLater -> AppIcon.Bookmark
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
    settings: TvSettingsStore,
    version: String,
    onPlay: (TvCard, List<TvCard>) -> Unit,
    onForgetPhones: () -> Unit,
    onSignOut: () -> Unit,
    onSignIn: () -> Unit,
    onOpenDownloads: () -> Unit,
    onExit: () -> Unit,
) {
    val identity by browse.identity.collectAsStateWithLifecycle()
    val section by browse.section.collectAsStateWithLifecycle()
    val opened by browse.opened.collectAsStateWithLifecycle()
    var sidebarOpen by remember { mutableStateOf(false) }
    val sidebarFocus = remember { FocusRequester() }
    val contentFocus = remember { FocusRequester() }
    val blur by animateDpAsState(if (sidebarOpen) 18.dp else 0.dp, label = "sidebarBlur")

    // Start in the section's tiles, like YouTube TV, once they are on screen.
    LaunchedEffect(identity) { requestFocusSoon(contentFocus) }
    BackHandler {
        when {
            opened.isNotEmpty() -> browse.closeTop()
            !sidebarOpen -> runCatching { sidebarFocus.requestFocus() }
            else -> onExit()
        }
    }

    Box(Modifier.fillMaxSize().background(TvColors.Background)) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(start = SIDEBAR_COLLAPSED)
                .blur(blur)
                .focusRequester(contentFocus),
        ) {
          key(identity) {
            when (val top = opened.lastOrNull()) {
                is TvOpen.Playlist -> key(top) { CardGrid(top.title, top.list, onPlay = onPlay, onOpen = browse::open) }
                is TvOpen.Channel -> key(top) { ChannelScreen(top.model, onPlay = onPlay, onOpen = browse::open) }
                null -> when (section) {
                    TvSection.Search -> SearchSection(browse, onPlay)
                    TvSection.Explore -> ExploreSection(browse, onPlay)
                    TvSection.Downloads -> DownloadsSection(onOpenDownloads)
                    TvSection.Phone -> PhoneSection(link)
                    TvSection.Settings -> SettingsSection(link, account, settings, version, onForgetPhones, onSignOut, onSignIn)
                    else -> {
                        val list = browse.list(section)
                        if (section.needsAccount && !browse.signedIn) {
                            NeedsAccount(section, onSignIn)
                        } else {
                            CardGrid(section.label, list, onPlay = onPlay, onOpen = browse::open, tall = section == TvSection.Shorts)
                        }
                    }
                }
            }
          }
        }
        if (sidebarOpen) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
        }
        val focusManager = LocalFocusManager.current
        val scope = rememberCoroutineScope()
        Sidebar(
            selected = section,
            open = sidebarOpen,
            focus = sidebarFocus,
            // Moving through the sidebar only highlights; pressing picks the section and goes into it.
            onSelect = {
                browse.select(it)
                scope.launch { enterContent(focusManager) }
            },
            modifier = Modifier.onFocusChanged { sidebarOpen = it.hasFocus },
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Sidebar(
    selected: TvSection,
    open: Boolean,
    focus: FocusRequester,
    onSelect: (TvSection) -> Unit,
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
        TvSection.entries.forEach { section ->
            val isSelected = section == selected
            Surface(
                onClick = { onSelect(section) },
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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun CardGrid(
    title: String?,
    model: TvListModel,
    onPlay: (TvCard, List<TvCard>) -> Unit,
    onOpen: (TvCard) -> Unit,
    /** Shorts: six tall tiles a row instead of four wide ones. */
    tall: Boolean = false,
) {
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(model) { model.loadFirst() }
    val columns = if (tall) TALL_COLUMNS else COLUMNS
    Column(Modifier.fillMaxSize()) {
        if (title != null) Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = TvColors.Text,
            modifier = Modifier.padding(start = 40.dp, top = 28.dp, bottom = 8.dp),
        )
        when {
            state.loading -> Message("Loading…")
            state.failed && state.needsAccount -> Message("Sign in from Settings to see this here.")
            state.failed -> RetryMessage("Couldn't load this.", model::reload)
            state.cards.isEmpty() -> Message("Nothing here yet.")
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                contentPadding = PaddingValues(start = 40.dp, end = 48.dp, top = 12.dp, bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
                modifier = Modifier.fillMaxSize().focusRestorer(),
            ) {
                itemsIndexed(state.cards, key = { _, card -> card.key }) { index, card ->
                    val focused = { if (index >= state.cards.size - PREFETCH_ROWS * columns) model.loadMore() }
                    val click = { if (card.videoId != null) onPlay(card, state.cards) else onOpen(card) }
                    if (card.channelId != null && card.videoId == null) ChannelTile(card, click, focused)
                    else VideoTile(card, onClick = click, onFocused = focused, tall = tall)
                }
            }
        }
    }
}

/** A video or playlist tile: thumbnail, duration, watched bar, then title and channel. */
@Composable
internal fun VideoTile(card: TvCard, onClick: () -> Unit, onFocused: () -> Unit = {}, modifier: Modifier = Modifier, tall: Boolean = false) {
    // DeArrow's crowd-sourced title and thumbnail, when those settings are on.
    val title = rememberDeArrowTitle(card.videoId, card.title)
    val artwork = rememberDeArrowArtwork(card.videoId, card.artwork)
    Column(modifier) {
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(if (tall) 9f / 16f else 16f / 9f).onFocusChanged { if (it.isFocused) onFocused() },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF272727), focusedContainerColor = Color(0xFF272727)),
            border = ClickableSurfaceDefaults.border(
                // A clean white ring just outside the thumbnail, never over the picture.
                focusedBorder = Border(BorderStroke(3.dp, TvColors.Focused), inset = 3.dp, shape = RoundedCornerShape(15.dp)),
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        ) {
            Box(Modifier.fillMaxSize()) {
                Artwork(artwork, Modifier.fillMaxSize())
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
private fun ChannelTile(card: TvCard, onClick: () -> Unit, onFocused: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(0.62f).aspectRatio(1f).onFocusChanged { if (it.isFocused) onFocused() },
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF272727), focusedContainerColor = Color(0xFF272727)),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(3.dp, TvColors.Focused), inset = 3.dp, shape = CircleShape),
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        ) {
            Artwork(card.artwork, Modifier.fillMaxSize().clip(CircleShape))
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
private fun SearchSection(browse: TvBrowseModels, onPlay: (TvCard, List<TvCard>) -> Unit) {
    val query by browse.query.collectAsStateWithLifecycle()
    var typed by remember { mutableStateOf(query) }
    val field = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    Column(Modifier.fillMaxSize()) {
        Surface(
            onClick = { runCatching { field.requestFocus() } },
            modifier = Modifier.padding(start = 40.dp, top = 28.dp, end = 48.dp).widthIn(max = 720.dp),
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
                keyboardActions = KeyboardActions(onSearch = {
                    browse.search(typed)
                    focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down)
                }),
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
        if (query.isNotBlank()) {
            CardGrid("Results for \"$query\"", browse.searchResults, onPlay = onPlay, onOpen = browse::open)
        } else {
            Message("Type with the remote, or with your phone's keyboard from the Google TV app.")
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
                "On your phone, open a video in Puretube, tap Cast and pick ${link.tvName}. Then type this code:",
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
        }
        }
    }
}

/** Explore: Music, Gaming, News and Sports as pills over the category's tiles. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ExploreSection(browse: TvBrowseModels, onPlay: (TvCard, List<TvCard>) -> Unit) {
    val category by browse.exploreCategory.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        Text("Explore", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = TvColors.Text,
            modifier = Modifier.padding(start = 40.dp, top = 28.dp))
        Row(
            Modifier.padding(start = 40.dp, top = 14.dp, bottom = 4.dp).focusRestorer(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            EXPLORE_CATEGORIES.forEach { name ->
                FrostPill(name, { browse.selectExplore(name) }, selected = name == category)
            }
        }
        key(category) { CardGrid(null, browse.explore(category), onPlay = onPlay, onOpen = browse::open) }
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
private fun Message(text: String) {
    Text(text, color = TvColors.Muted, style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 40.dp, top = 24.dp, end = 48.dp).widthIn(max = 720.dp))
}

@Composable
private fun RetryMessage(text: String, onRetry: () -> Unit) {
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
private suspend fun enterContent(focusManager: androidx.compose.ui.focus.FocusManager) {
    repeat(ENTER_TRIES) {
        withFrameNanos { }
        if (focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Right)) return
    }
}

private const val ENTER_TRIES = 30
private val SIDEBAR_COLLAPSED = 72.dp
private val SIDEBAR_OPEN = 260.dp
private const val PREFETCH_ROWS = 2
private const val COLUMNS = 4
private const val TALL_COLUMNS = 6
