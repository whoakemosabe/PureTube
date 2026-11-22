package com.hhst.youtubelite.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.hhst.youtubelite.R
import com.hhst.youtubelite.ui.about.AboutActivity
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Where the one-tap update is, as shown in About → Check for updates. */
sealed class UpdateUi {
    data object Idle : UpdateUi()
    data object Checking : UpdateUi()
    data class UpToDate(val version: String) : UpdateUi()
    /** Found a newer version; waiting for "Download" (the What's new window asks first). */
    data class Available(val release: Updater.Release) : UpdateUi()
    data class Downloading(val release: Updater.Release) : UpdateUi()
    data class Ready(val file: File, val release: Updater.Release, val note: String? = null) : UpdateUi()
    data class Error(val message: String) : UpdateUi()
}

/**
 * Notices new releases on its own, like Dropi: a background check asks GitHub about every
 * 6 hours and posts one quiet notification per version; opening the app asks at most every
 * 10 minutes. The notification opens About, where the What's new window downloads (with
 * progress) and then installs.
 */
object UpdateWatch {
    private const val BACKGROUND_EVERY = 6 * 60 * 60_000L
    private const val OPEN_EVERY = 10 * 60_000L
    private const val CHANNEL = "updates"
    private const val NOTIFICATION_ID = 4_0_1
    private const val WORK = "puretube-update-check"
    const val EXTRA_OPEN = "puretube.open_update"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var ui by mutableStateOf<UpdateUi>(UpdateUi.Idle)
    var progress by mutableFloatStateOf(0f)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("puretube_updates", Context.MODE_PRIVATE)

    /** Called once per app start: schedules the background check and asks GitHub if due. */
    fun start(context: Context) {
        val app = context.applicationContext
        scope.launch {
            runCatching { schedule(app) }
            runCatching { checkAndNotify(app, background = false) }
        }
    }

    /** The newer version waiting, if any. */
    fun waiting(context: Context): String? {
        val v = prefs(context).getString("available", "").orEmpty()
        return v.takeIf { it.isNotBlank() && Updater.isNewer(it, Updater.installedVersion(context)) }
    }

    private fun remember(context: Context, release: Updater.Release?) {
        prefs(context).edit().putString("available", release?.version.orEmpty()).apply()
    }

    /** Asks GitHub if it's been long enough (or [force]); returns the release waiting. */
    private suspend fun check(context: Context, background: Boolean, force: Boolean = false): Updater.Release? {
        val p = prefs(context)
        val now = System.currentTimeMillis()
        val every = if (background) BACKGROUND_EVERY else OPEN_EVERY
        if (!force && now - p.getLong("last_check", 0L) < every) return null
        p.edit().putLong("last_check", now).apply()
        return when (val r = Updater.check(context)) {
            is Updater.Check.Available -> r.release.also { remember(context, it) }
            Updater.Check.UpToDate -> null.also { remember(context, null) }
            is Updater.Check.Failed -> null
        }
    }

    /** Checks, and posts one notification per new version. */
    suspend fun checkAndNotify(context: Context, background: Boolean) {
        val release = check(context, background) ?: return
        val p = prefs(context)
        if (p.getString("notified", "") == release.version) return
        p.edit().putString("notified", release.version).apply()
        post(context, release.version)
    }

    /** About → Check for updates: only checks. A newer version opens the What's new window. */
    suspend fun checkOnly(context: Context) {
        if (ui is UpdateUi.Checking || ui is UpdateUi.Downloading) return
        ui = UpdateUi.Checking
        ui = when (val r = Updater.check(context)) {
            is Updater.Check.Available -> {
                remember(context, r.release)
                UpdateUi.Available(r.release)
            }
            Updater.Check.UpToDate -> {
                remember(context, null)
                UpdateUi.UpToDate(Updater.installedVersion(context))
            }
            is Updater.Check.Failed -> UpdateUi.Error(r.message)
        }
    }

    /** "Not now" in the What's new window: back to idle; the notification still offers it. */
    fun notNow() {
        if (ui is UpdateUi.Available || ui is UpdateUi.Ready || ui is UpdateUi.Error) ui = UpdateUi.Idle
    }

    private suspend fun download(context: Context, release: Updater.Release) {
        ui = UpdateUi.Downloading(release)
        progress = 0f
        ui = try {
            UpdateUi.Ready(Updater.download(context, release) { progress = it }, release)
        } catch (e: Exception) {
            UpdateUi.Error("Download failed. Tap Check for updates to try again.")
        }
    }

    /** The window's main button: Download when available, Install once downloaded. */
    suspend fun tap(context: Context) {
        when (val st = ui) {
            is UpdateUi.Available -> download(context, st.release)
            is UpdateUi.Ready -> if (!Updater.install(context, st.file)) {
                ui = st.copy(note = "Allow Puretube to install updates, then tap Install again.")
            }
            else -> {}
        }
    }

    private fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun post(context: Context, version: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager?.getNotificationChannel(CHANNEL) == null) {
                manager?.createNotificationChannel(
                    NotificationChannel(CHANNEL, "App updates", NotificationManager.IMPORTANCE_LOW)
                        .apply { description = "When a new Puretube version is out" })
            }
        }
        val open = PendingIntent.getActivity(context, 0,
            Intent(context, AboutActivity::class.java).putExtra(EXTRA_OPEN, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Puretube $version is available")
            .setContentText("Tap to see what's new and update.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n) }
    }
}

class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { UpdateWatch.checkAndNotify(applicationContext, background = true) }
        return Result.success()
    }
}
