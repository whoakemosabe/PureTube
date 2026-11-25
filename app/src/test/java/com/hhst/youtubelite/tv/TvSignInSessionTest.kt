package com.hhst.youtubelite.tv

import com.hhst.youtubelite.account.data.SignInService
import com.hhst.youtubelite.account.domain.SignInUiState
import com.liskovsoft.youtubeapi.videoinfo.V2.AccessToken
import com.liskovsoft.youtubeapi.videoinfo.V2.Account
import com.liskovsoft.youtubeapi.videoinfo.V2.SignInCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TvSignInSessionTest {
    @Before fun setup() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun cleanup() = Dispatchers.resetMain()

    @Test fun closingTheScreenStopsWaitingForTheCode() {
        var stopped = false
        val service = object : SignInService {
            override suspend fun loadCode() = SignInCode("device", "ABC-DEF", "https://yt.be/activate", Long.MAX_VALUE, 1000)
            override suspend fun poll(code: SignInCode): AccessToken = try { awaitCancellation() } finally { stopped = true }
            override suspend fun complete(token: AccessToken) = emptyList<Account>()
        }
        val session = TvSignInSession(service)
        assertEquals(SignInUiState.Ready("ABC-DEF", "https://yt.be/activate"), session.model.uiState.value)
        session.close()
        assertTrue(stopped)
    }
}
