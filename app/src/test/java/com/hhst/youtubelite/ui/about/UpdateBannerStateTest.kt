package com.hhst.youtubelite.ui.about

import com.hhst.youtubelite.update.UpdateUi
import com.hhst.youtubelite.update.Updater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateBannerStateTest {
    private val release = Updater.Release("1.0.14", "https://example.com/puretube.apk", 5_000_000)

    @Test fun nothingWaitingShowsNoBanner() = assertNull(updateBannerState(null, null, UpdateUi.Idle, 0f))

    @Test fun aWaitingVersionOffersUpdate() =
        assertEquals("Update", updateBannerState("1.0.14", null, UpdateUi.Idle, 0f)!!.action)

    @Test fun downloadingShowsItsProgress() {
        val state = updateBannerState("1.0.14", null, UpdateUi.Downloading(release), 0.42f)!!
        assertEquals(0.42f, state.progress)
        assertEquals("Downloading… 42%", state.detail)
        assertNull(state.action)
    }

    @Test fun hiddenStaysHiddenUntilTheDownloadStarts() {
        assertNull(updateBannerState("1.0.14", "1.0.14", UpdateUi.Idle, 0f))
        assertEquals("Install", updateBannerState(null, "1.0.14", UpdateUi.Ready(java.io.File("x"), release), 1f)!!.action)
    }

    @Test fun aHiddenVersionComesBackForTheNextOne() =
        assertEquals("1.0.15", updateBannerState("1.0.15", "1.0.14", UpdateUi.Idle, 0f)!!.version)
}
