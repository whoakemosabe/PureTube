package com.hhst.youtubelite.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.hhst.youtubelite.account.data.SignInService
import com.hhst.youtubelite.account.domain.SignInAction
import com.hhst.youtubelite.account.domain.SignInUiState
import com.hhst.youtubelite.account.presentation.SignInViewModel

/**
 * One device-code sign-in on the TV, with the phone app's own sign-in model. It lives only
 * while the sign-in screen is open: closing it stops the polling.
 */
internal class TvSignInSession(service: SignInService) {
    private val store = ViewModelStore()
    val model: SignInViewModel = ViewModelProvider.create(store, object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SignInViewModel(service) as T
    })[SignInViewModel::class.java]

    fun close() = store.clear()
}

/** The TV sign-in: a code to type at the address shown, on any phone or computer. */
@Composable
internal fun TvSignInScreen(openSession: () -> TvSignInSession, onClose: () -> Unit) {
    val session = remember { openSession() }
    DisposableEffect(session) { onDispose { session.close() } }
    val state by session.model.uiState.collectAsStateWithLifecycle()
    val action = remember { FocusRequester() }
    LaunchedEffect(state) {
        if (state == SignInUiState.Success) onClose()
        requestFocusSoon(action)
    }
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(TvColors.Background), contentAlignment = Alignment.CenterStart) {
        Column(Modifier.padding(horizontal = 96.dp).widthIn(max = 760.dp), verticalArrangement = Arrangement.Center) {
            Text("Sign in", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = TvColors.Text)
            Spacer(Modifier.height(24.dp))
            when (val current = state) {
                SignInUiState.Loading -> Text("Getting a code from YouTube…", style = MaterialTheme.typography.titleLarge, color = TvColors.Muted)
                SignInUiState.Completing, SignInUiState.Success ->
                    Text("Finishing sign-in…", style = MaterialTheme.typography.titleLarge, color = TvColors.Muted)
                is SignInUiState.Ready -> {
                    Text(
                        "On your phone or computer, go to",
                        style = MaterialTheme.typography.titleLarge,
                        color = TvColors.Muted,
                    )
                    Text(
                        current.verificationUrl.removePrefix("https://").removePrefix("http://"),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = TvColors.Text,
                    )
                    Spacer(Modifier.height(20.dp))
                    Text("and enter this code:", style = MaterialTheme.typography.titleLarge, color = TvColors.Muted)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        current.userCode,
                        fontSize = 64.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = TvColors.Text,
                        modifier = Modifier
                            .background(TvColors.Frost, RoundedCornerShape(20.dp))
                            .padding(horizontal = 32.dp, vertical = 12.dp),
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "This screen moves on by itself once you're done. With Puretube on your phone you can also tap Cast, pick this TV and tap Use my account on the TV.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TvColors.Muted,
                    )
                    Spacer(Modifier.height(28.dp))
                    Button(onClick = onClose, modifier = Modifier.focusRequester(action)) { Text("Cancel") }
                }
                is SignInUiState.Error -> {
                    Text(current.message, style = MaterialTheme.typography.titleLarge, color = TvColors.Text)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { session.model.onAction(SignInAction.Retry) }, modifier = Modifier.focusRequester(action)) {
                        Text("Get a new code")
                    }
                }
            }
        }
    }
}
