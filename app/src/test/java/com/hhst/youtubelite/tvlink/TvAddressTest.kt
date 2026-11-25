package com.hhst.youtubelite.tvlink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvAddressTest {
    @Test fun anAddressAloneUsesTheDefaultPort() {
        assertEquals("192.168.1.20" to TvLinkController.DEFAULT_PORT, TvLinkController.parseAddress(" 192.168.1.20 "))
        assertEquals("10.0.0.5" to 5000, TvLinkController.parseAddress("10.0.0.5:5000"))
    }

    @Test fun anythingElseIsRefused() {
        assertNull(TvLinkController.parseAddress("living room"))
        assertNull(TvLinkController.parseAddress("300.1.1.1"))
        assertNull(TvLinkController.parseAddress("10.0.0.5:99999"))
        assertNull(TvLinkController.parseAddress(""))
    }
}
