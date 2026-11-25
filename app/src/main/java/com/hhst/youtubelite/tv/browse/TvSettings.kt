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

internal data class TvToggleGroup(val title: String, val toggles: List<TvToggle>)

/** Keys whose change alters what browse lists show, so they are read again. */
internal val LIST_KEYS = setOf(PreferenceKeys.ENABLE_HIDE_SHORTS, PreferenceKeys.ENABLE_HIDE_WATCHED)

/** Every phone setting that means something on a TV. Gestures, PiP and haptics don't. */
internal val TV_SETTING_GROUPS = listOf(
    TvToggleGroup("Feed", listOf(
        TvToggle(PreferenceKeys.ENABLE_HIDE_SHORTS, "Hide Shorts", "Leave Shorts out of Home, Subscriptions and Explore"),
        TvToggle(PreferenceKeys.ENABLE_HIDE_WATCHED, "Hide watched videos", "Leave out videos you've already watched"),
        TvToggle(PreferenceKeys.ENABLE_HIDE_RELATED, "Hide recommended videos", "No recommended row under the player"),
    )),
    TvToggleGroup("Playback", listOf(
        TvToggle(PreferenceKeys.ENABLE_AUTOPLAY, "Autoplay", "Play the next video when one ends"),
        TvToggle(PreferenceKeys.REMEMBER_LAST_POSITION, "Remember where I stopped", "Start videos where you left off"),
        TvToggle(PreferenceKeys.REMEMBER_QUALITY, "Remember quality"),
        TvToggle(PreferenceKeys.REMEMBER_PLAYBACK_SPEED, "Remember playback speed"),
        TvToggle(PreferenceKeys.USE_ORIGINAL_TITLE, "Original titles", "Show titles in their original language"),
    )),
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
    )),
    TvToggleGroup("DeArrow", listOf(
        TvToggle(PreferenceKeys.DEARROW_TITLES, "Better titles", "Crowd-sourced titles instead of clickbait"),
        TvToggle(PreferenceKeys.DEARROW_THUMBNAILS, "Better thumbnails", "Crowd-sourced thumbnails instead of clickbait"),
    )),
    TvToggleGroup("Privacy and data", listOf(
        TvToggle(PreferenceKeys.PRIVATE_BROWSING, "Private browsing", "Don't add what you watch to your history"),
        TvToggle(PreferenceKeys.DATA_SAVER, "Data saver", "Lower quality and smaller thumbnails"),
    )),
)

/** Settings, as one D-pad list: account, phones, then every setting group, then the version. */
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
    LazyColumn(
        modifier = Modifier.fillMaxSize().focusRestorer(),
        contentPadding = PaddingValues(start = 40.dp, top = 28.dp, end = 48.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = TvColors.Text) }
        item { GroupTitle("Account") }
        item {
            if (account != null) ActionRow("Sign out on this TV", "Signed in as $account", onSignOut)
            else ActionRow("Sign in", "See your subscriptions, history and playlists", onSignIn)
        }
        item { GroupTitle("Phones") }
        item {
            ActionRow(
                "Forget paired phones",
                if (link.pairedPhones.isEmpty()) "No paired phones. This TV is ${link.tvName}."
                else link.pairedPhones.joinToString(", "),
                onForgetPhones,
                enabled = link.pairedPhones.isNotEmpty(),
            )
        }
        TV_SETTING_GROUPS.forEach { group ->
            item(key = "g-${group.title}") { GroupTitle(group.title) }
            group.toggles.forEach { toggle ->
                item(key = toggle.key) {
                    ToggleRow(toggle, values[toggle.key] == true) { settings.set(toggle.key, it) }
                }
            }
        }
        item { GroupTitle("About") }
        item(key = "check-update") {
            val context = LocalContext.current
            val waiting = UpdateWatch.waitingVersion
            ActionRow(
                if (waiting != null) "Update to Puretube $waiting" else "Check for updates",
                updateSummary(UpdateWatch.ui, version, waiting),
                {
                    // A newer version opens its panel, even one closed with Not now before.
                    UpdateWatch.showBanner(context)
                    if (waiting == null) UpdateWatch.checkOnly(context)
                },
            )
        }
    }
}

/** What the Check for updates row says under its title. */
internal fun updateSummary(ui: UpdateUi, installed: String, waiting: String?): String = when {
    ui is UpdateUi.Checking -> "Checking…"
    ui is UpdateUi.Error -> ui.message
    waiting != null -> "You have $installed. Press to see what's new and install."
    ui is UpdateUi.UpToDate -> "You have the latest version, $installed."
    else -> "You have Puretube $installed."
}

@Composable
private fun GroupTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = TvColors.Muted,
        modifier = Modifier.padding(start = 16.dp, top = 22.dp, bottom = 4.dp))
}

@Composable
private fun ActionRow(title: String, summary: String, onClick: () -> Unit, enabled: Boolean = true) =
    SettingRow(title, summary, onClick, enabled) {}

@Composable
private fun ToggleRow(toggle: TvToggle, on: Boolean, onChange: (Boolean) -> Unit) =
    SettingRow(toggle.title, toggle.summary, { onChange(!on) }) { Switch(on) }

@Composable
private fun SettingRow(title: String, summary: String, onClick: () -> Unit, enabled: Boolean = true, trailing: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth().heightIn(min = 60.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = TvColors.Text,
            focusedContainerColor = TvColors.Focused,
            focusedContentColor = TvColors.OnFocused,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = TvColors.Muted,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (summary.isNotBlank()) {
                    Text(summary, style = MaterialTheme.typography.bodyMedium, color = LocalContentColor.current.copy(alpha = 0.7f))
                }
            }
            Spacer(Modifier.width(16.dp))
            trailing()
        }
    }
}

/** A plain on/off pill; it follows the row's focus colours. */
@Composable
private fun Switch(on: Boolean) {
    val ink = LocalContentColor.current
    Box(
        Modifier
            .size(width = 44.dp, height = 24.dp)
            .background(if (on) TvColors.Red else ink.copy(alpha = 0.3f), CircleShape)
            .padding(3.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(18.dp).background(Color.White, CircleShape))
    }
}
