package com.hhst.youtubelite.settings.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.ui.design.AppText
import com.hhst.youtubelite.ui.design.DesignTokens

/**
 * One Settings section: a bold red heading over a rounded card of rows. Settings is one
 * page now (Matt, 2026-11-23), so the headings are what the eye scans for.
 */
@Composable
internal fun SettingsSection(label: String, content: @Composable ColumnScope.() -> Unit) {
    SettingsGroup(label)
    Column(Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
        .background(DesignTokens.Surface), content = content)
}

@Composable
internal fun SettingsGroup(label: String) {
    BasicText(label.uppercase(),
        Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp).semantics { heading() },
        style = DesignTokens.text(13, 18, FontWeight.Bold, DesignTokens.Heading).copy(letterSpacing = 1.4.sp))
}

@Composable
internal fun SettingsToggle(
    title: String,
    summary: String,
    key: String,
    preferences: Map<String, Boolean>,
    onToggle: (String, Boolean) -> Unit,
    icon: AppIcon? = null,
) {
    val enabled = preferences[key] == true
    val haptic = com.hhst.youtubelite.ui.design.rememberHapticAction()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .toggleable(value = enabled, role = Role.Switch) { haptic(); onToggle(key, it) }
            .testTag("settings-toggle-$key").padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon)
        SettingsLabels(title, summary, Modifier.weight(1f))
        Spacer(Modifier.width(16.dp))
        com.hhst.youtubelite.ui.components.AppSwitch(enabled, onCheckedChange = null,
            modifier = Modifier.testTag("settings-toggle-state-$key"))
    }
}

@Composable
internal fun SettingsRow(
    title: String,
    summary: String? = null,
    value: String? = null,
    tag: String,
    icon: AppIcon? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .testTag(tag).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon)
        SettingsLabels(title, summary, Modifier.weight(1f))
        if (value != null) {
            Spacer(Modifier.width(16.dp))
            AppText(value, size = 14, line = 20, color = DesignTokens.Secondary)
        }
    }
}

@Composable
private fun RowIcon(icon: AppIcon?) {
    if (icon == null) return
    Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(DesignTokens.Ghost),
        contentAlignment = Alignment.Center) {
        AppIcon(icon, Modifier.size(20.dp))
    }
    Spacer(Modifier.width(14.dp))
}

@Composable
internal fun SettingsLabels(title: String, summary: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        AppText(title, size = 18, line = 24, weight = FontWeight.Medium)
        if (!summary.isNullOrBlank()) AppText(summary, Modifier.padding(top = 2.dp), size = 13, line = 18,
            color = DesignTokens.Secondary)
    }
}
