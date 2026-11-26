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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
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
import com.hhst.youtubelite.tv.TvMenuRow
import com.hhst.youtubelite.tv.TvSidePanel
import com.hhst.youtubelite.tv.TvSwitch
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.hhst.youtubelite.tv.requestFocusSoon
import com.hhst.youtubelite.ui.design.AppIcon
import kotlinx.coroutines.delay

/** Holding OK on a tile opens its menu; screens deep in the tree reach it through this. */
internal val LocalTileMenu = staticCompositionLocalOf<(TvCard) -> Unit> { {} }

/** Each screen's scroll spot and focused tile, kept for the activity's life. */
internal val LocalTvPlaces = staticCompositionLocalOf<MutableMap<String, TvPlace>> { mutableMapOf() }

/** What a tile's menu offers, in YouTube's order. */
internal enum class TileAction(val label: String, val icon: AppIcon) {
    Play("Play", AppIcon.Play),
    StartOver("Play from the start", AppIcon.Refresh),
    PlayNext("Play next", AppIcon.QueueAdd),
    NotInterested("Not interested", AppIcon.Block),
    Dislike("Dislike", AppIcon.Dislike),
    WatchLater("Save to Watch later", AppIcon.Clock),
    Subscribe("Subscribe", AppIcon.Bell),
    Channel("Go to channel", AppIcon.ChannelGrid),
    Download("Download", AppIcon.Download),
}

/** The actions that make sense for [card]: videos get all of them, a playlist only opens. */
internal fun tileActions(card: TvCard, signedIn: Boolean): List<TileAction> = buildList {
    add(TileAction.Play)
    if (card.videoId == null) return@buildList
    // Part-watched: Play carries on, this starts again.
    if ((card.progress ?: 0f) > 0f && !card.live) add(TileAction.StartOver)
    add(TileAction.PlayNext)
    // Only Home's own tiles carry what YouTube needs for this.
    if (signedIn && card.key.startsWith("v-")) add(TileAction.NotInterested)
    if (signedIn) add(TileAction.Dislike)
    if (signedIn) add(TileAction.WatchLater)
    val hasChannel = !card.ownerId.isNullOrBlank()
    if (signedIn && hasChannel) add(TileAction.Subscribe)
    if (hasChannel) add(TileAction.Channel)
    if (!card.live) add(TileAction.Download)
}

/** The long-press menu over a tile, sliding in from the right like the player's menus. */
@Composable
internal fun TileMenu(
    card: TvCard,
    actions: List<TileAction>,
    subscribed: Boolean?,
    onPick: (TileAction) -> Unit,
    onClose: () -> Unit,
) {
    SideMenu(card.title, card.author?.takeIf(String::isNotBlank) ?: card.subtitle.takeIf(String::isNotBlank),
        onClose = onClose, focusKey = card.key) { first ->
        actions.forEachIndexed { index, action ->
            val modifier = if (index == 0) Modifier.focusRequester(first) else Modifier
            if (action == TileAction.Subscribe) {
                // Waits for YouTube's answer rather than guess which way it goes.
                TvMenuRow(
                    when (subscribed) { true -> "Unsubscribe"; false -> "Subscribe"; null -> "Subscribe" },
                    { onPick(action) }, modifier, icon = AppIcon.Bell,
                    summary = card.author?.takeIf(String::isNotBlank) ?: if (subscribed == null) "Checking…" else null,
                    value = if (subscribed == true) "Subscribed" else null,
                    enabled = subscribed != null,
                )
            } else {
                TvMenuRow(action.label, { onPick(action) }, modifier, icon = action.icon)
            }
        }
    }
}

/** What holding OK on a sidebar section offers. */
internal enum class SectionAction { Refresh, HideShorts, HideWatched, BetterTitles, SignIn, Search, Settings }

internal fun sectionActions(section: TvSection, signedIn: Boolean): List<SectionAction> = buildList {
    add(SectionAction.Refresh)
    // The feed switches matter where there's a feed of videos.
    if (section != TvSection.Shorts && section != TvSection.Playlists && section != TvSection.Channels) {
        add(SectionAction.HideShorts)
        add(SectionAction.HideWatched)
    }
    add(SectionAction.BetterTitles)
    if (!signedIn) add(SectionAction.SignIn)
    add(SectionAction.Search)
    add(SectionAction.Settings)
}

internal val SectionAction.key: String? get() = when (this) {
    SectionAction.HideShorts -> com.hhst.youtubelite.extension.PreferenceKeys.ENABLE_HIDE_SHORTS
    SectionAction.HideWatched -> com.hhst.youtubelite.extension.PreferenceKeys.ENABLE_HIDE_WATCHED
    SectionAction.BetterTitles -> com.hhst.youtubelite.extension.PreferenceKeys.DEARROW_TITLES
    else -> null
}

/** The menu for a held sidebar section: refresh it, the feed switches, and quick ways out. */
@Composable
internal fun SectionMenu(
    section: TvSection,
    signedIn: Boolean,
    values: Map<String, Boolean>,
    onPick: (SectionAction) -> Unit,
    onClose: () -> Unit,
) {
    SideMenu(section.label, "Options", onClose = onClose, focusKey = section) { first ->
        sectionActions(section, signedIn).forEachIndexed { index, action ->
            val modifier = if (index == 0) Modifier.focusRequester(first) else Modifier
            val key = action.key
            when (action) {
                SectionAction.Refresh -> TvMenuRow("Refresh ${section.label}", { onPick(action) }, modifier, icon = AppIcon.Refresh)
                SectionAction.SignIn -> TvMenuRow("Sign in", { onPick(action) }, modifier, icon = AppIcon.You,
                    summary = "See your own recommendations", opens = true)
                SectionAction.Search -> TvMenuRow("Search", { onPick(action) }, modifier, icon = AppIcon.Search, opens = true)
                SectionAction.Settings -> TvMenuRow("All settings", { onPick(action) }, modifier, icon = AppIcon.Settings, opens = true)
                else -> {
                    val on = key != null && values[key] == true
                    val (label, icon) = when (action) {
                        SectionAction.HideShorts -> "Hide Shorts" to AppIcon.Shorts
                        SectionAction.HideWatched -> "Hide watched videos" to AppIcon.Check
                        else -> "Better titles (DeArrow)" to AppIcon.Sparkle
                    }
                    TvMenuRow(label, { onPick(action) }, modifier, icon = icon) { TvSwitch(on) }
                }
            }
        }
    }
}

/** A small menu of [options] under a title, from the right. */
@Composable
internal fun ActionMenu(title: String, options: List<String>, onPick: (String) -> Unit, onClose: () -> Unit) {
    SideMenu(title, null, onClose = onClose, focusKey = title) { first ->
        options.forEachIndexed { index, option ->
            TvMenuRow(option, { onPick(option) }, if (index == 0) Modifier.focusRequester(first) else Modifier)
        }
    }
}

/** A menu in the same right-hand panel the player uses, with focus on its first row. */
@Composable
private fun SideMenu(
    title: String,
    eyebrow: String?,
    onClose: () -> Unit,
    focusKey: Any,
    rows: @Composable (FocusRequester) -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(focusKey) { requestFocusSoon(first) }
    BackHandler(onBack = onClose)
    // The menu opens while OK is still held: letting go must not press its first row.
    // Only an OK pressed inside the menu counts.
    var pressedHere by remember(focusKey) { androidx.compose.runtime.mutableStateOf(false) }
    TvSidePanel(title, eyebrow = eyebrow) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).focusGroup().onPreviewKeyEvent { event ->
                if (!isOk(event.key)) return@onPreviewKeyEvent false
                when (event.type) {
                    KeyEventType.KeyDown -> { pressedHere = true; false }
                    KeyEventType.KeyUp -> !pressedHere
                    else -> false
                }
            },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) { rows(first) }
    }
}

private fun isOk(key: Key) = key == Key.DirectionCenter || key == Key.Enter || key == Key.NumPadEnter

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
