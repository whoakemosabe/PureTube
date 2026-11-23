package com.hhst.youtubelite.player.surface

import kotlin.math.roundToInt

/**
 * The player's brightness swipe is a temporary override of the window's brightness. The
 * window keeps an override until it is cleared, and while it is set the system brightness
 * slider has no effect on this app, so the override must end with the player: [restore]
 * puts back what the window had before the first swipe (normally "follow the system").
 */
internal class WindowBrightness(
    private val read: () -> Float?,
    private val write: (Float) -> Unit,
) {
    private var origin: Float? = null
    /** Per-gesture level; avoids re-reading OEM-quantized window attributes. */
    private var level: Float? = null
    private var lastSet: Float? = null

    val overridden: Boolean get() = origin != null

    /** Applies one swipe step and returns the new level (0.01..1). */
    fun adjust(delta: Float): Float? {
        val windowLevel = read() ?: return null
        if (origin == null) origin = windowLevel
        val current = level ?: if (windowLevel < 0f) 0.5f else windowLevel
        val next = GestureMath.accumulateLevel(current, delta, 0.01f, 1f)
        level = next
        val snapped = (next * 100f).roundToInt() / 100f
        if (lastSet != snapped) {
            write(snapped)
            lastSet = snapped
        }
        return next
    }

    fun endGesture() {
        level = null
        lastSet = null
    }

    fun restore() {
        val before = origin ?: return
        origin = null
        endGesture()
        write(before)
    }
}
