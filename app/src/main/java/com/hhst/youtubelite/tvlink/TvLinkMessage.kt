package com.hhst.youtubelite.tvlink

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Messages between the phone and a Puretube TV, one JSON object per line. The handshake
 * messages travel in the clear; every message after it is sealed by [SessionCipher].
 */
@Serializable
internal sealed interface TvLinkMessage {
    // Handshake.

    /** The TV speaks first, so the phone knows which TV it reached before it says anything. */
    @Serializable @SerialName("banner")
    data class Banner(val version: Int, val tvId: String, val tvName: String, val nonce: String) : TvLinkMessage

    /** The phone answers. [pairShare] is present only while pairing with a code. */
    @Serializable @SerialName("hello")
    data class Hello(
        val version: Int,
        val phoneId: String,
        val phoneName: String,
        val nonce: String,
        val pairShare: String? = null,
    ) : TvLinkMessage

    /** The TV knows this phone, or (with [pairShare]) takes the pairing attempt. */
    @Serializable @SerialName("welcome")
    data class Welcome(val pairShare: String? = null) : TvLinkMessage

    /** The TV has no key for this phone: the phone has to pair with the TV's code. */
    @Serializable @SerialName("not_paired")
    data object NotPaired : TvLinkMessage

    @Serializable @SerialName("confirm")
    data class Confirm(val mac: String) : TvLinkMessage

    @Serializable @SerialName("refused")
    data class Refused(val reason: String) : TvLinkMessage

    // Phone to TV, sealed.

    @Serializable @SerialName("load")
    data class Load(
        val videoId: String,
        val title: String,
        val author: String? = null,
        val startMs: Long = 0,
        val autoplay: Boolean = true,
    ) : TvLinkMessage

    @Serializable @SerialName("play") data object Play : TvLinkMessage
    @Serializable @SerialName("pause") data object Pause : TvLinkMessage
    @Serializable @SerialName("seek") data class Seek(val positionMs: Long) : TvLinkMessage
    @Serializable @SerialName("speed") data class Speed(val speed: Float) : TvLinkMessage
    @Serializable @SerialName("stop") data object Stop : TvLinkMessage
    @Serializable @SerialName("ping") data object Ping : TvLinkMessage

    /**
     * The phone's sign-in, handed to a paired TV over the sealed channel so the TV can show
     * Subscriptions, History and Playlists. The TV refreshes its own access tokens from it.
     */
    @Serializable @SerialName("sign_in")
    data class SignIn(
        val clientId: String,
        val clientSecret: String,
        val deviceId: String,
        val refreshToken: String,
        val accountId: String? = null,
        val accounts: List<LinkAccount> = emptyList(),
    ) : TvLinkMessage {
        override fun toString() = "SignIn(accounts=${accounts.size})"
    }

    @Serializable
    data class LinkAccount(
        val id: String,
        val name: String? = null,
        val email: String? = null,
        val photo: String? = null,
        val pageId: String? = null,
        val disabled: Boolean = false,
    )

    /** Signs the TV out; the phone stays signed in. */
    @Serializable @SerialName("sign_out") data object SignOut : TvLinkMessage

    // TV to phone, sealed.

    @Serializable @SerialName("status")
    data class Status(
        val videoId: String? = null,
        val state: String = STATE_IDLE,
        val playing: Boolean = false,
        val wantsPlay: Boolean = false,
        val positionMs: Long = 0,
        val durationMs: Long = 0,
        val bufferedMs: Long = 0,
        val speed: Float = 1f,
        val error: String? = null,
    ) : TvLinkMessage {
        companion object {
            const val STATE_IDLE = "idle"
            const val STATE_BUFFERING = "buffering"
            const val STATE_READY = "ready"
            const val STATE_ENDED = "ended"
        }
    }

    /** Who the TV is signed in as, sent on connect and after every change. */
    @Serializable @SerialName("account")
    data class AccountInfo(val signedIn: Boolean = false, val name: String? = null) : TvLinkMessage

    /** The TV remote's next or previous button: the phone owns the queue, so it picks the video. */
    @Serializable @SerialName("skip") data class Skip(val forward: Boolean) : TvLinkMessage

    companion object {
        const val VERSION = 1
        const val REFUSED_WRONG_CODE = "wrong_code"
        const val REFUSED_NEW_CODE = "new_code"
        const val REFUSED_VERSION = "version"
        const val REFUSED_PROTOCOL = "protocol"

        val json = Json {
            classDiscriminator = "t"
            ignoreUnknownKeys = true
            encodeDefaults = false
        }

        fun encode(message: TvLinkMessage): String = json.encodeToString(serializer(), message)

        /** Null for a line that is not a known message. */
        fun decodeOrNull(line: String): TvLinkMessage? = runCatching { json.decodeFromString(serializer(), line) }.getOrNull()
    }
}
