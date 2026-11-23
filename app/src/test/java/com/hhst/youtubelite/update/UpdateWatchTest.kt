package com.hhst.youtubelite.update

import android.content.Context
import android.content.ContextWrapper
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateWatchTest {
    private val context = object : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
    }
    private val release = Updater.Release("v1.0.99", "https://example.invalid/puretube.apk", 1)

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After fun tearDown() {
        UpdateWatch.downloader = { c, r, p -> Updater.download(c, r, p) }
        UpdateWatch.ui = UpdateUi.Idle
        Dispatchers.resetMain()
    }

    // Closing the What's new window (or scrolling the Home banner away) used to cancel the
    // download, leaving "Downloading" on screen with nothing running.
    @Test fun aDownloadKeepsGoingAfterTheWindowCloses() {
        val file = CompletableDeferred<File>()
        UpdateWatch.downloader = { _, _, progress -> progress(0.5f); file.await() }
        UpdateWatch.ui = UpdateUi.Available(release)
        UpdateWatch.tap(context)
        assertTrue(UpdateWatch.ui is UpdateUi.Downloading)
        assertEquals(0.5f, UpdateWatch.progress)
        UpdateWatch.notNow()
        val apk = File("puretube.apk")
        file.complete(apk)
        assertEquals(UpdateUi.Ready(apk, release), UpdateWatch.ui)
    }

    @Test fun aFailedDownloadOffersARetryInsteadOfStickingOnDownloading() {
        UpdateWatch.downloader = { _, _, _ -> throw java.io.IOException("reset") }
        UpdateWatch.ui = UpdateUi.Available(release)
        UpdateWatch.tap(context)
        assertTrue(UpdateWatch.ui is UpdateUi.Error)
    }
}
