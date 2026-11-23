package com.hhst.youtubelite.ui.about

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.ui.design.AppIconButton
import com.hhst.youtubelite.ui.design.AppText
import com.hhst.youtubelite.ui.design.DesignTokens
import com.hhst.youtubelite.update.UpdateUi
import com.hhst.youtubelite.update.UpdateWatch
import kotlinx.coroutines.launch

/** What the banner says and whether it shows, from the updater's state; pure so tests pin it. */
internal data class UpdateBannerState(val version: String, val detail: String, val action: String?, val progress: Float?)

internal fun updateBannerState(waiting: String?, hidden: String?, ui: UpdateUi, progress: Float): UpdateBannerState? {
    val readyVersion = (ui as? UpdateUi.Ready)?.release?.version
    val downloadingVersion = (ui as? UpdateUi.Downloading)?.release?.version
    val version = downloadingVersion ?: readyVersion ?: waiting ?: return null
    // Hiding only applies before the download starts; a download in progress always shows.
    if (hidden == version && downloadingVersion == null && readyVersion == null) return null
    return when (ui) {
        is UpdateUi.Downloading -> UpdateBannerState(version, "Downloading… ${(progress * 100).toInt()}%", null, progress)
        is UpdateUi.Ready -> UpdateBannerState(version, ui.note ?: "Downloaded and ready to install", "Install", null)
        UpdateUi.Checking -> UpdateBannerState(version, "Checking…", null, null)
        is UpdateUi.Error -> UpdateBannerState(version, ui.message, "Retry", null)
        else -> UpdateBannerState(version, "Tap to see what's new", "Update", null)
    }
}

/**
 * Dropi's update banner at the top of Home: it appears when a newer version is waiting. A tap
 * opens the What's new window (changelog, Download with a progress bar, then Install); while
 * downloading, the banner shows the same progress. × hides it until the next version.
 */
@Composable
fun UpdateBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = updateBannerState(UpdateWatch.waitingVersion, UpdateWatch.bannerHidden, UpdateWatch.ui, UpdateWatch.progress)
    // The window the banner opens; it carries the download's progress bar too.
    if (UpdateWatch.whatsNewOpen) UpdateDialog(UpdateWatch.ui, UpdateWatch.progress,
        onMain = { scope.launch { UpdateWatch.tap(context) } },
        onDismiss = { UpdateWatch.notNow() })
    AnimatedVisibility(state != null, modifier, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        val shown = state ?: return@AnimatedVisibility
        val act: () -> Unit = { scope.launch { UpdateWatch.bannerTap(context) } }
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp)).background(DesignTokens.Surface)
            .clickable(enabled = shown.action != null, role = Role.Button, onClick = act)
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp)
            .testTag("update-banner")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(AppIcon.Download, color = DesignTokens.Red)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    AppText("PureTube ${shown.version} is out", size = 15, line = 20, weight = FontWeight.SemiBold, maxLines = 1)
                    AppText(shown.detail, size = 13, line = 18, color = DesignTokens.Secondary, maxLines = 2)
                }
                shown.action?.let { label ->
                    Box(Modifier.padding(start = 8.dp).clip(RoundedCornerShape(18.dp)).background(DesignTokens.Red)
                        .clickable(role = Role.Button, onClick = act).padding(horizontal = 14.dp, vertical = 8.dp)
                        .testTag("update-banner-action")) {
                        AppText(label, size = 13, line = 18, weight = FontWeight.SemiBold, color = androidx.compose.ui.graphics.Color.White)
                    }
                }
                if (shown.progress == null) AppIconButton(AppIcon.Close, "Hide this update", diameter = 40.dp) {
                    UpdateWatch.hideBanner(context, shown.version)
                } else Spacer(Modifier.size(12.dp))
            }
            shown.progress?.let { p ->
                Spacer(Modifier.height(10.dp))
                androidx.compose.material3.LinearProgressIndicator(progress = { p },
                    modifier = Modifier.fillMaxWidth().padding(end = 12.dp).testTag("update-banner-progress"),
                    color = DesignTokens.Red, trackColor = DesignTokens.Red.copy(alpha = 0.2f))
            }
        }
    }
}
