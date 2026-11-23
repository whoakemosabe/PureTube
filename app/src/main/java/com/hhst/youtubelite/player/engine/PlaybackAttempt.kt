package com.hhst.youtubelite.player.engine

import com.hhst.youtubelite.diagnostics.DiagnosticContext

/**
 * Recovery belongs to one explicit play/retry across its source generations; the
 * engine's main thread owns this state and performs all IO and player changes.
 */
internal class PlaybackAttempt(val videoId: String) {
    val diagnosticRoot = DiagnosticContext(videoId = videoId)

    var lightRefreshes = 0
        private set
    var fullRefreshes = 0
        private set
    var backupAttempted = false
        private set
    var transportManifestAttempted = false
        private set
    var adaptiveRestoreAttempted = false
        private set
    var liveRejoins = 0
        private set

    /** Played time still needed before a recovered source counts as healthy again. */
    private var healthyNeededMs = 0L

    private val recoveryHistory = ArrayDeque<Map<String, Any?>>()
    val history: List<Map<String, Any?>> get() = recoveryHistory.toList()

    /** Call only after the engine has established this recovery path is eligible. */
    fun tryLightRefresh(): Boolean {
        if (lightRefreshes >= MAX_LIGHT_REFRESHES) return false
        lightRefreshes++
        recovering()
        return true
    }

    fun tryFullRefresh(): Boolean {
        if (fullRefreshes >= MAX_FULL_REFRESHES) return false
        fullRefreshes++
        recovering()
        return true
    }

    /** A failed lookup still consumes the one backup after a full refresh. */
    fun tryBackup(): Boolean {
        if (fullRefreshes == 0 || backupAttempted) return false
        backupAttempted = true
        recovering()
        return true
    }

    fun tryTransportManifest(): Boolean {
        if (transportManifestAttempted) return false
        transportManifestAttempted = true
        recovering()
        return true
    }

    /**
     * A live stream that stalls past its window rejoins at the live edge. Bounded so a
     * stream that keeps falling behind ends in the error screen, not an endless loop;
     * a minute of good playback gives the budget back like the other recoveries.
     */
    fun tryLiveRejoin(): Boolean {
        if (liveRejoins >= MAX_LIVE_REJOINS) return false
        liveRejoins++
        recovering()
        return true
    }

    /** Muxed fallback is a stopgap: grant one return to adaptive quality per attempt. */
    fun tryRestoreAdaptive(): Boolean {
        if (adaptiveRestoreAttempted) return false
        adaptiveRestoreAttempted = true
        return true
    }

    /** Marks a muxed fallback as a recovery that must prove itself before budgets return. */
    fun recovering() {
        healthyNeededMs = HEALTHY_SPAN_MS
    }

    /**
     * Feeds actually played time. Once a recovered source plays [HEALTHY_SPAN_MS], the
     * failure is over: a later, unrelated 403 or stall (a long video, an expiring URL)
     * gets the full recovery chain again instead of an error. Returns true on that reset.
     */
    fun played(ms: Long): Boolean {
        if (healthyNeededMs <= 0L || ms <= 0L) return false
        healthyNeededMs -= ms
        if (healthyNeededMs > 0L) return false
        healthyNeededMs = 0L
        lightRefreshes = 0
        fullRefreshes = 0
        backupAttempted = false
        transportManifestAttempted = false
        liveRejoins = 0
        return true
    }

    fun recordRecovery(fields: Map<String, Any?>) {
        recoveryHistory.addLast(fields.toMap())
        while (recoveryHistory.size > MAX_HISTORY) recoveryHistory.removeFirst()
    }

    private companion object {
        const val MAX_LIGHT_REFRESHES = 1
        const val MAX_FULL_REFRESHES = 1
        const val MAX_HISTORY = 6
        const val HEALTHY_SPAN_MS = 60_000L
        const val MAX_LIVE_REJOINS = 3
    }
}
