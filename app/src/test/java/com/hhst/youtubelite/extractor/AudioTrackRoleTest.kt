package com.hhst.youtubelite.extractor

import com.liskovsoft.youtubeapi.videoinfo.V2.PlayerAudio
import com.liskovsoft.youtubeapi.videoinfo.V2.PlayerFormat
import org.junit.Assert.assertEquals
import org.junit.Test

/** LiTube #337/#370/#399: a dub in the viewer's language must never pass for the original track. */
class AudioTrackRoleTest {
    private fun audio(id: String, isDefault: Boolean, autoDubbed: Boolean = false, url: String = "https://r.googlevideo.com/videoplayback?itag=140") =
        PlayerFormat(140, "audio/mp4", url, null, audioTrack = PlayerAudio(id, id.substringBefore('.'), id, isDefault, autoDubbed))

    @Test fun aDubMarkedDefaultForTheViewersLanguageIsADub() =
        assertEquals("dubbed", MscPlayback.audioTrackType(audio("es.3", isDefault = true)))

    @Test fun theOriginalTrackIdIsOriginalEvenWhenAnotherTrackIsDefault() =
        assertEquals("original", MscPlayback.audioTrackType(audio("en.4", isDefault = false)))

    @Test fun autoDubbedTracksAreDubs() =
        assertEquals("dubbed", MscPlayback.audioTrackType(audio("de.10", isDefault = true, autoDubbed = true)))

    @Test fun xtagsStillWin() = assertEquals("original", MscPlayback.audioTrackType(
        audio("es.3", isDefault = false, url = "https://r.googlevideo.com/videoplayback?xtags=acont%3Doriginal:lang%3Den&itag=140".replace("%3D", "="))))

    @Test fun aSingleUnlabelledTrackFallsBackToItsDefaultFlag() =
        assertEquals("original", MscPlayback.audioTrackType(audio("", isDefault = true)))
}
