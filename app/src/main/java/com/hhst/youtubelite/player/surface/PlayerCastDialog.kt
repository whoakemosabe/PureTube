package com.hhst.youtubelite.player.surface

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.hhst.youtubelite.ui.components.AppAlertDialog as AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.hhst.youtubelite.ui.components.AppTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.hhst.youtubelite.R
import com.hhst.youtubelite.ui.theme.AppStyle
import com.hhst.youtubelite.cast.CastDevice
import com.hhst.youtubelite.tvlink.TvPairingPrompt
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.hhst.youtubelite.ui.theme.SettingsTokens
import com.hhst.youtubelite.player.PlayerUiState
import kotlinx.coroutines.delay

private const val CAST_LEARN_URL = "https://support.google.com/googlecast/answer/6102923"

@Composable
internal fun CastDialog(
    state: PlayerUiState,
    onDevice: (String) -> Unit,
    onStop: () -> Unit,
    onShareLink: () -> Unit,
    onCloseLink: () -> Unit,
    onRefresh: () -> Unit,
    onDiscovery: (Boolean) -> Unit,
    onTvCode: (String) -> Unit = {},
    onTvAddress: (String) -> Boolean = { false },
    onTvCancel: () -> Unit = {},
    onTvSignIn: () -> Unit = {},
    onTvSignOut: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    state.tvPairing?.let { prompt ->
        TvPairingDialog(prompt, onCode = onTvCode, onCancel = onTvCancel)
        return
    }
    // Discovery scanning is battery-hungry: run it only while this dialog is open;
    // the 1.5 s route re-poll covers OEM ROMs that drop discovery callbacks.
    DisposableEffect(Unit) {
        onDiscovery(true)
        onDispose { onDiscovery(false) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            onRefresh()
            delay(1_500)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppStyle.Panel,
        title = {
            Text(
                text = stringResource(R.string.cast),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (state.casting) {
                    Text(
                        text = stringResource(
                            R.string.casting_to,
                            state.castingDeviceName ?: stringResource(R.string.cast),
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = SettingsTokens.BodySize.sp,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    TextButton(onClick = onStop) {
                        Text(stringResource(R.string.cast_stop), color = PlayerUi.YtRed)
                    }
                    if (state.tvSession) TvAccountRow(state, onTvSignIn, onTvSignOut)
                }
                Text(
                    text = stringResource(R.string.cast_section_devices),
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
                if (state.castDevices.isEmpty()) {
                    Text(
                        text = stringResource(R.string.cast_searching),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = SettingsTokens.DetailSize.sp,
                    )
                    Text(
                        text = stringResource(R.string.cast_no_devices_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    LearnAboutCastingLink(
                        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                    )
                } else {
                    state.castDevices.forEach { device ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .clickable { onDevice(device.id) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(
                                    if (device.selected) R.drawable.ic_cast_connected else R.drawable.ic_cast,
                                ),
                                contentDescription = null,
                                tint = if (device.selected) {
                                    PlayerUi.YtRed
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .size(22.dp),
                            )
                            Text(
                                text = device.name,
                                color = if (device.selected) {
                                    PlayerUi.YtRed
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.weight(1f),
                            )
                            if (device.kind == CastDevice.KIND_PURETUBE) {
                                // Brand tag: this TV runs Puretube itself.
                                Text(
                                    text = "Puretube",
                                    color = PlayerUi.YtRed,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                            if (device.kind == CastDevice.KIND_DLNA) {
                                // Brand tag, not translated prose: it names the protocol.
                                Text(
                                    text = "DLNA",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }
                }
                TvAddressRow(onTvAddress)
                // Link-cast is a separate mode from a Chromecast session (the proxy
                // serves the receiver's manifest), so the link section is hidden here.
                if (!state.casting) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    Text(
                        text = stringResource(R.string.cast_section_link),
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                    Text(
                        text = stringResource(R.string.cast_section_link_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    state.castLinkUrl?.let { url ->
                        Text(
                            text = url,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (state.castLinkConnected) {
                            Text(
                                text = stringResource(R.string.cast_link_connected),
                                color = PlayerUi.YtRed,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                    Row {
                        TextButton(onClick = onShareLink) {
                            Text(stringResource(R.string.share))
                        }
                        if (state.castLinkUrl != null) {
                            TextButton(onClick = onCloseLink) {
                                Text(
                                    stringResource(R.string.cast_link_close),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
    )
}

/** Who the connected Puretube TV is signed in as, and the button to hand it this phone's sign-in. */
@Composable
private fun TvAccountRow(state: PlayerUiState, onSignIn: () -> Unit, onSignOut: () -> Unit) {
    val name = state.tvAccountName
    Text(
        text = when (state.tvSignedIn) {
            true -> stringResource(R.string.tv_account_signed_in, name ?: stringResource(R.string.tv_account_an_account))
            false -> stringResource(R.string.tv_account_signed_out)
            null -> ""
        },
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = SettingsTokens.DetailSize.sp,
        modifier = Modifier.padding(top = 4.dp),
    )
    Row {
        if (state.phoneSignedIn && state.tvSignedIn != null) {
            TextButton(onClick = onSignIn) { Text(stringResource(R.string.tv_account_use_mine)) }
        }
        if (state.tvSignedIn == true) {
            TextButton(onClick = onSignOut) { Text(stringResource(R.string.tv_account_sign_out)) }
        }
    }
}

/**
 * For Wi-Fi that hides the TV's announcement, and for telling a Puretube TV apart from the
 * same TV's built-in Chromecast: type the address Puretube's Phone screen shows on the TV.
 */
@Composable
private fun TvAddressRow(onConnect: (String) -> Boolean) {
    var open by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    if (!open) {
        TextButton(onClick = { open = true }, modifier = Modifier.padding(top = 4.dp)) {
            Text(stringResource(R.string.tv_address_open), color = PlayerUi.YtRed, fontSize = 13.sp)
        }
        return
    }
    Column(Modifier.padding(top = 8.dp)) {
        Text(stringResource(R.string.tv_address_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; wrong = false },
            singleLine = true,
            placeholder = { Text("192.168.1.20") },
            isError = wrong,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
        if (wrong) Text(stringResource(R.string.tv_address_wrong), color = PlayerUi.YtRed, fontSize = 12.sp)
        TextButton(onClick = { wrong = !onConnect(text) }) {
            Text(stringResource(R.string.tv_address_connect), color = PlayerUi.YtRed)
        }
    }
}

/** Pairing with a Puretube TV: type the six digits the TV shows. */
@Composable
private fun TvPairingDialog(prompt: TvPairingPrompt, onCode: (String) -> Unit, onCancel: () -> Unit) {
    var code by remember(prompt.deviceId) { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val submit = { if (code.length == 6 && !prompt.busy) onCode(code) }
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = AppStyle.Panel,
        title = {
            Text(
                text = stringResource(R.string.tv_pair_title, prompt.tvName),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.tv_pair_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = SettingsTokens.BodySize.sp,
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { typed -> code = typed.filter(Char::isDigit).take(6) },
                    singleLine = true,
                    enabled = !prompt.busy,
                    textStyle = MaterialTheme.typography.headlineSmall.copy(letterSpacing = 6.sp, textAlign = TextAlign.Center),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .focusRequester(focus),
                )
                prompt.error?.let {
                    Text(it, color = PlayerUi.YtRed, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = submit) {
                Text(stringResource(if (prompt.busy) R.string.tv_pair_busy else R.string.tv_pair_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun LearnAboutCastingLink(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    TextButton(
        onClick = {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, CAST_LEARN_URL.toUri()),
                )
            }
        },
        modifier = modifier,
    ) {
        Text(
            stringResource(R.string.cast_learn_casting),
        )
    }
}
