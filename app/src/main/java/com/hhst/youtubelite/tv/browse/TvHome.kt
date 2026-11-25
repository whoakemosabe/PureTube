package com.hhst.youtubelite.tv.browse

import androidx.compose.animation.Crossfade
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
internal fun homeShelves(signedIn: Boolean): List<TvHomeShelf> = listOfNotNull(
    TvHomeShelf("Recommended", TvSection.Home),
    TvHomeShelf("Continue watching", TvSection.History).takeIf { signedIn },
    TvHomeShelf("Shorts", TvSection.Shorts, tall = true),
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
) {
    val shelves = remember(browse.signedIn) { homeShelves(browse.signedIn) }
    val recommended = browse.list(TvSection.Home)
    val first by recommended.state.collectAsStateWithLifecycle()
    var focused by remember { mutableStateOf<TvCard?>(null) }
    val hero = focused ?: first.cards.firstOrNull { it.videoId != null }

    Box(Modifier.fillMaxSize()) {
        Hero(hero)
        Column(Modifier.fillMaxSize()) {
            SearchBar(onSearch)
            HeroText(hero, Modifier.height(HERO_HEIGHT))
            when {
                first.loading && first.cards.isEmpty() -> Message("Loading…")
                first.failed && first.cards.isEmpty() -> RetryMessage("Couldn't load Home.", recommended::reload)
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().focusRestorer(),
                    contentPadding = PaddingValues(bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    shelves.forEach { shelf ->
                        item(key = shelf.section.name) {
                            Shelf(shelf, browse.list(shelf.section), onPlay, onOpen, onFocused = { focused = it })
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
private fun Hero(card: TvCard?) {
    Box(Modifier.fillMaxWidth().height(HERO_HEIGHT + 150.dp)) {
        Crossfade(card?.key, animationSpec = tween(HERO_FADE_MS), label = "hero") { key ->
            val shown = card?.takeIf { it.key == key } ?: return@Crossfade
            Box(Modifier.fillMaxSize()) {
                val big = shown.videoId?.takeUnless { shown.short }?.let { "https://i.ytimg.com/vi/$it/maxresdefault.jpg" }
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
) {
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(model) { model.loadFirst() }
    // Channel cards have no place on a shelf of videos.
    val cards = state.cards.filter { it.videoId != null || it.playlistId != null }
    if (cards.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        Text(shelf.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = TvColors.Text,
            modifier = Modifier.padding(start = 40.dp, bottom = 10.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth().focusRestorer(),
            contentPadding = PaddingValues(start = 40.dp, end = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(if (shelf.tall) 14.dp else 18.dp),
        ) {
            itemsIndexed(cards, key = { _, card -> card.key }) { index, card ->
                VideoTile(
                    card,
                    onClick = { if (card.videoId != null) onPlay(card, cards) else onOpen(card) },
                    onFocused = {
                        onFocused(card)
                        if (index >= cards.size - SHELF_PREFETCH) model.loadMore()
                    },
                    modifier = Modifier.width(if (shelf.tall) TALL_TILE else WIDE_TILE),
                    tall = shelf.tall,
                )
            }
        }
    }
}

private val HERO_HEIGHT = 170.dp
private val WIDE_TILE = 250.dp
private val TALL_TILE = 136.dp
private const val HERO_FADE_MS = 350
private const val SHELF_PREFETCH = 4
