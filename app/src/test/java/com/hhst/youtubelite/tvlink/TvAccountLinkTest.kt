package com.hhst.youtubelite.tvlink

import com.hhst.youtubelite.data.media.MediaSession
import com.hhst.youtubelite.data.media.MemoryAuthStore
import com.liskovsoft.youtubeapi.videoinfo.V2.AccessToken
import com.liskovsoft.youtubeapi.videoinfo.V2.Account
import com.liskovsoft.youtubeapi.videoinfo.V2.SignInClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TvAccountLinkTest {
    private lateinit var store: MemoryAuthStore

    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        store = MemoryAuthStore()
        MediaSession.initialize(store)
    }

    @After fun cleanup() {
        MediaSession.initialize(MemoryAuthStore())
        Dispatchers.resetMain()
    }

    private fun signInPhone() {
        MediaSession.completeSignIn(
            SignInClient("client", "secret", "device"),
            AccessToken("access", "refresh", Long.MAX_VALUE),
            listOf(Account("a1", "Matt", null, null, false, false, null), Account("a2", "Brand", null, null, true, false, "page")),
        )
    }

    @Test fun theTvSignsInAsThePhonesSelectedAccountWithoutItsAccessToken() {
        signInPhone()
        val message = TvAccountLink.messageOf(checkNotNull(MediaSession.handoff()))
        assertEquals("refresh", message.refreshToken)
        assertEquals("a2", message.accountId)

        // The TV starts signed out, on its own store.
        val tvStore = MemoryAuthStore()
        MediaSession.initialize(tvStore)
        assertTrue(TvAccountLink.apply(message))
        // Signed in for real once the first refresh mints an access token (network, not here).
        assertEquals("Brand", MediaSession.selectedAccount()?.name)
        assertEquals("refresh", tvStore.values[REFRESH])
        assertEquals("", tvStore.values[ACCESS].orEmpty())
        assertEquals("a2", tvStore.values[ACCOUNT])
    }

    @Test fun aBlankRefreshTokenIsRefused() {
        val message = TvLinkMessage.SignIn("client", "secret", "device", refreshToken = " ")
        assertFalse(TvAccountLink.apply(message))
        assertFalse(MediaSession.signedIn())
        assertEquals(TvLinkMessage.AccountInfo(), TvAccountLink.info())
    }

    @Test fun nothingIsHandedOffWhenSignedOutOrInOnThisDeviceMode() {
        assertNull(MediaSession.handoff())
        signInPhone()
        MediaSession.setAnonymousMode(true)
        assertNull(MediaSession.handoff())
    }

    @Test fun theSignInSurvivesTheWireAndNeverPrintsItsToken() {
        signInPhone()
        val message = TvAccountLink.messageOf(MediaSession.handoff()!!)
        assertEquals(message, TvLinkMessage.decodeOrNull(TvLinkMessage.encode(message)))
        assertFalse(message.toString().contains("refresh"))
        assertFalse(MediaSession.handoff().toString().contains("refresh"))
    }

    private companion object {
        const val REFRESH = "auth:refresh_token"
        const val ACCESS = "auth:access_token"
        const val ACCOUNT = "auth:account_id"
    }
}
