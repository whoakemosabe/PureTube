package com.hhst.youtubelite.tv.browse

import androidx.compose.animation.Crossfade
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.hhst.youtubelite.tv.requestFocusSoon
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import com.hhst.youtubelite.ui.design.AppIcon
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.hhst.youtubelite.dearrow.rememberDeArrowTitle
import com.hhst.youtubelite.tv.TvColors
import com.hhst.youtubelite.ui.design.Artwork

/** One shelf on Home: which list it reads and how its tiles look. */
internal data class TvHomeShelf(val title: String, val section: TvSection, val tall: Boolean = false)

/** Home's shelves, top to bottom, as YouTube on TV orders them. Account shelves drop when signed out. */
internal fun homeShelves(signedIn: Boolean, hideShorts: Boolean = false): List<TvHomeShelf> = listOfNotNull(
    TvHomeShelf("Recommended", TvSection.Home),
    TvHomeShelf("Continue watching", TvSection.History).takeIf { signedIn },
    TvHomeShelf("Shorts", TvSection.Shorts, tall = true).takeIf { !hideShorts },
    TvHomeShelf("Latest from your subscriptions", TvSection.Subscriptions).takeIf { signedIn },
    TvHomeShelf("Watch later", TvSection.WatchLater).takeIf { signedIn },
    TvHomeShelf("Music", TvSection.Music),
    TvHomeShelf("Gaming", TvSection.Gaming),
    TvHomeShelf("News", TvSection.News),
    TvHomeShelf("Sports", TvSection.Sports),
)

/**
 * YouTube's TV home: the focused video large at the top (its picture behind the title),
 * and shelves of tiles under it, one per kind of list. A shelf that is empty or fails hides.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun HomeSection(
    browse: TvBrowseModels,
    onPlay: (TvCard, List<TvCard>) -> Unit,
    onOpen: (TvCard) -> Unit,
    onSearch: () -> Unit,
    hideShorts: Boolean = false,
    /** Data saver: the big picture reuses the tile's thumbnail instead of a 1280 px one. */
    dataSaver: Boolean = false,
    /** Goes up each time Home is picked in the sidebar: focus goes into Recommended, not the search bar. */
    enter: Int = 0,
) {
    val shelves = remember(browse.signedIn, hideShorts) { homeShelves(browse.signedIn, hideShorts) }
    val recommended = browse.list(TvSection.Home)
    val first by recommended.state.collectAsStateWithLifecycle()
    var focused by remember { mutableStateOf<TvCard?>(null) }
    // The big picture follows focus only once it rests, so holding the D-pad doesn't load one per tile.
    var hero by remember { mutableStateOf<TvCard?>(null) }
    LaunchedEffect(focused) {
        if (focused != null) delay(HERO_SETTLE_MS)
        hero = focused
    }
    val shownHero = hero ?: first.cards.firstOrNull { it.videoId != null }
    val places = LocalTvPlaces.current
    val saved = remember { places[HOME_PLACE] }
    val column = rememberLazyListState(saved?.index ?: 0, saved?.offset ?: 0)
    val lastFocus = remember { mutableStateOf(saved?.focusedKey) }
    DisposableEffect(Unit) {
        onDispose { places[HOME_PLACE] = TvPlace(column.firstVisibleItemIndex, column.firstVisibleItemScrollOffset, lastFocus.value) }
    }
    val scope = rememberCoroutineScope()
    val firstTile = remember { FocusRequester() }
    val recommendedRow = remember { FocusRequester() }
    LaunchedEffect(enter) {
        if (enter == 0) return@LaunchedEffect
        column.scrollToItem(0)
        withFrameNanos { }
        // The row brings back the tile last focused in it, else its first.
        requestFocusSoon(recommendedRow)
    }
    // Home starts on the first video, as YouTube TV does, not on the search bar.
    LaunchedEffect(first.cards.isNotEmpty()) {
        if (first.cards.isNotEmpty() && saved?.focusedKey == null) {
            withFrameNanos { }
            requestFocusSoon(firstTile)
        }
    }
    BackHandler(enabled = column.firstVisibleItemIndex > 0) {
        scope.launch {
            column.scrollToItem(0)
            requestFocusSoon(firstTile)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Hero(shownHero, dataSaver)
        Column(Modifier.fillMaxSize()) {
            SearchBar(onSearch)
            HeroText(shownHero, Modifier.height(HERO_HEIGHT))
            when {
                first.loading && first.cards.isEmpty() -> Message("Loading…")
                first.failed && first.cards.isEmpty() -> RetryMessage("Couldn't load Home.", recommended::reload)
                else -> LazyColumn(
                    state = column,
                    modifier = Modifier.fillMaxSize().focusRestorer(),
                    contentPadding = PaddingValues(bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    shelves.forEach { shelf ->
                        item(key = shelf.section.name) {
                            Shelf(shelf, browse.list(shelf.section), onPlay, onOpen,
                                // Remembered with its row: the same video can sit in two rows.
                                onFocused = { focused = it; lastFocus.value = shelf.section.name + "|" + it.key },
                                restoreKey = saved?.focusedKey?.takeIf { it.startsWith(shelf.section.name + "|") }?.substringAfter('|'),
                                firstTile = firstTile.takeIf { shelf.section == TvSection.Home },
                                rowFocus = recommendedRow.takeIf { shelf.section == TvSection.Home },
                                // Recommended keeps YouTube's picks for you, without its general shelves.
                                select = if (shelf.section == TvSection.Home) ::personalCards else { cards -> cards })
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(recommended) { recommended.loadFirst() }
}

/** Home's search bar: pressing it opens Search with the keyboard up. */
@Composable
private fun SearchBar(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.padding(start = 40.dp, top = 22.dp).width(420.dp).height(46.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(23.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Black.copy(alpha = 0.45f),
            contentColor = TvColors.Muted,
            focusedContainerColor = TvColors.Focused,
            focusedContentColor = TvColors.OnFocused,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, TvColors.Frost), shape = RoundedCornerShape(23.dp)),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(AppIcon.Search, Modifier.size(22.dp), color = androidx.tv.material3.LocalContentColor.current)
            Spacer(Modifier.width(12.dp))
            Text("Search YouTube", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** The focused tile's picture, filling the top right and fading into the page. */
@Composable
private fun Hero(card: TvCard?, dataSaver: Boolean) {
    Box(Modifier.fillMaxWidth().height(HERO_HEIGHT + 150.dp)) {
        Crossfade(card?.key, animationSpec = tween(HERO_FADE_MS), label = "hero") { key ->
            val shown = card?.takeIf { it.key == key } ?: return@Crossfade
            Box(Modifier.fillMaxSize()) {
                val big = shown.videoId?.takeUnless { shown.short || dataSaver }?.let { "https://i.ytimg.com/vi/$it/maxresdefault.jpg" }
                Artwork(big ?: shown.artwork, Modifier.align(Alignment.TopEnd).fillMaxWidth(0.68f).fillMaxHeight(),
                    fallback = shown.artwork.takeIf { big != null })
            }
        }
        // Dark from the left and from below, so the title reads and the shelves sit on black.
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
            0f to TvColors.Background, 0.38f to TvColors.Background, 0.62f to TvColors.Background.copy(alpha = 0.55f), 1f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to Color.Transparent, 0.6f to Color.Transparent, 1f to TvColors.Background)))
    }
}

@Composable
private fun HeroText(card: TvCard?, modifier: Modifier) {
    Column(modifier.fillMaxWidth().padding(start = 40.dp, top = 18.dp, end = 48.dp), verticalArrangement = Arrangement.Top) {
        if (card == null) return@Column
        val title = rememberDeArrowTitle(card.videoId, card.title)
        Row(verticalAlignment = Alignment.CenterVertically) {
            val tag = when {
                card.live -> "LIVE"
                card.short -> "SHORTS"
                card.playlistId != null && card.videoId == null -> "PLAYLIST"
                else -> null
            }
            tag?.let {
                Text(it, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    modifier = Modifier.background(if (card.live || card.short) TvColors.Red else TvColors.Frost, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = TvColors.Text,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp).widthIn(max = 560.dp))
        val details = listOf(card.subtitle, card.duration).filter(String::isNotBlank).joinToString(" • ")
        if (details.isNotBlank()) {
            Text(details, style = MaterialTheme.typography.titleSmall, color = TvColors.Muted, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp).widthIn(max = 560.dp))
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Shelf(
    shelf: TvHomeShelf,
    model: TvListModel,
    onPlay: (TvCard, List<TvCard>) -> Unit,
    onOpen: (TvCard) -> Unit,
    onFocused: (TvCard) -> Unit,
    restoreKey: String? = null,
    firstTile: FocusRequester? = null,
    /** Which of the list's tiles this shelf shows (one YouTube shelf of Home). */
    select: (List<TvCard>) -> List<TvCard> = { it },
    /** Whether reaching the end of the row reads the next page. */
    loadsMore: Boolean = true,
    rowFocus: FocusRequester? = null,
) {
    val state by model.state.collectAsStateWithLifecycle()
    // A shelf reads its list only once it scrolls into view (it keeps its height while it
    // loads), so Home doesn't ask for every shelf at once.
    LaunchedEffect(model) { model.loadFirst() }
    // A shelf whose first read failed (Shorts often does while Home loads everything else)
    // tries again on its own a couple of times, instead of staying empty until you visit its section.
    var retries by remember(model) { mutableStateOf(0) }
    LaunchedEffect(state.failed, retries) {
        if (state.failed && retries < SHELF_RETRIES) {
            delay(SHELF_RETRY_MS * (retries + 1))
            retries++
            model.reload()
        }
    }
    // Channel cards have no place on a shelf of videos; Continue watching keeps only videos you're part way through.
    val cards = shelfCards(shelf, select(state.cards))
    if (cards.isEmpty()) {
        if (state.loading || (!state.failed && !state.done && state.cards.isEmpty())) {
            Box(Modifier.fillMaxWidth().height(if (shelf.tall) TALL_TILE * 16 / 9 + 60.dp else WIDE_TILE * 9 / 16 + 60.dp))
        }
        return
    }
    val restore = remember { FocusRequester() }
    LaunchedEffect(cards.any { it.key == restoreKey }) {
        if (restoreKey != null && cards.any { it.key == restoreKey }) {
            withFrameNanos { }
            requestFocusSoon(restore)
        }
    }
    Column(Modifier.fillMaxWidth()) {
        Text(shelf.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = TvColors.Text,
            modifier = Modifier.padding(start = 40.dp, bottom = 10.dp))
        // Back from a video: open the row scrolled to the tile that was played, so it exists to take focus.
        val row = androidx.compose.foundation.lazy.rememberLazyListState(
            initialFirstVisibleItemIndex = cards.indexOfFirst { it.key == restoreKey }.coerceAtLeast(0).let { (it - 1).coerceAtLeast(0) })
        LazyRow(
            state = row,
            modifier = Modifier.fillMaxWidth().then(rowFocus?.let { Modifier.focusRequester(it) } ?: Modifier).focusRestorer(),
            contentPadding = PaddingValues(start = 40.dp, end = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(if (shelf.tall) 14.dp else 18.dp),
        ) {
            itemsIndexed(cards, key = { _, card -> card.key }) { index, card ->
                VideoTile(
                    card,
                    onClick = { if (card.videoId != null) onPlay(card, cards) else onOpen(card) },
                    onFocused = {
                        onFocused(card)
                        if (loadsMore && index >= cards.size - SHELF_PREFETCH) model.loadMore()
                    },
                    modifier = Modifier.width(if (shelf.tall) TALL_TILE else WIDE_TILE).then(when {
                        card.key == restoreKey -> Modifier.focusRequester(restore)
                        index == 0 && firstTile != null -> Modifier.focusRequester(firstTile)
                        else -> Modifier
                    }),
                    tall = shelf.tall,
                )
            }
        }
    }
}

/** The tiles a shelf shows: no channel cards, and Continue watching only what's part watched. */
internal fun shelfCards(shelf: TvHomeShelf, cards: List<TvCard>): List<TvCard> =
    cards.filter { it.videoId != null || it.playlistId != null }
        .let { list -> if (shelf.section == TvSection.History) list.filter(com.hhst.youtubelite.tv.TvHomeRows::inProgress) else list }

/**
 * Home's tiles split by the YouTube shelf they came in, in order. Tiles with no shelf stay with
 * the one before them; the first shelf is "Recommended" when YouTube gave it no name.
 */
internal fun homeGroups(cards: List<TvCard>): List<Pair<String, List<TvCard>>> {
    val groups = LinkedHashMap<String, MutableList<TvCard>>()
    var current = RECOMMENDED
    cards.forEach { card ->
        current = card.group?.trim()?.takeIf(String::isNotEmpty) ?: current
        groups.getOrPut(current) { mutableListOf() } += card
    }
    return groups.map { it.key to it.value.toList() }
}

/**
 * Home's tiles minus YouTube's general shelves (Trending, Music, News…), which are the same
 * for everyone and have their own rows further down. Untitled and personal shelves stay.
 */
internal fun personalCards(cards: List<TvCard>): List<TvCard> =
    cards.filter { card -> card.group?.trim()?.lowercase()?.let { title -> GENERAL_SHELVES.any(title::contains) } != true }

private val GENERAL_SHELVES = listOf("trending", "popular", "music", "gaming", "news", "sports", "live", "shorts",
    "breaking", "top stories", "recently uploaded", "movies", "podcasts")
private const val RECOMMENDED = "Recommended"
internal const val HOME_PLACE = "home"
private const val SHELF_RETRIES = 2
private const val SHELF_RETRY_MS = 2_500L
private const val HERO_SETTLE_MS = 300L
private val HERO_HEIGHT = 170.dp
private val WIDE_TILE = 250.dp
private val TALL_TILE = 136.dp
private const val HERO_FADE_MS = 350
private const val SHELF_PREFETCH = 4
