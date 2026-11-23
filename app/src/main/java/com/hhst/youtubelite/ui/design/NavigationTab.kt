package com.hhst.youtubelite.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * One tab in a bottom bar. Every bar (Home, Subscriptions, You, Search and the browse pages)
 * draws its tabs through this, so the current tab looks the same everywhere: PureTube red on
 * a faint red pill, the others in the secondary colour (LiTube #397). The account picture
 * replaces the icon only when there is one.
 */
@Composable
fun NavigationTabContent(label: String, icon: AppIcon, current: Boolean, avatar: String? = null) {
    val tint = if (current) DesignTokens.Red else DesignTokens.Secondary
    Box(Modifier.size(width = 56.dp, height = 30.dp).clip(RoundedCornerShape(15.dp))
        .background(if (current) DesignTokens.Red.copy(alpha = 0.14f) else Color.Transparent),
        contentAlignment = Alignment.Center) {
        if (!avatar.isNullOrBlank()) {
            Artwork(avatar, Modifier.size(24.dp).clip(CircleShape)
                .then(if (current) Modifier.border(1.5.dp, DesignTokens.Red, CircleShape) else Modifier))
        } else AppIcon(icon, color = tint)
    }
    AppText(label, size = 11, line = 14, maxLines = 1, color = tint,
        weight = if (current) FontWeight.SemiBold else FontWeight.Normal)
}
