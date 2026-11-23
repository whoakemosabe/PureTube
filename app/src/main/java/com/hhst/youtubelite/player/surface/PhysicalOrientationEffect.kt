package com.hhst.youtubelite.player.surface

import android.app.Activity
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.OrientationEventListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Feeds physical device tilt to the player's auto-fullscreen rule. Turning the phone
 * sideways enters fullscreen and turning it upright leaves an auto-entered session, but
 * only while the system auto-rotate setting is on, so a locked rotation is respected.
 * The sensor runs only while the screen is started.
 */
@Composable
internal fun PhysicalOrientationEffect(
    activity: Activity?,
    inPip: Boolean,
    onOrientation: (degrees: Int, systemAutoRotate: Boolean, inPip: Boolean) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val pip = rememberUpdatedState(inPip)
    val callback = rememberUpdatedState(onOrientation)
    DisposableEffect(activity, lifecycleOwner) {
        val act = activity ?: return@DisposableEffect onDispose {}
        val resolver = act.contentResolver
        fun readAutoRotate() =
            Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 1
        // The listener fires at sensor rate; a Settings read per event would be a binder call each time.
        var autoRotate = readAutoRotate()
        val rotateObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                autoRotate = readAutoRotate()
            }
        }
        resolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION), true, rotateObserver)
        val listener = object : OrientationEventListener(act) {
            override fun onOrientationChanged(degrees: Int) {
                callback.value(degrees, autoRotate, pip.value)
            }
        }
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    autoRotate = readAutoRotate()
                    if (listener.canDetectOrientation()) listener.enable()
                }
                Lifecycle.Event.ON_STOP -> listener.disable()
                else -> Unit
            }
        }
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
            listener.canDetectOrientation()) listener.enable()
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            resolver.unregisterContentObserver(rotateObserver)
            listener.disable()
        }
    }
}
