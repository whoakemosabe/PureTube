package com.hhst.youtubelite.core

import android.content.Context
import android.net.ConnectivityManager

/**
 * Bytes fetched before the user asks for them (LiTube #386). Home warmed ~4 MB of media
 * for every video the feed settled on, mobile data included, so browsing alone cost tens
 * of megabytes. Media guesses now run only on unmetered networks with Data saver off.
 */
object SpeculativeData {
    fun mediaAllowed(context: Context, dataSaver: Boolean): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        // Unknown counts as metered: a wrong guess costs the user money, not a slower start.
        val metered = connectivity?.isActiveNetworkMetered ?: true
        return mediaAllowed(metered, dataSaver)
    }

    internal fun mediaAllowed(metered: Boolean, dataSaver: Boolean): Boolean = !metered && !dataSaver
}
