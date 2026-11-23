package com.hhst.youtubelite.account

import com.hhst.youtubelite.account.data.MediaSignInService
import com.hhst.youtubelite.data.media.MediaSession
import com.hhst.youtubelite.data.media.MemoryAuthStore
import com.liskovsoft.youtubeapi.videoinfo.V2.AccessToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class SignInServiceTest {
    private lateinit var store: MemoryAuthStore

    @Before fun setup() {
        store = MemoryAuthStore()
        store.write(MediaSession.KEY_CLIENT_ID, "test-client")
        store.write(MediaSession.KEY_CLIENT_SECRET, "test-secret")
        store.write(MediaSession.KEY_DEVICE_ID, "test-device")
        MediaSession.initialize(store)
    }

    @After fun cleanup() { MediaSession.initialize(MemoryAuthStore()) }

    private fun service(failProfiles: Boolean = false, beforeProfiles: () -> Unit = {}): MediaSignInService {
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val codeRequest = request.url.encodedPath.endsWith("device/code")
            if (!codeRequest) {
                assertTrue(request.url.encodedPath.endsWith("account/accounts_list"))
                beforeProfiles()
            }
            val body = when {
                codeRequest -> """{"device_code":"device","user_code":"ABCD","verification_url":"https://www.google.com/device","expires_in":300,"interval":1}"""
                failProfiles -> """{"error":{"code":503,"status":"UNAVAILABLE"}}"""
                else -> """{"contents":[{"accountItemRenderer":{"accountId":"owner",
                    "accountName":{"simpleText":"Owner"},"isSelected":true}}]}"""
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(if (!codeRequest && failProfiles) 503 else 200).message("test")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        return MediaSignInService(http, store)
    }

    @Test fun profileFailureCannotDiscardAnAuthorizedCredential() = runBlocking {
        val service = service(failProfiles = true)
        service.loadCode()
        assertTrue(service.complete(AccessToken("access", "refresh", Long.MAX_VALUE)).isEmpty())
        assertTrue(MediaSession.signedIn())
        MediaSession.initialize(store)
        assertTrue(MediaSession.signedIn())
        assertEquals("access", MediaSession.snapshot().accessToken)
    }

    @Test fun profilesFillTheCommittedSession() = runBlocking {
        // The session changes once, with the account chosen, never half signed in first.
        val service = service(beforeProfiles = { assertFalse(MediaSession.signedIn()) })
        service.loadCode()
        val accounts = service.complete(AccessToken("access", "refresh", Long.MAX_VALUE))
        assertEquals("owner", accounts.single().id)
        assertEquals("owner", MediaSession.snapshot().accountId)
    }

    @Test fun signingOutDuringDiscoveryCannotRestoreTheAccount() = runBlocking {
        val service = service(beforeProfiles = MediaSession::signOut)
        service.loadCode()
        try {
            service.complete(AccessToken("access", "refresh", Long.MAX_VALUE))
            fail("A replaced session accepted a late profile")
        } catch (_: CancellationException) {
            assertFalse(MediaSession.signedIn())
        }
    }

    @Test fun aDroppedConnectionWhileWaitingKeepsTheSameCode() = runBlocking {
        var polls = 0
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            polls++
            if (polls == 1) throw java.net.SocketTimeoutException("network switched")
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("test")
                .body("""{"access_token":"access","refresh_token":"refresh","expires_in":3600}"""
                    .toResponseBody("application/json".toMediaType())).build()
        }.build()
        val code = com.liskovsoft.youtubeapi.videoinfo.V2.SignInCode("device", "ABCD", "https://www.google.com/device",
            System.currentTimeMillis() + 60_000, 1)
        // No loadCode first: a code restored after process death resolves the stored client.
        val token = MediaSignInService(http, store).poll(code)
        assertEquals("access", token.accessToken)
        assertEquals(2, polls)
    }

    @Test fun aDroppedConnectionAfterTheCodeExpiredStillFails() = runBlocking {
        val http = OkHttpClient.Builder().addInterceptor { throw java.net.SocketTimeoutException("offline") }.build()
        val code = com.liskovsoft.youtubeapi.videoinfo.V2.SignInCode("device", "ABCD", "https://www.google.com/device",
            1_000, 1)
        try {
            MediaSignInService(http, store, now = { 2_000 }).poll(code)
            fail("An expired code kept waiting")
        } catch (_: com.hhst.youtubelite.data.media.MediaException) { }
    }
}
