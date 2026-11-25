package com.hhst.youtubelite.tv

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.hhst.youtubelite.update.UpdateUi
import com.hhst.youtubelite.update.UpdateWatch

/** True when a newer version is waiting and its panel wasn't closed. */
internal fun tvUpdateWaiting(): Boolean {
    val version = UpdateWatch.waitingVersion ?: return false
    return UpdateWatch.bannerHidden != version
}

/**
 * The update panel, over the browse screens: what's new, then Download with its progress,
 * then Install. "Not now" hides it until the next version.
 */
@Composable
internal fun TvUpdatePanel() {
    val context = LocalContext.current
    val version = UpdateWatch.waitingVersion ?: return
    val ui = UpdateWatch.ui
    val main = remember { FocusRequester() }
    val close = {
        UpdateWatch.notNow()
        UpdateWatch.hideBanner(context, version)
    }
    LaunchedEffect(Unit) {
        // The panel only knows the version until GitHub is asked for the notes.
        if (ui !is UpdateUi.Available && ui !is UpdateUi.Downloading && ui !is UpdateUi.Ready) UpdateWatch.bannerTap(context)
        requestFocusSoon(main)
    }
    LaunchedEffect(ui is UpdateUi.Ready) { requestFocusSoon(main) }
    BackHandler(onBack = close)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .width(620.dp)
                .background(Color(0xFF212121), RoundedCornerShape(24.dp))
                .padding(36.dp),
        ) {
            Text("Puretube $version is out", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = TvColors.Text)
            Spacer(Modifier.height(14.dp))
            val notes = when (ui) {
                is UpdateUi.Available -> ui.release.notes
                is UpdateUi.Downloading -> ui.release.notes
                is UpdateUi.Ready -> ui.release.notes
                else -> emptyList()
            }
            notes.take(MAX_NOTES).forEach { line ->
                Text("• $line", style = MaterialTheme.typography.bodyLarge, color = TvColors.Muted, maxLines = 2)
            }
            when (ui) {
                UpdateUi.Checking -> Text("Getting what's new…", style = MaterialTheme.typography.bodyLarge, color = TvColors.Muted)
                is UpdateUi.Error -> Text(ui.message, style = MaterialTheme.typography.bodyLarge, color = TvColors.Text)
                is UpdateUi.Ready -> ui.note?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = TvColors.Text) }
                else -> {}
            }
            if (ui is UpdateUi.Downloading) {
                Spacer(Modifier.height(18.dp))
                Box(Modifier.fillMaxWidth().height(6.dp).background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(3.dp))) {
                    Box(Modifier.fillMaxWidth(UpdateWatch.progress.coerceIn(0f, 1f)).height(6.dp).background(TvColors.Red, RoundedCornerShape(3.dp)))
                }
            }
            Spacer(Modifier.height(26.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                when (ui) {
                    is UpdateUi.Available -> FrostPill("Download", { UpdateWatch.tap(context) }, Modifier.focusRequester(main))
                    is UpdateUi.Ready -> FrostPill("Install", { UpdateWatch.tap(context) }, Modifier.focusRequester(main))
                    is UpdateUi.Error -> FrostPill("Try again", { UpdateWatch.bannerTap(context) }, Modifier.focusRequester(main))
                    else -> {}
                }
                FrostPill("Not now", close, if (ui is UpdateUi.Available || ui is UpdateUi.Ready || ui is UpdateUi.Error) Modifier else Modifier.focusRequester(main))
            }
        }
    }
}

private const val MAX_NOTES = 6
