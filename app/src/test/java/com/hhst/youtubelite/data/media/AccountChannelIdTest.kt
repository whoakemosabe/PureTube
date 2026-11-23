package com.hhst.youtubelite.data.media

import org.junit.Assert.assertEquals
import org.junit.Test

/** LiTube #390: a personal account has no page id, so its channel comes from the @handle. */
class AccountChannelIdTest {
    @Test fun brandAccountsUseTheirPageId() =
        assertEquals("UCbrand", accountChannelId("UCbrand", "@brand"))

    @Test fun personalAccountsUseTheirHandle() =
        assertEquals("@matt", accountChannelId(null, " @matt "))

    @Test fun anEmailBylineIsNotAChannel() {
        assertEquals("", accountChannelId(null, "someone@example.com"))
        assertEquals("", accountChannelId("", null))
        assertEquals("", accountChannelId(null, "@"))
    }
}
