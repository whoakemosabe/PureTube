package com.hhst.youtubelite.update

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks this repo's GitHub Releases for a newer puretube.apk, downloads it and hands it to the
 * system installer. The repo is public, so no token is needed. Ported from Dropi's updater.
 */
object Updater {
    const val REPO = "whoakemosabe/Puretube"
    const val REPO_URL = "https://github.com/$REPO"
    const val RELEASES_URL = "$REPO_URL/releases"

    data class Release(val version: String, val assetUrl: String, val sizeBytes: Long, val notes: List<String> = emptyList(), val behind: Int = 1)

    /**
     * The bullets from a release's notes: the hand-written "Highlights" when the release has
     * them (written for people, not developers), else the commit titles under "What's new".
     */
    fun whatsNew(body: String): List<String> {
        val sections = HashMap<String, MutableList<String>>()
        var current: String? = null
        for (line in body.lines()) {
            val t = line.trim()
            if (t.startsWith("###")) {
                current = when {
                    t.contains("Highlights", ignoreCase = true) -> "h"
                    t.contains("What's new", ignoreCase = true) -> "w"
                    else -> null
                }
                continue
            }
            if (t.startsWith("**")) current = null
            if (current != null && t.startsWith("- ")) sections.getOrPut(current) { ArrayList() } += t.removePrefix("- ").trim()
        }
        return (sections["h"] ?: sections["w"]).orEmpty().take(8)
    }

    /**
     * Everything new since [installed], newest release first: someone who skipped a few
     * versions sees all of their highlights, not just the last one's.
     */
    fun notesSince(releasesJson: String, installed: String): List<String> {
        val arr = JSONArray(releasesJson)
        val out = LinkedHashSet<String>()
        for (i in 0 until arr.length()) {
            val r = arr.getJSONObject(i)
            if (r.optBoolean("draft") || r.optBoolean("prerelease")) continue
            if (!isNewer(r.optString("tag_name"), installed)) continue
            out += whatsNew(r.optString("body"))
        }
        return out.take(10)
    }

    /** How many published releases are newer than [installed] (capped by what the page holds). */
    fun countSince(releasesJson: String, installed: String): Int {
        val arr = JSONArray(releasesJson)
        var n = 0
        for (i in 0 until arr.length()) {
            val r = arr.getJSONObject(i)
            if (!r.optBoolean("draft") && !r.optBoolean("prerelease") && isNewer(r.optString("tag_name"), installed)) n++
        }
        return n
    }

    sealed class Check {
        data object UpToDate : Check()
        data class Available(val release: Release) : Check()
        data class Failed(val message: String) : Check()
    }

    fun installedVersion(context: Context): String =
        try { context.packageManager.getPackageInfo(context.packageName, 0).versionName } catch (e: Exception) { null } ?: "0"

    /** "v4.0.12" / "4.0.12" / "4.0.12-debug" → [4, 0, 12]. */
    fun parts(v: String): List<Int> = v.trim().removePrefix("v").split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }

    fun isNewer(remote: String, local: String): Boolean {
        val a = parts(remote)
        val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    suspend fun check(context: Context): Check = withContext(Dispatchers.IO) {
        try {
            val c = open("https://api.github.com/repos/$REPO/releases/latest", "application/vnd.github+json")
            val code = c.responseCode
            if (code == 404) {
                c.disconnect()
                return@withContext Check.Failed("No releases yet.")
            }
            if (code == 401 || code == 403 || code == 429) {
                c.disconnect()
                return@withContext Check.Failed("GitHub is busy. Try again in a bit.")
            }
            if (code !in 200..299) throw IOException("GitHub returned $code")
            val json = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            c.disconnect()
            val tag = json.getString("tag_name")
            val assets = json.getJSONArray("assets")
            var asset: JSONObject? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) asset = a
            }
            val apk = asset ?: return@withContext Check.Failed("The latest release has no APK yet.")
            val installed = installedVersion(context)
            if (!isNewer(tag, installed)) return@withContext Check.UpToDate
            val notes = try {
                val r = open("https://api.github.com/repos/$REPO/releases?per_page=30", "application/vnd.github+json")
                val body = if (r.responseCode in 200..299) r.inputStream.bufferedReader().use { it.readText() } else null
                r.disconnect()
                body?.let { notesSince(it, installed) to countSince(it, installed) }
            } catch (e: Exception) { null }
            Check.Available(Release(
                tag.removePrefix("v"), apk.getString("browser_download_url"), apk.optLong("size"),
                notes?.first?.takeIf { it.isNotEmpty() } ?: whatsNew(json.optString("body")),
                behind = notes?.second?.coerceAtLeast(1) ?: 1,
            ))
        } catch (e: Exception) {
            Check.Failed("Couldn't reach GitHub.")
        }
    }

    /** Downloads the APK into the app cache, reporting progress 0..1. */
    suspend fun download(context: Context, release: Release, progress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            // GitHub answers with redirects to signed storage; follow them by hand (cross-host).
            var conn = open(release.assetUrl, "application/octet-stream").apply { instanceFollowRedirects = false }
            var hops = 0
            while (conn.responseCode in 300..399 && hops < 5) {
                val next = conn.getHeaderField("Location") ?: throw IOException("Download link missing")
                conn.disconnect()
                hops++
                conn = open(next, "application/octet-stream").apply { instanceFollowRedirects = false }
            }
            if (conn.responseCode !in 200..299) throw IOException("Download failed (${conn.responseCode})")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.sizeBytes
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val out = File(dir, "puretube.apk")
            // Written to a part file first: a half-finished download never looks ready.
            val part = File(dir, "puretube-part.apk").apply { delete() }
            var done = 0L
            conn.inputStream.use { input ->
                part.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0) progress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            conn.disconnect()
            if (total > 0 && done != total) {
                part.delete()
                throw IOException("Download incomplete ($done of $total bytes)")
            }
            verify(context, part, release.version)
            out.delete()
            if (!part.renameTo(out)) throw IOException("Couldn't save the update")
            out
        }

    /**
     * Checks the downloaded file is a whole APK of this app and the version it should be,
     * before the system installer sees it (it would only say "problem parsing the package").
     * The installer still checks the signature itself.
     */
    private fun verify(context: Context, apk: File, expected: String) {
        val info = try {
            context.packageManager.getPackageArchiveInfo(apk.path, 0)
        } catch (e: Exception) { null }
        if (info == null) {
            apk.delete()
            throw IOException("The download is damaged")
        }
        if (info.packageName != context.packageName) {
            apk.delete()
            throw IOException("The download isn't this PureTube")
        }
        val v = info.versionName.orEmpty()
        if (isNewer(expected, v) || !isNewer(v, installedVersion(context))) {
            apk.delete()
            throw IOException("The download is the wrong version ($v)")
        }
    }

    /** True if the installer opened; false if the app first needs "install unknown apps". */
    fun install(context: Context, apk: File): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        // Shares the app's existing FileProvider (download_file_paths.xml has an "updates" entry).
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.download.fileprovider", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }

    private fun open(url: String, accept: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 60_000
        c.setRequestProperty("Accept", accept)
        c.setRequestProperty("User-Agent", "Puretube-updater")
        c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        return c
    }
}
