package com.hhst.youtubelite.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.update.UpdateUi
import com.hhst.youtubelite.update.Updater

/**
 * The "What's new" window: the new version's highlights, then Download (with progress) and
 * Install, all in one place. Shown for Available, Downloading and Ready.
 */
@Composable
fun UpdateDialog(ui: UpdateUi, progress: Float, onMain: () -> Unit, onDismiss: () -> Unit) {
    val release: Updater.Release = when (ui) {
        is UpdateUi.Available -> ui.release
        is UpdateUi.Downloading -> ui.release
        is UpdateUi.Ready -> ui.release
        else -> return
    }
    val downloading = ui is UpdateUi.Downloading
    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        title = { Text("PureTube ${release.version} is available") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (release.behind > 1) Text("You're ${release.behind} updates behind. Here's everything new:",
                    style = MaterialTheme.typography.bodyMedium)
                if (release.notes.isEmpty()) Text("Fixes and improvements.", style = MaterialTheme.typography.bodyMedium)
                release.notes.forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium) }
                if (downloading) {
                    Text("Downloading… ${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                }
                (ui as? UpdateUi.Ready)?.note?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onMain, enabled = !downloading) {
                Text(when (ui) {
                    is UpdateUi.Ready -> "Install"
                    is UpdateUi.Downloading -> "Downloading…"
                    else -> "Download"
                })
            }
        },
        dismissButton = { if (!downloading) TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}
