package com.hhst.youtubelite.tv.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.hhst.youtubelite.extension.ExtensionManager
import com.hhst.youtubelite.extension.PreferenceKeys
import com.hhst.youtubelite.tv.TvColors
import com.hhst.youtubelite.tv.TvMenu
import com.hhst.youtubelite.tv.TvMenuHeader
import com.hhst.youtubelite.tv.TvMenuRow
import com.hhst.youtubelite.tv.TvSwitch
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.tv.TvLinkUiState
import androidx.compose.ui.platform.LocalContext
import com.hhst.youtubelite.update.UpdateUi
import com.hhst.youtubelite.update.UpdateWatch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The app's on/off settings, shared with the phone screens through the same store. */
internal interface TvSettingsStore {
    val values: StateFlow<Map<String, Boolean>>
    fun set(key: String, enabled: Boolean)
}

internal class ExtensionTvSettings(private val prefs: ExtensionManager) : TvSettingsStore {
    private val _values = MutableStateFlow(prefs.allPreferences())
    override val values: StateFlow<Map<String, Boolean>> = _values.asStateFlow()
    private val listener: (String) -> Unit = { _values.value = prefs.allPreferences() }

    init { prefs.addOnChangedListener(listener) }

    override fun set(key: String, enabled: Boolean) = prefs.setEnabled(key, enabled)

    fun close() = prefs.removeOnChangedListener(listener)
}

internal data class TvToggle(val key: String, val title: String, val summary: String = "")

internal data class TvToggleGroup(val title: String, val toggles: List<TvToggle>, val icon: AppIcon? = null)

/** Keys whose change alters what browse lists show, so they are read again. */
internal val LIST_KEYS = setOf(PreferenceKeys.ENABLE_HIDE_SHORTS, PreferenceKeys.ENABLE_HIDE_WATCHED)

/** Every phone setting that means something on a TV. Gestures, PiP and haptics don't. */
internal val TV_SETTING_GROUPS = listOf(
    TvToggleGroup("Feed", listOf(
        TvToggle(PreferenceKeys.ENABLE_HIDE_SHORTS, "Hide Shorts", "Leave Shorts out of Home, Subscriptions and Explore"),
        TvToggle(PreferenceKeys.ENABLE_HIDE_WATCHED, "Hide watched videos", "Leave out videos you've already watched"),
        TvToggle(PreferenceKeys.ENABLE_HIDE_RELATED, "Hide recommended videos", "No recommended row under the player"),
    ), AppIcon.Home),
    TvToggleGroup("Playback", listOf(
        TvToggle(PreferenceKeys.ENABLE_AUTOPLAY, "Autoplay", "Play the next video when one ends"),
        TvToggle(PreferenceKeys.REMEMBER_LAST_POSITION, "Remember where I stopped", "Start videos where you left off"),
        TvToggle(PreferenceKeys.REMEMBER_QUALITY, "Remember quality"),
        TvToggle(PreferenceKeys.REMEMBER_PLAYBACK_SPEED, "Remember playback speed"),
        TvToggle(PreferenceKeys.USE_ORIGINAL_TITLE, "Original titles", "Show titles in their original language"),
    ), AppIcon.Play),
    TvToggleGroup("SponsorBlock", listOf(
        TvToggle(PreferenceKeys.SKIP_SPONSORS, "Skip sponsors"),
        TvToggle(PreferenceKeys.SKIP_SELF_PROMO, "Skip self-promotion"),
        TvToggle(PreferenceKeys.SKIP_INTERACTION, "Skip subscribe reminders"),
        TvToggle(PreferenceKeys.SKIP_INTRO, "Skip intros"),
        TvToggle(PreferenceKeys.SKIP_OUTRO, "Skip end cards"),
        TvToggle(PreferenceKeys.SKIP_PREVIEW, "Skip previews and recaps"),
        TvToggle(PreferenceKeys.SKIP_MUSIC_OFFTOPIC, "Skip non-music parts of music videos"),
        TvToggle(PreferenceKeys.SKIP_FILLER, "Skip filler"),
        TvToggle(PreferenceKeys.SKIP_POI_HIGHLIGHT, "Jump to the highlight"),
        TvToggle(PreferenceKeys.SPONSOR_COUNTDOWN, "Count down before skipping"),
    ), AppIcon.Block),
    TvToggleGroup("DeArrow", listOf(
        TvToggle(PreferenceKeys.DEARROW_TITLES, "Better titles", "Crowd-sourced titles instead of clickbait"),
        TvToggle(PreferenceKeys.DEARROW_THUMBNAILS, "Better thumbnails", "Crowd-sourced thumbnails instead of clickbait"),
    ), AppIcon.Sparkle),
    TvToggleGroup("Privacy and data", listOf(
        TvToggle(PreferenceKeys.PRIVATE_BROWSING, "Private browsing", "Don't add what you watch to your history"),
        TvToggle(PreferenceKeys.DATA_SAVER, "Data saver", "Lower quality and smaller thumbnails"),
    ), AppIcon.Lock),
)

/**
 * Settings, as one D-pad list of cards, in the same look as the player's menus: account,
 * phones, then every setting group, then the version.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun SettingsSection(
    link: TvLinkUiState,
    account: String?,
    settings: TvSettingsStore,
    version: String,
    onForgetPhones: () -> Unit,
    onSignOut: () -> Unit,
    onSignIn: () -> Unit,
) {
    val values by settings.values.collectAsStateWithLifecycle()
    // Settings opens on its top row, not wherever the D-pad happens to land.
    val top = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { com.hhst.youtubelite.tv.requestFocusSoon(top) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().focusRestorer(top),
        contentPadding = PaddingValues(start = 40.dp, top = 28.dp, end = 48.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item { TvMenuHeader("Settings", "Puretube $version", Modifier.widthIn(max = SETTINGS_WIDTH)) }
        item(key = "account") {
            SettingsCard("Account", AppIcon.You) {
                val first = Modifier.focusRequester(top)
                if (account != null) TvMenuRow("Sign out on this TV", onSignOut, first, summary = "Signed in as $account")
                else TvMenuRow("Sign in", onSignIn, first, summary = "See your subscriptions, history and playlists", opens = true)
            }
        }
        item(key = "phones") {
            SettingsCard("Phones", AppIcon.Device) {
                TvMenuRow(
                    "Forget paired phones",
                    onForgetPhones,
                    summary = if (link.pairedPhones.isEmpty()) "No paired phones. This TV is ${link.tvName}."
                    else link.pairedPhones.joinToString(", "),
                    enabled = link.pairedPhones.isNotEmpty(),
                )
            }
        }
        TV_SETTING_GROUPS.forEach { group ->
            item(key = "g-${group.title}") {
                SettingsCard(group.title, group.icon) {
                    group.toggles.forEach { toggle ->
                        val on = values[toggle.key] == true
                        TvMenuRow(toggle.title, { settings.set(toggle.key, !on) }, summary = toggle.summary) { TvSwitch(on) }
                    }
                }
            }
        }
        item(key = "about") {
            SettingsCard("About", AppIcon.Info) {
                val context = LocalContext.current
                val waiting = UpdateWatch.waitingVersion
                TvMenuRow(
                    if (waiting != null) "Update to Puretube $waiting" else "Check for updates",
                    {
                        // A newer version opens its panel, even one closed with Not now before.
                        UpdateWatch.showBanner(context)
                        if (waiting == null) UpdateWatch.checkOnly(context)
                    },
                    summary = updateSummary(UpdateWatch.ui, version, waiting),
                    icon = AppIcon.Refresh,
                )
            }
        }
    }
}

/** One settings group: a small titled heading, then its rows on a dark card. */
@Composable
private fun SettingsCard(title: String, icon: AppIcon?, rows: @Composable () -> Unit) {
    Column(Modifier.widthIn(max = SETTINGS_WIDTH).fillMaxWidth()) {
        Row(Modifier.padding(start = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                AppIcon(icon, Modifier.size(18.dp), color = TvColors.Muted)
                Spacer(Modifier.width(10.dp))
            }
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = TvColors.Muted)
        }
        Column(
            Modifier.fillMaxWidth().background(TvMenu.Card, TvMenu.CardShape).padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) { rows() }
    }
}

private val SETTINGS_WIDTH = 820.dp

/** What the Check for updates row says under its title. */
internal fun updateSummary(ui: UpdateUi, installed: String, waiting: String?): String = when {
    ui is UpdateUi.Checking -> "Checking…"
    ui is UpdateUi.Error -> ui.message
    waiting != null -> "You have $installed. Press to see what's new and install."
    ui is UpdateUi.UpToDate -> "You have the latest version, $installed."
    else -> "You have Puretube $installed."
}
