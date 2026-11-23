package com.hhst.youtubelite.data.media

import com.liskovsoft.youtubeapi.videoinfo.V2.HttpRequest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** EU/EEA requests without a consent answer bounce to consent.youtube.com, breaking the Shorts feed. */
class ShortsConsentCookieTest {
    @Test fun theShortsDocumentCarriesTheConsentAnswer() {
        assertTrue(needsConsentCookie(HttpRequest("GET", "https://m.youtube.com/shorts?hl=en")))
        assertTrue(needsConsentCookie(HttpRequest("GET", "https://m.youtube.com/shorts/abc123")))
    }

    @Test fun otherRequestsAndExistingCookiesAreLeftAlone() {
        assertFalse(needsConsentCookie(HttpRequest("GET", "https://m.youtube.com/shorts", mapOf("Cookie" to "SOCS=x"))))
        assertFalse(needsConsentCookie(HttpRequest("POST", "https://m.youtube.com/shorts")))
        assertFalse(needsConsentCookie(HttpRequest("GET", "https://www.youtube.com/youtubei/v1/browse")))
        assertFalse(needsConsentCookie(HttpRequest("GET", "https://m.youtube.com/shortsfeed")))
        assertFalse(needsConsentCookie(HttpRequest("GET", "https://evil.example/shorts")))
    }
}
