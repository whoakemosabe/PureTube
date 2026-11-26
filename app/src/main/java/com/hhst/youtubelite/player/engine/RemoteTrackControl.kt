package com.hhst.youtubelite.player.engine

/**
 * A remote player that picks tracks itself (a Puretube TV). With one attached, the phone's
 * quality, caption and audio menus send the pick on instead of rebuilding local playback.
 */
interface RemoteTrackControl {
    fun setRemoteQuality(label: String?)
    fun setRemoteCaptions(key: String?)
    fun setRemoteAudio(key: String?)
}
