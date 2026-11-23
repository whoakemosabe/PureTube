package com.hhst.youtubelite.account.data

import com.hhst.youtubelite.data.media.AuthStore
import com.hhst.youtubelite.data.media.MediaException
import com.hhst.youtubelite.data.media.MediaClient
import com.hhst.youtubelite.data.media.MediaSession
import com.hhst.youtubelite.data.media.requireValue
import com.liskovsoft.youtubeapi.videoinfo.V2.AccessToken
import com.liskovsoft.youtubeapi.videoinfo.V2.Account
import com.liskovsoft.youtubeapi.videoinfo.V2.HttpClient
import com.liskovsoft.youtubeapi.videoinfo.V2.RequestContext
import com.liskovsoft.youtubeapi.videoinfo.V2.RequestFailure
import com.liskovsoft.youtubeapi.videoinfo.V2.SignInClient
import com.liskovsoft.youtubeapi.videoinfo.V2.SignInCode
import com.liskovsoft.youtubeapi.videoinfo.V2.loadAccounts
import com.liskovsoft.youtubeapi.videoinfo.V2.loadSignInCode
import com.liskovsoft.youtubeapi.videoinfo.V2.loadSignInCredentials
import com.liskovsoft.youtubeapi.videoinfo.V2.pollSignIn
import java.util.UUID

/** Device-code sign-in steps; credentials themselves are only ever entered in the browser. */
interface SignInService {
    suspend fun loadCode(): SignInCode
    suspend fun poll(code: SignInCode): AccessToken
    suspend fun complete(token: AccessToken): List<Account>
}

class MediaSignInService(
    http: okhttp3.OkHttpClient,
    private val store: AuthStore,
    private val now: () -> Long = System::currentTimeMillis,
) : SignInService {
    private val client = MediaClient(http)
    private var resolved: SignInClient? = null

    override suspend fun loadCode(): SignInCode = client.read { _, http ->
        val signInClient = resolveClient(http)
        resolved = signInClient
        loadSignInCode(signInClient, http).requireValue()
    }

    /**
     * Waits for the browser confirmation. A dropped connection (Wi-Fi to mobile data while the
     * user is in the browser) only pauses the wait; the same code stays valid until it expires.
     * A code restored after process death resolves the install's stored client again.
     */
    override suspend fun poll(code: SignInCode): AccessToken {
        while (true) {
            try {
                return client.read { _, http ->
                    val signInClient = resolved ?: resolveClient(http).also { resolved = it }
                    pollSignIn(code, signInClient, http).requireValue()
                }
            } catch (error: MediaException) {
                if (error.failure.kind != RequestFailure.TRANSPORT || now() >= code.expiresAtMs) throw error
                kotlinx.coroutines.delay(code.intervalMs.coerceIn(MIN_RETRY_MS, MAX_RETRY_MS))
            }
        }
    }

    /**
     * Reads the accounts with the new token first, then commits the sign-in once with the
     * account already chosen, so screens see a single identity change instead of a
     * half-signed-in session followed by a second switch.
     */
    override suspend fun complete(token: AccessToken): List<Account> {
        val signInClient = resolved ?: error("poll must succeed before complete")
        // A sign-out or another sign-in while the accounts load wins over this one.
        val generation = MediaSession.snapshot().generation
        val accounts = try {
            client.read { _, http ->
                loadAccounts(RequestContext(accessToken = token.accessToken), http).requireValue()
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // The library reloads the account list later; the sign-in itself still stands.
            com.hhst.youtubelite.diagnostics.AppLog.event(
                com.hhst.youtubelite.diagnostics.AppLog.Category.APP, "sign_in.accounts_unavailable", failure = error)
            emptyList()
        }
        if (!MediaSession.completeSignIn(signInClient, token, accounts, generation))
            throw kotlinx.coroutines.CancellationException("Session changed")
        return accounts
    }

    private fun resolveClient(http: HttpClient): SignInClient {
        val id = store.read(MediaSession.KEY_CLIENT_ID)
        val secret = store.read(MediaSession.KEY_CLIENT_SECRET)
        val credentials = if (!id.isNullOrBlank() && !secret.isNullOrBlank()) {
            com.liskovsoft.youtubeapi.videoinfo.V2.SignInCredentials(id, secret)
        } else {
            loadSignInCredentials(http).requireValue().also {
                store.write(MediaSession.KEY_CLIENT_ID, it.clientId)
                store.write(MediaSession.KEY_CLIENT_SECRET, it.clientSecret)
            }
        }
        val device = store.read(MediaSession.KEY_DEVICE_ID)?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString().also { store.write(MediaSession.KEY_DEVICE_ID, it) }
        return SignInClient(credentials.clientId, credentials.clientSecret, device)
    }

    private companion object {
        const val MIN_RETRY_MS = 1_000L
        const val MAX_RETRY_MS = 10_000L
    }
}
