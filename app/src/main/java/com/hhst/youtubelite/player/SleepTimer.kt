package com.hhst.youtubelite.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What the sleep timer is set to. [Minutes] counts down; [EndOfVideo] stops when this video ends. */
sealed interface SleepChoice {
    data object Off : SleepChoice
    data class Minutes(val minutes: Int) : SleepChoice
    data object EndOfVideo : SleepChoice

    companion object {
        val choices: List<SleepChoice> = listOf(Off, Minutes(15), Minutes(30), Minutes(45), Minutes(60), EndOfVideo)
    }
}

/**
 * Stops playback after a while. [onFire] pauses; it runs once, then the timer is Off again,
 * so the next video plays normally. A countdown keeps running in the background and in
 * the mini player, because that is where people fall asleep to a video.
 */
class SleepTimer(
    private val scope: CoroutineScope,
    private val onChange: (SleepChoice) -> Unit,
    private val onFire: () -> Unit,
) {
    var choice: SleepChoice = SleepChoice.Off
        private set
    private var job: Job? = null

    fun set(next: SleepChoice) {
        job?.cancel()
        job = null
        choice = next
        if (next is SleepChoice.Minutes) job = scope.launch {
            delay(next.minutes * 60_000L)
            fire()
        }
        onChange(next)
    }

    /** The current video ended. True when the timer took the end (playback must not advance). */
    fun onVideoEnded(): Boolean {
        if (choice != SleepChoice.EndOfVideo) return false
        fire()
        return true
    }

    private fun fire() {
        job = null
        choice = SleepChoice.Off
        onChange(SleepChoice.Off)
        onFire()
    }
}
