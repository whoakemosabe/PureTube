package com.hhst.youtubelite.account.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hhst.youtubelite.account.data.SignInService
import com.hhst.youtubelite.account.domain.SignInAction
import com.hhst.youtubelite.account.domain.SignInUiState
import com.hhst.youtubelite.data.media.MediaException
import com.liskovsoft.youtubeapi.videoinfo.V2.RequestFailure
import com.liskovsoft.youtubeapi.videoinfo.V2.SignInCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Device-code sign-in. The code is kept in [SavedStateHandle], so if Android closes the app
 * while the user is entering it in the browser, coming back resumes waiting on the same code
 * instead of showing a new one.
 */
class SignInViewModel(
    private val service: SignInService,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _uiState = MutableStateFlow<SignInUiState>(SignInUiState.Loading)
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    private var job: Job? = null

    init {
        start(restored())
    }

    fun onAction(action: SignInAction) {
        when (action) {
            SignInAction.Retry -> {
                forget()
                start(null)
            }
        }
    }

    private fun start(resume: SignInCode?) {
        job?.cancel()
        _uiState.value = SignInUiState.Loading
        job = viewModelScope.launch {
            try {
                val code = resume ?: service.loadCode().also(::remember)
                _uiState.value = SignInUiState.Ready(code.userCode, code.verificationUrl)
                val token = service.poll(code)
                _uiState.value = SignInUiState.Completing
                service.complete(token)
                forget()
                _uiState.value = SignInUiState.Success
            } catch (error: CancellationException) {
                // Our own cancellation (Retry, leaving the screen) ends quietly. Any other
                // cancellation must not leave the screen spinning forever.
                currentCoroutineContext().ensureActive()
                forget()
                _uiState.value = SignInUiState.Error(messageFor(error))
            } catch (error: Exception) {
                forget()
                _uiState.value = SignInUiState.Error(messageFor(error))
            }
        }
    }

    private fun restored(): SignInCode? {
        val device = savedState.get<String>(KEY_DEVICE) ?: return null
        val code = SignInCode(device, savedState[KEY_USER] ?: return null, savedState[KEY_URL] ?: return null,
            savedState[KEY_EXPIRES] ?: return null, savedState[KEY_INTERVAL] ?: return null)
        if (code.expiresAtMs <= now()) {
            forget()
            return null
        }
        return code
    }

    private fun remember(code: SignInCode) {
        savedState[KEY_DEVICE] = code.deviceCode
        savedState[KEY_USER] = code.userCode
        savedState[KEY_URL] = code.verificationUrl
        savedState[KEY_EXPIRES] = code.expiresAtMs
        savedState[KEY_INTERVAL] = code.intervalMs
    }

    private fun forget() {
        listOf(KEY_DEVICE, KEY_USER, KEY_URL, KEY_EXPIRES, KEY_INTERVAL).forEach { savedState.remove<Any>(it) }
    }

    private fun messageFor(error: Exception): String {
        val failure = (error as? MediaException)?.failure
        return when {
            failure?.code == "expired_token" -> "The sign-in code expired. Get a new one."
            failure?.code == "access_denied" -> "Sign-in was declined in the browser."
            failure?.kind == RequestFailure.TRANSPORT -> "No connection. Check the network and try again."
            failure?.kind == RequestFailure.AUTH -> "YouTube refused the sign-in. Try again."
            else -> "Sign-in failed. Try again."
        }
    }

    private companion object {
        const val KEY_DEVICE = "sign_in:device_code"
        const val KEY_USER = "sign_in:user_code"
        const val KEY_URL = "sign_in:verification_url"
        const val KEY_EXPIRES = "sign_in:expires_at"
        const val KEY_INTERVAL = "sign_in:interval"
    }
}
