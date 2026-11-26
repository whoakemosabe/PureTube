package com.hhst.youtubelite.tv

import android.text.format.DateFormat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.hhst.youtubelite.ui.design.AppIcon
import java.util.Date
import kotlinx.coroutines.delay

/**
 * The one look every TV menu shares: the player's side panels, the Settings page and the
 * pop-up menus. A dark solid card, a header with a small line above the title, and rows
 * that turn white when focused, with an icon on the left and the current value on the right.
 */
internal object TvMenu {
    val Panel = Color(0xFF1B1B1B)
    val Card = Color(0xFF242424)
    val Hairline = Color(0x1AFFFFFF)
    val Scrim = Color(0x8C000000)
    val PanelShape = RoundedCornerShape(28.dp)
    val CardShape = RoundedCornerShape(20.dp)
    val RowShape = RoundedCornerShape(14.dp)
    val RowHeight = 60.dp
}

/** A menu's title, with an optional small line above it (where it opened from). */
@Composable
internal fun TvMenuHeader(title: String, eyebrow: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 16.dp)) {
        if (!eyebrow.isNullOrBlank()) {
            Text(eyebrow, style = MaterialTheme.typography.labelLarge, color = TvColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = TvColors.Text,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(TvMenu.Hairline))
    }
}

/**
 * One menu row. [icon] leads; a picked row ([checked]) shows a check there instead when it
 * has no icon. [value] sits on the right, and [opens] adds an arrow for rows that open
 * another menu. [trailing] replaces the value, for a switch.
 */
@Composable
internal fun TvMenuRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: AppIcon? = null,
    summary: String? = null,
    value: String? = null,
    checked: Boolean = false,
    checkable: Boolean = false,
    opens: Boolean = false,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = TvMenu.RowHeight),
        shape = ClickableSurfaceDefaults.shape(TvMenu.RowShape),
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
        val ink = LocalContentColor.current
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                icon != null -> {
                    AppIcon(icon, Modifier.size(24.dp), color = ink)
                    Spacer(Modifier.width(16.dp))
                }
                checkable -> {
                    Box(Modifier.size(24.dp)) { if (checked) AppIcon(AppIcon.Check, Modifier.size(24.dp), color = ink) }
                    Spacer(Modifier.width(16.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!summary.isNullOrBlank()) {
                    Text(summary, style = MaterialTheme.typography.bodyMedium, color = ink.copy(alpha = 0.7f),
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(16.dp))
                trailing()
            } else if (!value.isNullOrBlank()) {
                Spacer(Modifier.width(16.dp))
                Text(value, style = MaterialTheme.typography.bodyLarge, color = ink.copy(alpha = 0.72f), maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp))
            }
            if (opens) {
                Spacer(Modifier.width(8.dp))
                AppIcon(AppIcon.ChevronDown, Modifier.size(20.dp).rotate(-90f), color = ink.copy(alpha = 0.72f))
            }
        }
    }
}

/** A plain on/off pill; it follows the row's focus colours. */
@Composable
internal fun TvSwitch(on: Boolean) {
    val ink = LocalContentColor.current
    Box(
        Modifier
            .size(width = 44.dp, height = 24.dp)
            .background(if (on) TvColors.Red else ink.copy(alpha = 0.3f), androidx.compose.foundation.shape.CircleShape)
            .padding(3.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(18.dp).background(Color.White, androidx.compose.foundation.shape.CircleShape))
    }
}

/** Whether the clock is dimmed: the player dims it while its controls are hidden. */
internal val LocalClockDimmed = compositionLocalOf<MutableState<Boolean>> { mutableStateOf(false) }

/** The time, top right, on every screen. It fades a little while a video plays with no controls. */
@Composable
internal fun TvClock(dimmed: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            // Wake on the next minute, so the time changes when the minute does.
            delay(MINUTE_MS - now % MINUTE_MS + 50)
        }
    }
    val text = remember(now) { DateFormat.getTimeFormat(context).format(Date(now)) }
    val alpha by animateFloatAsState(if (dimmed) DIMMED_ALPHA else 1f, tween(CLOCK_FADE_MS), label = "clock")
    Text(
        text,
        modifier = modifier.alpha(alpha),
        color = TvColors.Text,
        style = MaterialTheme.typography.titleLarge.merge(
            TextStyle(fontWeight = FontWeight.Medium, shadow = Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 8f)),
        ),
    )
}

private const val MINUTE_MS = 60_000L
private const val DIMMED_ALPHA = 0.45f
private const val CLOCK_FADE_MS = 600
