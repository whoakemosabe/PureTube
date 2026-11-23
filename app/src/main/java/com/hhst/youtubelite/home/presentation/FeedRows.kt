package com.hhst.youtubelite.home.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.R
import com.hhst.youtubelite.dearrow.VideoArtwork
import com.hhst.youtubelite.dearrow.rememberDeArrowTitle
import com.hhst.youtubelite.home.domain.FeedItem
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.ui.design.AppIconButton
import com.hhst.youtubelite.ui.design.AppText
import com.hhst.youtubelite.ui.design.DesignTokens

/**
 * Settings > Feed layout > List: a 160 dp thumbnail beside the title, channel and details,
 * so about twice as many videos fit on a phone screen. Home and Subscriptions share it.
 */
@Composable
internal fun FeedVideoRow(item: FeedItem.Video, testTag: String, onOpen: () -> Unit, onMenu: () -> Unit) {
    Row(Modifier.fillMaxWidth().testTag(testTag).clickable(role = Role.Button, onClick = onOpen),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.width(160.dp).aspectRatio(16f / 9).clip(RoundedCornerShape(8.dp))
            .semantics { contentDescription = "${item.title}, ${item.duration}" }) {
            VideoArtwork(item.id, item.artwork, Modifier.fillMaxSize())
            FeedDurationBadge(item, Modifier.align(Alignment.BottomEnd).padding(6.dp))
            FeedProgressBar(item.progress, Modifier.align(Alignment.BottomStart))
        }
        Box(Modifier.weight(1f)) {
            Column(Modifier.padding(end = 28.dp, top = 2.dp)) {
                AppText(rememberDeArrowTitle(item.id, item.title), size = 15, line = 20, weight = FontWeight.Medium, maxLines = 2)
                if (item.author.isNotBlank()) AppText(item.author, Modifier.padding(top = 4.dp), size = 12, line = 16,
                    color = DesignTokens.Secondary, maxLines = 1)
                if (item.metadata.isNotBlank()) AppText(item.metadata, Modifier.padding(top = 2.dp), size = 12, line = 16,
                    color = DesignTokens.Secondary, maxLines = 1)
            }
            AppIconButton(AppIcon.More, stringResource(R.string.a11y_more_actions_for, item.title),
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-8).dp), diameter = 40.dp, onClick = onMenu)
        }
    }
}

/** The duration (or LIVE, in red) in a thumbnail's corner. */
@Composable
internal fun FeedDurationBadge(item: FeedItem.Video, modifier: Modifier = Modifier) {
    Box(modifier.background(if (item.live) DesignTokens.Red else Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
        .padding(horizontal = 4.dp, vertical = 1.dp)) {
        AppText(item.duration, size = 12, line = 16, weight = FontWeight.Medium, color = Color.White)
    }
}

/** How far into the video you got, in the accent colour; nothing for an unwatched video. */
@Composable
internal fun FeedProgressBar(progress: Float?, modifier: Modifier = Modifier) {
    progress ?: return
    Box(modifier.fillMaxWidth().height(3.dp).background(Color.Black.copy(alpha = 0.5f)).testTag("feed-progress")) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(DesignTokens.Accent))
    }
}
