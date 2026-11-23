package com.hhst.youtubelite.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * YouTube links (LiTube #65). PureTube can't verify youtube.com, so Android 12+ only opens
 * those links here after the user turns them on in the app's "Open by default" screen.
 */
object LinkHandling {
    /** The host every YouTube link filter in the manifest shares. */
    private const val HOST = "www.youtube.com"

    /** Whether YouTube links open here; null where Android asks per link instead (before 12). */
    fun enabled(context: Context): Boolean? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val manager = context.getSystemService(DomainVerificationManager::class.java) ?: return null
        val state = runCatching { manager.getDomainVerificationUserState(context.packageName) }.getOrNull()
            ?: return null
        return state.hostToStateMap[HOST]?.let { it != DomainVerificationUserState.DOMAIN_STATE_NONE }
    }

    fun settingsIntent(context: Context): Intent {
        val uri = Uri.parse("package:${context.packageName}")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, uri)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri)
        }
    }

    /** YouTube's own account settings: Restricted Mode, privacy, notifications (LiTube #389). */
    const val YOUTUBE_SETTINGS_URL = "https://www.youtube.com/account"

    /**
     * A web page in a real browser. PureTube claims every youtube.com link, so a plain VIEW
     * intent for one would come straight back here; the browser is picked from what opens an
     * ordinary web page instead.
     */
    fun browserIntent(context: Context, url: String): Intent {
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE)
        val manager = context.packageManager
        val preferred = manager.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        val installed = manager.queryIntentActivities(probe, 0).map { it.activityInfo.packageName }
        val target = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
        pickBrowser(preferred, installed, context.packageName)?.let(target::setPackage)
        return target
    }

    /** The default browser if there is one, else the first installed one that isn't PureTube. */
    internal fun pickBrowser(preferred: String?, installed: List<String>, own: String): String? =
        preferred?.takeIf { it != own && it != "android" && it in installed }
            ?: installed.firstOrNull { it != own && it != "android" }
}
