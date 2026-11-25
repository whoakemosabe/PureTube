package com.hhst.youtubelite.tvlink

import com.hhst.youtubelite.data.media.MediaSession
import com.liskovsoft.youtubeapi.videoinfo.V2.AccessToken
import com.liskovsoft.youtubeapi.videoinfo.V2.Account
import com.liskovsoft.youtubeapi.videoinfo.V2.SignInClient

/**
 * Moves a sign-in from the phone to a paired TV. Only the refresh token and the app's client
 * credentials travel, sealed; the TV mints its own access tokens, and signing either device
 * out leaves the other signed in.
 */
internal object TvAccountLink {
    fun messageOf(handoff: MediaSession.Handoff) = TvLinkMessage.SignIn(
        clientId = handoff.client.clientId,
        clientSecret = handoff.client.clientSecret,
        deviceId = handoff.client.deviceId,
        refreshToken = handoff.refreshToken,
        accountId = handoff.accountId,
        accounts = handoff.accounts.map { TvLinkMessage.LinkAccount(it.id, it.name, it.email, it.photo, it.pageId, it.disabled) },
    )

    /** Signs this device in from [message]; false for a message that can't be a sign-in. */
    fun apply(message: TvLinkMessage.SignIn): Boolean {
        if (listOf(message.clientId, message.clientSecret, message.deviceId, message.refreshToken).any(String::isBlank)) return false
        val accounts = message.accounts.filter { it.id.isNotBlank() }.map {
            Account(it.id, it.name, it.email, it.photo, it.id == message.accountId, it.disabled, it.pageId)
        }
        // No access token yet: the first request refreshes one from the refresh token.
        return MediaSession.completeSignIn(
            SignInClient(message.clientId, message.clientSecret, message.deviceId),
            AccessToken("", message.refreshToken, 0L),
            accounts,
        )
    }

    fun info(): TvLinkMessage.AccountInfo {
        if (!MediaSession.signedIn()) return TvLinkMessage.AccountInfo()
        val account = MediaSession.selectedAccount()
        return TvLinkMessage.AccountInfo(signedIn = true, name = account?.name ?: account?.email)
    }
}
