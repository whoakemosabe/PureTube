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
import com.hhst.youtubelite.tv.TvColors
import com.hhst.youtubelite.tv.TvLinkUiState
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.ui.design.Artwork

private val TvSection.icon: AppIcon get() = when (this) {
    TvSection.Search -> AppIcon.Search
    TvSection.Home -> AppIcon.Home
    TvSection.Subscriptions -> AppIcon.Subscriptions
    TvSection.History -> AppIcon.Clock
    TvSection.Playlists -> AppIcon.Playlist
    TvSection.WatchLater -> AppIcon.Bookmark
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
    onPlay: (TvCard) -> Unit,
    onForgetPhones: () -> Unit,
    onSignOut: () -> Unit,
    onExit: () -> Unit,
) {
    val identity by browse.identity.collectAsStateWithLifecycle()
    val section by browse.section.collectAsStateWithLifecycle()
    val openPlaylist by browse.openPlaylist.collectAsStateWithLifecycle()
    var sidebarOpen by remember { mutableStateOf(false) }
    val sidebarFocus = remember { FocusRequester() }
    val contentFocus = remember { FocusRequester() }
    val blur by animateDpAsState(if (sidebarOpen) 18.dp else 0.dp, label = "sidebarBlur")

    BackHandler {
        when {
            openPlaylist != null -> browse.closePlaylist()
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
            val playlist = openPlaylist
            if (playlist != null) {
                CardGrid(playlist.title, playlist.list, onPlay = onPlay, onOpen = browse::openPlaylist)
            } else when (section) {
                TvSection.Search -> SearchSection(browse, onPlay)
                TvSection.Phone -> PhoneSection(link)
                TvSection.Settings -> SettingsSection(link, account, onForgetPhones, onSignOut)
                else -> {
                    val list = browse.list(section)
                    if (section.needsAccount && !browse.signedIn) {
                        NeedsAccount(section)
                    } else {
                        CardGrid(section.label, list, onPlay = onPlay, onOpen = browse::openPlaylist)
                    }
                }
            }
          }
        }
        if (sidebarOpen) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
        }
        Sidebar(
            selected = section,
            open = sidebarOpen,
            focus = sidebarFocus,
            onSelect = { browse.select(it) },
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
            .padding(vertical = 32.dp, horizontal = 12.dp)
            .focusRestorer(focus),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        TvSection.entries.forEach { section ->
            val isSelected = section == selected
            Surface(
                onClick = { onSelect(section) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .then(if (isSelected) Modifier.focusRequester(focus) else Modifier)
                    .onFocusChanged { if (it.isFocused) onSelect(section) },
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
internal fun CardGrid(title: String, model: TvListModel, onPlay: (TvCard) -> Unit, onOpen: (TvCard) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(model) { model.loadFirst() }
    Column(Modifier.fillMaxSize()) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = TvColors.Text,
            modifier = Modifier.padding(start = 40.dp, top = 28.dp, bottom = 8.dp),
        )
        when {
            state.loading -> Message("Loading…")
            state.failed && state.needsAccount -> Message("Sign this TV in from your phone's Cast menu to see this here.")
            state.failed -> RetryMessage("Couldn't load this.", model::reload)
            state.cards.isEmpty() -> Message("Nothing here yet.")
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                contentPadding = PaddingValues(start = 40.dp, end = 48.dp, top = 12.dp, bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
                modifier = Modifier.fillMaxSize().focusRestorer(),
            ) {
                itemsIndexed(state.cards, key = { _, card -> card.key }) { index, card ->
                    VideoTile(card, onClick = { if (card.videoId != null) onPlay(card) else onOpen(card) },
                        onFocused = { if (index >= state.cards.size - PREFETCH_ROWS * 4) model.loadMore() })
                }
            }
        }
    }
}

/** A video or playlist tile: thumbnail, duration, watched bar, then title and channel. */
@Composable
internal fun VideoTile(card: TvCard, onClick: () -> Unit, onFocused: () -> Unit = {}, modifier: Modifier = Modifier) {
    Column(modifier) {
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).onFocusChanged { if (it.isFocused) onFocused() },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF272727), focusedContainerColor = Color(0xFF272727)),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(3.dp, TvColors.Focused), shape = RoundedCornerShape(12.dp)),
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        ) {
            Box(Modifier.fillMaxSize()) {
                Artwork(card.artwork, Modifier.fillMaxSize())
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
        Text(card.title, color = TvColors.Text, style = MaterialTheme.typography.titleSmall, maxLines = 2,
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
internal fun RelatedRow(model: TvListModel, onPlay: (TvCard) -> Unit, modifier: Modifier = Modifier) {
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(model) { model.loadFirst() }
    if (state.cards.isEmpty()) return
    LazyRow(
        modifier = modifier.fillMaxWidth().focusRestorer(),
        contentPadding = PaddingValues(horizontal = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        itemsIndexed(state.cards, key = { _, card -> card.key }) { _, card ->
            VideoTile(card, onClick = { onPlay(card) }, modifier = Modifier.width(300.dp))
        }
    }
}

@Composable
private fun SearchSection(browse: TvBrowseModels, onPlay: (TvCard) -> Unit) {
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
            CardGrid("Results for \"$query\"", browse.searchResults, onPlay = onPlay, onOpen = browse::openPlaylist)
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
        Column(
            Modifier
                .widthIn(max = 640.dp)
                .background(TvColors.Frost, RoundedCornerShape(20.dp))
                .padding(horizontal = 32.dp, vertical = 24.dp),
        ) {
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

@Composable
private fun SettingsSection(link: TvLinkUiState, account: String?, onForgetPhones: () -> Unit, onSignOut: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(start = 40.dp, top = 28.dp, end = 48.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = TvColors.Text)
        Spacer(Modifier.height(16.dp))
        if (account != null) {
            Text("Signed in as $account", style = MaterialTheme.typography.titleMedium, color = TvColors.Text)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onSignOut) { Text("Sign out on this TV") }
        } else {
            Text(
                "Not signed in. On your phone, connect to this TV from the Cast menu and tap Use my account on the TV.",
                style = MaterialTheme.typography.bodyLarge,
                color = TvColors.Muted,
                modifier = Modifier.widthIn(max = 720.dp),
            )
        }
        Spacer(Modifier.height(28.dp))
        Text("This TV: ${link.tvName}", style = MaterialTheme.typography.titleMedium, color = TvColors.Text)
        Spacer(Modifier.height(12.dp))
        Text(
            if (link.pairedPhones.isEmpty()) "No paired phones." else "Paired phones: " + link.pairedPhones.joinToString(", "),
            style = MaterialTheme.typography.bodyLarge,
            color = TvColors.Muted,
        )
        if (link.pairedPhones.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Button(onClick = onForgetPhones) { Text("Forget paired phones") }
        }
    }
}

@Composable
private fun NeedsAccount(section: TvSection) {
    Column(Modifier.fillMaxSize().padding(start = 40.dp, top = 28.dp)) {
        Text(section.label, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = TvColors.Text)
        Message("On your phone, connect to this TV from the Cast menu and tap Use my account on the TV.")
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

private val SIDEBAR_COLLAPSED = 72.dp
private val SIDEBAR_OPEN = 260.dp
private const val PREFETCH_ROWS = 2
