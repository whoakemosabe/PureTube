package com.hhst.youtubelite.tv.browse

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.hhst.youtubelite.search.domain.SearchFilters
import com.hhst.youtubelite.search.domain.SearchResultType
import com.hhst.youtubelite.search.domain.SearchSort
import com.hhst.youtubelite.search.domain.SearchUploadDate
import com.hhst.youtubelite.subscriptions.data.SubscriptionsFilter
import com.hhst.youtubelite.tv.FrostButton
import com.hhst.youtubelite.tv.FrostPill
import com.hhst.youtubelite.tv.TvColors
import com.hhst.youtubelite.tv.requestFocusSoon
import com.hhst.youtubelite.ui.design.AppIcon
import kotlinx.coroutines.delay

/** Holding OK on a tile opens its menu; screens deep in the tree reach it through this. */
internal val LocalTileMenu = staticCompositionLocalOf<(TvCard) -> Unit> { {} }

/** Each screen's scroll spot and focused tile, kept for the activity's life. */
internal val LocalTvPlaces = staticCompositionLocalOf<MutableMap<String, TvPlace>> { mutableMapOf() }

/** What a tile's menu offers, in YouTube's order. */
internal enum class TileAction(val label: String) {
    Play("Play"), PlayNext("Play next"), WatchLater("Save to Watch later"), Channel("Go to channel"), NotInterested("Not interested"),
}

/** The actions that make sense for [card]: videos get all of them, a playlist only opens. */
internal fun tileActions(card: TvCard, signedIn: Boolean): List<TileAction> = buildList {
    add(TileAction.Play)
    if (card.videoId == null) return@buildList
    add(TileAction.PlayNext)
    if (signedIn) add(TileAction.WatchLater)
    if (!card.ownerId.isNullOrBlank()) add(TileAction.Channel)
    // Only Home's own tiles carry what YouTube needs for this.
    if (signedIn && card.key.startsWith("v-")) add(TileAction.NotInterested)
}

/** The long-press menu over a tile: a short list in the middle of the screen. */
@Composable
internal fun TileMenu(card: TvCard, actions: List<TileAction>, onPick: (TileAction) -> Unit, onClose: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(card.key) { requestFocusSoon(first) }
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(420.dp).background(Color(0xFF212121), RoundedCornerShape(20.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(card.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TvColors.Text,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            actions.forEachIndexed { index, action ->
                FrostPill(action.label, { onPick(action) }, if (index == 0) Modifier.focusRequester(first).fillMaxWidth() else Modifier.fillMaxWidth())
            }
        }
    }
}

/** A small menu of [options] under a title, in the middle of the screen. */
@Composable
internal fun ActionMenu(title: String, options: List<String>, onPick: (String) -> Unit, onClose: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { requestFocusSoon(first) }
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(340.dp).background(Color(0xFF212121), RoundedCornerShape(20.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TvColors.Text)
            Spacer(Modifier.height(4.dp))
            options.forEachIndexed { index, option ->
                FrostPill(option, { onPick(option) }, if (index == 0) Modifier.focusRequester(first).fillMaxWidth() else Modifier.fillMaxWidth())
            }
        }
    }
}

/** A short line at the top of the screen that clears itself. */
@Composable
internal fun BrowseNotice(text: String?, onDone: () -> Unit, modifier: Modifier = Modifier) {
    if (text == null) return
    LaunchedEffect(text) {
        delay(NOTICE_MS)
        onDone()
    }
    Text(text, color = TvColors.Text, style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(top = 24.dp).background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(24.dp))
            .padding(horizontal = 24.dp, vertical = 10.dp))
}

/** A row of pills where one is picked, like YouTube's chips. */
@Composable
internal fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        options.forEach { option -> FrostPill(label(option), { onPick(option) }, selected = option == selected) }
    }
}

/** The filters Subscriptions offers on the TV (posts have no TV screen). */
internal val TV_SUBSCRIPTION_FILTERS = SubscriptionsFilter.entries.filter { it != SubscriptionsFilter.Posts }

internal fun SubscriptionsFilter.label(): String = when (this) {
    SubscriptionsFilter.All -> "All"
    SubscriptionsFilter.Today -> "Today"
    SubscriptionsFilter.ContinueWatching -> "Continue watching"
    SubscriptionsFilter.Unwatched -> "Unwatched"
    SubscriptionsFilter.Live -> "Live"
    SubscriptionsFilter.Posts -> "Posts"
}

/** Search's filters as three pills that each step to the next choice when pressed. */
@Composable
internal fun SearchFilterPills(filters: SearchFilters, onChange: (SearchFilters) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FrostPill("Type: ${filters.type.label}", { onChange(filters.copy(type = filters.type.next())) },
            selected = filters.type != SearchResultType.All)
        FrostPill("Uploaded: ${filters.uploaded.label}", { onChange(filters.copy(uploaded = filters.uploaded.next())) },
            selected = filters.uploaded != SearchUploadDate.Anytime)
        FrostPill("Sort: ${filters.sort.label}", { onChange(filters.copy(sort = filters.sort.next())) },
            selected = filters.sort != SearchSort.Relevance)
    }
}

private inline fun <reified E : Enum<E>> E.next(): E {
    val all = enumValues<E>()
    return all[(ordinal + 1) % all.size]
}

/** The remote's mic: Google TV's voice search, its words go into the field and search. */
@Composable
internal fun VoiceButton(onHeard: (String) -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.takeIf(String::isNotBlank)?.let(onHeard)
    }
    FrostButton(AppIcon.Voice, "Voice search", {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Search YouTube")
        runCatching { launcher.launch(intent) }
    }, size = 52.dp)
}

/** Recent searches first, then YouTube's suggestions, as pills to press. */
@Composable
internal fun SearchRows(history: List<String>, suggestions: List<String>, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    if (history.isEmpty() && suggestions.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (history.isNotEmpty()) {
            Text("Recent searches", style = MaterialTheme.typography.titleSmall, color = TvColors.Muted)
            WrapPills(history, onPick)
        }
        if (suggestions.isNotEmpty()) {
            Text("Suggestions", style = MaterialTheme.typography.titleSmall, color = TvColors.Muted,
                modifier = Modifier.padding(top = 6.dp))
            WrapPills(suggestions, onPick)
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun WrapPills(items: List<String>, onPick: (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items.forEach { item -> FrostPill(item, { onPick(item) }) }
    }
}

private const val NOTICE_MS = 3_000L
