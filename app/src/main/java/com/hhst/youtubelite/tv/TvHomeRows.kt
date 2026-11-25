package com.hhst.youtubelite.tv

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.tvprovider.media.tv.PreviewChannel
import androidx.tvprovider.media.tv.PreviewChannelHelper
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import com.hhst.youtubelite.R
import com.hhst.youtubelite.tv.browse.TvCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Puretube's rows on the Google TV home screen: a "Puretube" row of Home recommendations, and
 * half-watched videos in the system's Continue watching row. Each tile opens the video here.
 */
internal object TvHomeRows {
    private const val SCHEME = "puretube-tv"
    private const val HOST = "watch"
    private const val PREFS = "puretube_tv_rows"
    private const val CHANNEL_ID = "channel_id"
    private const val CHANNEL_KEY = "puretube-home"
    private const val MAX_TILES = 20

    /** The video a home-screen tile asked for, or null for any other intent. */
    fun videoOf(intent: Intent?): String? {
        val data = intent?.data ?: return null
        if (data.scheme != SCHEME || data.host != HOST) return null
        return data.lastPathSegment?.takeIf { it.matches(VIDEO_ID) }
    }

    private fun linkOf(videoId: String): Uri = Uri.Builder().scheme(SCHEME).authority(HOST).appendPath(videoId).build()

    /** Replaces both rows with these tiles. Devices without the TV provider are skipped quietly. */
    suspend fun publish(context: Context, recommended: List<TvCard>, continueWatching: List<TvCard>) = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        runCatching { publishChannel(app, recommended.filter { it.videoId != null && !it.short }.take(MAX_TILES)) }
        runCatching { publishWatchNext(app, continueWatching.filter { it.videoId != null }.take(MAX_TILES)) }
        Unit
    }

    private fun publishChannel(context: Context, cards: List<TvCard>) {
        if (cards.isEmpty()) return
        val helper = PreviewChannelHelper(context)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var channelId = prefs.getLong(CHANNEL_ID, -1L)
        if (channelId < 0 || helper.getPreviewChannel(channelId) == null) {
            val channel = PreviewChannel.Builder()
                .setDisplayName("Puretube")
                .setDescription("Recommended for you")
                .setAppLinkIntentUri("$SCHEME://$HOST".toUri())
                .setInternalProviderId(CHANNEL_KEY)
                .setLogo(logo(context))
                .build()
            channelId = helper.publishChannel(channel)
            prefs.edit().putLong(CHANNEL_ID, channelId).apply()
            // The first channel an app adds can show without asking; later ones need the user's OK.
            TvContractCompat.requestChannelBrowsable(context, channelId)
        }
        context.contentResolver.delete(TvContractCompat.buildPreviewProgramsUriForChannel(channelId), null, null)
        cards.forEachIndexed { index, card ->
            val program = PreviewProgram.Builder()
                .setChannelId(channelId)
                .setType(TvContractCompat.PreviewPrograms.TYPE_CLIP)
                .setTitle(card.title)
                .setDescription(card.subtitle)
                .setPosterArtUri(card.artwork.toUri())
                .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9)
                .setIntentUri(linkOf(card.videoId!!))
                .setInternalProviderId(card.videoId)
                .setWeight(MAX_TILES - index)
                .build()
            helper.publishPreviewProgram(program)
        }
    }

    private fun publishWatchNext(context: Context, cards: List<TvCard>) {
        val resolver = context.contentResolver
        // Only this app's own rows can be read or removed here.
        resolver.query(TvContractCompat.WatchNextPrograms.CONTENT_URI, arrayOf(TvContractCompat.WatchNextPrograms._ID), null, null, null)?.use { rows ->
            while (rows.moveToNext()) {
                resolver.delete(ContentUris.withAppendedId(TvContractCompat.WatchNextPrograms.CONTENT_URI, rows.getLong(0)), null, null)
            }
        }
        val now = System.currentTimeMillis()
        cards.forEachIndexed { index, card ->
            val duration = durationMs(card.duration)
            val builder = WatchNextProgram.Builder()
                .setType(TvContractCompat.WatchNextPrograms.TYPE_CLIP)
                .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                .setLastEngagementTimeUtcMillis(now - index * 60_000L)
                .setTitle(card.title)
                .setDescription(card.subtitle)
                .setPosterArtUri(card.artwork.toUri())
                .setPosterArtAspectRatio(TvContractCompat.WatchNextPrograms.ASPECT_RATIO_16_9)
                .setIntentUri(linkOf(card.videoId!!))
                .setInternalProviderId(card.videoId)
            if (duration > 0) {
                builder.setDurationMillis(duration.toInt())
                builder.setLastPlaybackPositionMillis(((card.progress ?: 0f) * duration).toInt())
            }
            resolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, builder.build().toContentValues())
        }
    }

    /** "1:02:03" or "12:34" to milliseconds; 0 when it isn't a time. */
    internal fun durationMs(text: String): Long {
        val parts = text.trim().split(':').map { it.toLongOrNull() ?: return 0L }
        if (parts.isEmpty() || parts.size > 3) return 0L
        return parts.fold(0L) { total, part -> total * 60 + part } * 1000L
    }

    /** Half-watched: started, and not nearly done. */
    internal fun inProgress(card: TvCard): Boolean = (card.progress ?: 0f) in STARTED..NEARLY_DONE

    private fun logo(context: Context): Bitmap {
        val drawable = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)!!
        val bitmap = Bitmap.createBitmap(LOGO_PX, LOGO_PX, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, LOGO_PX, LOGO_PX)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    private const val LOGO_PX = 160
    private const val STARTED = 0.03f
    private const val NEARLY_DONE = 0.95f
    private val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")
}
