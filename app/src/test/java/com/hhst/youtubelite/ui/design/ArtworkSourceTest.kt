package com.hhst.youtubelite.ui.design

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtworkSourceTest {
    @Test fun httpsUrlsLoadAsImages() =
        assertEquals(ArtworkSource.Remote("https://yt3.ggpht.com/a"), artworkSource("https://yt3.ggpht.com/a"))

    @Test fun schemeLessAndHttpUrlsLoadOverHttps() {
        assertEquals(ArtworkSource.Remote("https://yt3.ggpht.com/a"), artworkSource("//yt3.ggpht.com/a"))
        assertEquals(ArtworkSource.Remote("https://yt3.ggpht.com/a"), artworkSource("http://yt3.ggpht.com/a"))
    }

    @Test fun onlyFixtureIdsShowSamplePictures() {
        assertEquals(ArtworkSource.Sample("sintel"), artworkSource("sintel"))
        assertEquals(ArtworkSource.Blank, artworkSource("some-unknown-value"))
        assertEquals(ArtworkSource.Blank, artworkSource(""))
    }
}
