package com.hhst.youtubelite.cast

/**
 * The DLNA media attributes renderers expect on a streamed resource (LiTube #369). LG webOS
 * and other strict renderers answer SetAVTransportURI with UPnP 716 when the resource has no
 * DLNA profile in its protocolInfo or the HTTP reply carries no contentFeatures/transferMode.
 */
internal object DlnaProtocol {
    /** Byte seeks (OP=01), not transcoded (CI=0), streaming transfer with background/connection stalling allowed. */
    const val FEATURES = "DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"

    /** The third field is the bare MIME; the player answer's `; codecs="..."` part puts quotes and commas there, which renderers reject. */
    fun protocolInfo(mimeType: String) = "http-get:*:${mimeType.substringBefore(';').trim().ifBlank { "video/mp4" }}:$FEATURES"

    /** Sent on every stream reply; renderers that ask with getcontentFeatures.dlna.org read it. */
    val streamHeaders = mapOf(
        "contentFeatures.dlna.org" to FEATURES,
        "transferMode.dlna.org" to "Streaming",
    )
}
