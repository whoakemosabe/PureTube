package com.hhst.youtubelite.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserPickTest {
    private val own = "com.whoakemosabe.puretube"

    @Test fun theDefaultBrowserWins() =
        assertEquals("org.mozilla.firefox", LinkHandling.pickBrowser("org.mozilla.firefox",
            listOf("com.android.chrome", "org.mozilla.firefox"), own))

    @Test fun withNoDefaultTheFirstOtherBrowserOpensIt() =
        assertEquals("com.android.chrome", LinkHandling.pickBrowser("android", listOf(own, "com.android.chrome"), own))

    @Test fun puretubeIsNeverPicked() = assertNull(LinkHandling.pickBrowser(own, listOf(own), own))
}
