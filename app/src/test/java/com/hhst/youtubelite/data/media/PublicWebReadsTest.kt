package com.hhst.youtubelite.data.media

import com.liskovsoft.youtubeapi.videoinfo.V2.RequestContext
import com.liskovsoft.youtubeapi.videoinfo.V2.WebSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PublicWebReadsTest {
    @Test fun anOauthSignInReadsPublicWebResults() {
        val oauth = RequestContext(accountId = "account", accessToken = "token", visitor = "visitor", generation = 3)
        val read = oauth.forPublicWebRead()
        assertNull(read.accessToken)
        assertNull(read.accountId)
        assertEquals("visitor", read.visitor)
        assertEquals(3, read.generation)
    }

    @Test fun aCookieSignInAndAnAnonymousSessionAreUnchanged() {
        val cookie = RequestContext(accountId = "web", webSession = WebSession("SAPISID=one"))
        assertEquals(cookie, cookie.forPublicWebRead())
        val anonymous = RequestContext(visitor = "visitor")
        assertEquals(anonymous, anonymous.forPublicWebRead())
    }
}
