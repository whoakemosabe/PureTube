package com.hhst.youtubelite.tv.browse

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.hhst.youtubelite.tv.FrostPill
import com.hhst.youtubelite.tv.TvColors
import com.hhst.youtubelite.ui.design.Artwork

/**
 * A channel page, YouTube TV style: the picture, name and subscriber count with Subscribe,
 * then the tabs as pills, then the tab's tiles.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun ChannelScreen(model: TvChannelModel, onPlay: (TvCard, List<TvCard>) -> Unit, onOpen: (TvCard) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val header = state.header
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 40.dp, top = 28.dp, end = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            header?.avatar?.takeIf(String::isNotBlank)?.let {
                Artwork(it, Modifier.size(72.dp).clip(CircleShape))
                Spacer(Modifier.width(20.dp))
            }
            Column(Modifier.weight(1f, fill = false)) {
                Text(header?.name ?: "Channel", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                    color = TvColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(header?.handle, header?.subscribers, header?.videoCount).filter(String::isNotBlank).joinToString(" • ")
                if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodyLarge, color = TvColors.Muted, maxLines = 1)
            }
        }
        when {
            state.loading -> Text("Loading…", color = TvColors.Muted, modifier = Modifier.padding(start = 40.dp, top = 24.dp))
            state.failed || header == null -> Column(Modifier.padding(start = 40.dp, top = 24.dp)) {
                Text("Couldn't load this channel.", color = TvColors.Muted)
                Spacer(Modifier.height(12.dp))
                FrostPill("Try again", model::load)
            }
            else -> {
                Row(
                    Modifier.padding(start = 40.dp, top = 16.dp, bottom = 4.dp).focusRestorer(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FrostPill(
                        when {
                            state.busy -> "…"
                            state.subscribed == true -> "Subscribed"
                            else -> "Subscribe"
                        },
                        model::toggleSubscribe,
                        active = state.subscribed == true,
                    )
                    Spacer(Modifier.width(14.dp))
                    TvChannelTab.entries.forEach { tab ->
                        FrostPill(tab.label, { model.selectTab(tab) }, selected = tab == state.tab)
                    }
                }
                state.notice?.let {
                    Text(it, color = TvColors.Muted, modifier = Modifier.padding(start = 40.dp, top = 4.dp))
                }
                key(state.tab) {
                    CardGrid(
                        title = null,
                        model = model.list(state.tab),
                        onPlay = onPlay,
                        onOpen = onOpen,
                        tall = state.tab == TvChannelTab.Shorts,
                    )
                }
            }
        }
    }
}
