package com.hhst.youtubelite.player.engine

/**
 * The Settings switch "Play next Short automatically" and a Short's own Playback order
 * menu are two views of one value: the remembered Shorts loop mode. The switch only
 * says whether that mode is Play next, so each side writes only when they disagree.
 */
internal object ShortsAdvance {
    /** The Shorts loop mode to store after the switch changed, or null to keep [current]. */
    fun modeForSwitch(enabled: Boolean, current: LoopMode?): LoopMode? = when {
        enabled && current != LoopMode.QUEUE_NEXT -> LoopMode.QUEUE_NEXT
        // Off keeps Pause at end; only Play next has to become the Shorts default, Loop.
        !enabled && current == LoopMode.QUEUE_NEXT -> LoopMode.LOOP_ONE
        else -> null
    }

    fun switchFor(mode: LoopMode): Boolean = mode == LoopMode.QUEUE_NEXT
}
