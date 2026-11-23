package com.hhst.youtubelite.watch

import com.hhst.youtubelite.extension.PreferenceKeys
import org.junit.Assert.assertEquals
import org.junit.Test

class HideRelatedTest {
    @Test fun relatedVideosShowByDefault() =
        assertEquals(false, PreferenceKeys.DEFAULTS[PreferenceKeys.ENABLE_HIDE_RELATED])
}
