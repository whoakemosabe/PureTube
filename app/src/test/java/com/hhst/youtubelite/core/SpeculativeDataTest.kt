package com.hhst.youtubelite.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** LiTube #386: browsing Home must not download video bytes on mobile data. */
class SpeculativeDataTest {
    @Test fun meteredNetworksWarmNoMedia() = assertFalse(SpeculativeData.mediaAllowed(metered = true, dataSaver = false))
    @Test fun dataSaverWarmsNoMedia() = assertFalse(SpeculativeData.mediaAllowed(metered = false, dataSaver = true))
    @Test fun wifiWarmsMedia() = assertTrue(SpeculativeData.mediaAllowed(metered = false, dataSaver = false))
}
