package com.hhst.youtubelite.ui.design

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * One tab in a bottom bar. Every bar (Home, Subscriptions, You, Search and the browse pages)
 * draws its tabs through this, so spacing and the current tab look the same everywhere
 * (LiTube #397). The current tab is the accent colour with a soft glow of it falling from
 * above, like a light shining down; the others are the secondary colour. The account picture
 * replaces the icon only when there is one.
 */
@Composable
fun NavigationTabContent(label: String, icon: AppIcon, current: Boolean, avatar: String? = null) {
    val red = DesignTokens.Accent // The accent colour; red unless changed in Settings.
    val tint = if (current) red else DesignTokens.Secondary
    val glow by androidx.compose.animation.core.animateFloatAsState(if (current) 1f else 0f, label = "tab-glow")
    // Fixed size for every state, so the label never moves between bars or selections.
    Box(Modifier.size(width = 56.dp, height = 30.dp).drawBehind {
        if (glow > 0f) {
            // The light source sits above the icon; the glow fades out downwards and sideways.
            drawOval(Brush.radialGradient(
                listOf(red.copy(alpha = 0.45f * glow), red.copy(alpha = 0.12f * glow), Color.Transparent),
                center = Offset(size.width / 2f, -size.height * 0.15f), radius = size.width * 0.62f),
                topLeft = Offset(-size.width * 0.1f, -size.height * 0.6f),
                size = Size(size.width * 1.2f, size.height * 1.5f))
        }
    }, contentAlignment = Alignment.Center) {
        if (!avatar.isNullOrBlank()) {
            Artwork(avatar, Modifier.size(24.dp).clip(CircleShape)
                .then(if (current) Modifier.border(1.5.dp, red, CircleShape) else Modifier))
        } else AppIcon(icon, color = tint)
    }
    // One weight for every state: a heavier selected label shifted the text between bars.
    AppText(label, size = 11, line = 14, maxLines = 1, color = tint, weight = FontWeight.Medium)
}
