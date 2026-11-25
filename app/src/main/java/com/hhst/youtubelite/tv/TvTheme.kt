package com.hhst.youtubelite.tv

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Text
import com.hhst.youtubelite.ui.design.AppIcon
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.darkColorScheme

/** Puretube on a TV: black, white type, red only for progress and the brand. */
internal object TvColors {
    val Background = Color(0xFF0F0F0F)
    val Red = Color(0xFFFF0033)
    val Text = Color(0xFFF1F1F1)
    val Muted = Color(0xFFAAAAAA)
    /** The frost: a light tint over the video. Real blur is too heavy for low-end TV chips. */
    val Frost = Color(0x29FFFFFF)
    val FrostEdge = Color(0x8CFFFFFF)
    val Focused = Color(0xFFF1F1F1)
    val OnFocused = Color(0xFF0F0F0F)
    val Sponsor = Color(0xFFF2C94C)
}

@Composable
internal fun TvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = TvColors.Red,
            background = TvColors.Background,
            surface = TvColors.Background,
            onSurface = TvColors.Text,
            onBackground = TvColors.Text,
        ),
        content = content,
    )
}

/** Light catching the top edge of the glass, fading out by the middle. */
private val litEdge = Brush.verticalGradient(0f to TvColors.FrostEdge, 0.5f to Color.Transparent)

/**
 * The round control button: clear, slightly frosted glass with a lit top edge and a solid
 * icon inside. Focus fills it white with a dark icon, the way the YouTube TV player does.
 */
@Composable
internal fun FrostButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    /** False inside a [FrostGroup], which already supplies the glass. */
    glass: Boolean = true,
) = FrostShell(onClick, modifier, size, glass) {
    Icon(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        modifier = Modifier.fillMaxSize().padding(size * 0.25f),
    )
}

/** The same button with one of the app's own icons; [active] (liked, subscribed, on) tints it red. */
@Composable
internal fun FrostButton(
    icon: AppIcon,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    glass: Boolean = true,
    active: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    FrostShell(onClick, modifier.onFocusChanged { focused = it.isFocused }.semantics { this.contentDescription = contentDescription },
        size, glass) {
        AppIcon(
            icon,
            Modifier.fillMaxSize().padding(size * 0.25f),
            color = if (active && !focused) TvColors.Red else LocalContentColor.current,
        )
    }
}

@Composable
private fun FrostShell(onClick: () -> Unit, modifier: Modifier, size: Dp, glass: Boolean, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(size),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (glass) TvColors.Frost else Color.Transparent,
            contentColor = TvColors.Text,
            focusedContainerColor = TvColors.Focused,
            focusedContentColor = TvColors.OnFocused,
            pressedContainerColor = TvColors.Focused,
            pressedContentColor = TvColors.OnFocused,
        ),
        border = ClickableSurfaceDefaults.border(
            border = if (glass) Border(BorderStroke(1.dp, litEdge)) else Border.None,
            focusedBorder = Border(BorderStroke(1.dp, litEdge)),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
    ) {
        content()
    }
}

/** A text pill in the same glass, for Subscribe and Description. */
@Composable
internal fun FrostPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, active: Boolean = false) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (active) TvColors.Frost else TvColors.Text.copy(alpha = 0.9f),
            contentColor = if (active) TvColors.Text else TvColors.OnFocused,
            focusedContainerColor = TvColors.Focused,
            focusedContentColor = TvColors.OnFocused,
        ),
        border = ClickableSurfaceDefaults.border(border = Border(BorderStroke(1.dp, litEdge))),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
    ) {
        Box(Modifier.fillMaxHeight().padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        }
    }
}

/** A pill of glass holding several [FrostButton]s, like the YouTube TV player's action groups. */
@Composable
internal fun FrostGroup(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier
            .background(TvColors.Frost, CircleShape)
            .border(BorderStroke(1.dp, litEdge), CircleShape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
