package com.hhst.youtubelite.tv

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/** Whether this device is a TV. The same APK runs on phones and Google TV. */
internal object TvDevice {
    fun isTelevision(context: Context): Boolean {
        val mode = context.getSystemService(UiModeManager::class.java)?.currentModeType
        return mode == Configuration.UI_MODE_TYPE_TELEVISION ||
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }
}
