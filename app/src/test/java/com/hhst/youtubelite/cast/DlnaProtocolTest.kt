package com.hhst.youtubelite.cast

import org.junit.Assert.assertEquals
import org.junit.Test

class DlnaProtocolTest {

    @Test
    fun protocolInfo_dropsTheCodecsParameterFromTheAnswerMime() {
        assertEquals(
            "http-get:*:video/mp4:${DlnaProtocol.FEATURES}",
            DlnaProtocol.protocolInfo("video/mp4; codecs=\"avc1.42001E, mp4a.40.2\""),
        )
    }
}
