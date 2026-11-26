package com.hhst.youtubelite.tvlink

import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import com.hhst.youtubelite.player.engine.RemoteTrackControl
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * The phone's view of a Puretube TV, as a Media3 player: the watch screen, mini player,
 * notification and queue drive it exactly like the Chromecast player. Commands go out as
 * link messages; the TV's status (once a second and on every change) comes back in [status].
 */
@UnstableApi
internal class TvRemotePlayer(
    private val send: (TvLinkMessage) -> Unit,
    looper: Looper = Looper.getMainLooper(),
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) : SimpleBasePlayer(looper), RemoteTrackControl {
    /** The TV's volume as it last reported it; the phone's volume keys move it. */
    private var volume = -1
    private var maxVolume = 0
    private var item: MediaItem? = null
    private var videoId: String? = null
    private var title = ""
    private var author: String? = null
    private var durationMs = 0L
    private var positionMs = 0L
    private var bufferedMs = 0L
    private var sampledAt = clock()
    private var state = STATE_IDLE
    private var playing = false
    private var wanted = false
    /** A play/pause the TV hasn't confirmed yet wins over its older status. */
    private var pendingPlayWhenReady: Boolean? = null
    private var repeat = REPEAT_MODE_OFF
    private var parameters = PlaybackParameters.DEFAULT
    private var error: PlaybackException? = null
    /** The TV has reported on the current video at least once since it was loaded. */
    private var confirmed = false

    fun load(videoId: String, title: String, author: String?, startMs: Long, autoplay: Boolean = true) {
        this.videoId = videoId
        this.title = title
        this.author = author
        item = MediaItem.Builder().setMediaId(videoId)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(author).build()).build()
        positionMs = startMs.coerceAtLeast(0)
        bufferedMs = positionMs
        durationMs = 0
        sampledAt = clock()
        playing = false
        wanted = autoplay
        pendingPlayWhenReady = autoplay
        error = null
        confirmed = false
        state = STATE_BUFFERING
        send(TvLinkMessage.Load(videoId, title, author, positionMs, autoplay))
        if (parameters.speed != 1f) send(TvLinkMessage.Speed(parameters.speed))
        invalidateState()
    }

    /** True when the TV stopped on its own (Back on the TV remote): the session is over. */
    fun status(status: TvLinkMessage.Status): Boolean {
        if (item == null) return false
        // An empty status before the TV took the load is just an old tick, not a stop.
        if (status.videoId == null) return confirmed
        confirmed = confirmed || status.videoId == videoId
        // A status for the previous video, sent before the TV took the new load.
        if (status.videoId != videoId) return false
        positionMs = status.positionMs.coerceAtLeast(0)
        bufferedMs = status.bufferedMs.coerceAtLeast(positionMs)
        if (status.durationMs > 0) durationMs = status.durationMs
        if (status.volume >= 0 && status.maxVolume > 0) {
            volume = status.volume
            maxVolume = status.maxVolume
        }
        sampledAt = clock()
        playing = status.playing
        val observed = if (status.state == TvLinkMessage.Status.STATE_BUFFERING && pendingPlayWhenReady != null) null else status.wantsPlay
        if (observed != null && observed == pendingPlayWhenReady) pendingPlayWhenReady = null
        wanted = pendingPlayWhenReady ?: observed ?: wanted
        state = when (status.state) {
            TvLinkMessage.Status.STATE_READY -> STATE_READY
            TvLinkMessage.Status.STATE_BUFFERING -> STATE_BUFFERING
            TvLinkMessage.Status.STATE_ENDED -> STATE_ENDED
            else -> STATE_IDLE
        }
        if (status.error != null) {
            error = PlaybackException(status.error, null, PlaybackException.ERROR_CODE_REMOTE_ERROR)
            playing = false
        }
        if (state == STATE_ENDED && repeat == REPEAT_MODE_ONE) {
            videoId?.let { load(it, title, author, 0) }
            return false
        }
        invalidateState()
        return false
    }

    fun currentVideoId(): String? = videoId

    private fun estimatedPosition(): Long {
        val drift = if (playing) ((clock() - sampledAt) * parameters.speed).toLong() else 0L
        val position = positionMs + drift
        return if (durationMs > 0) position.coerceIn(0, durationMs) else position.coerceAtLeast(0)
    }

    override fun getState(): State {
        val playlist = item?.let {
            listOf(MediaItemData.Builder(it.mediaId).setMediaItem(it)
                .setDurationUs(if (durationMs > 0) durationMs * 1000 else C.TIME_UNSET).setIsSeekable(true).build())
        }.orEmpty()
        return State.Builder().setAvailableCommands(Player.Commands.Builder().addAll(
            Player.COMMAND_PLAY_PAUSE, Player.COMMAND_PREPARE, Player.COMMAND_STOP,
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM, Player.COMMAND_GET_TIMELINE,
            Player.COMMAND_GET_METADATA, Player.COMMAND_SET_REPEAT_MODE, Player.COMMAND_SET_SPEED_AND_PITCH,
            Player.COMMAND_GET_DEVICE_VOLUME, Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS,
            Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS,
        ).build()).setPlaylist(playlist)
            // A remote device: the phone's volume keys change the TV's volume, as with a Chromecast.
            .setDeviceInfo(DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).setMaxVolume(maxVolume.coerceAtLeast(1)).build())
            .setDeviceVolume(volume.coerceIn(0, maxVolume.coerceAtLeast(1))).setPlaybackState(if (item == null) STATE_IDLE else state)
            .setPlayWhenReady(wanted, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setRepeatMode(repeat).setPlaybackParameters(parameters).setPlayerError(error)
            .setContentPositionMs(PositionSupplier { estimatedPosition() })
            .setContentBufferedPositionMs(PositionSupplier { bufferedMs.coerceAtLeast(estimatedPosition()) })
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        wanted = playWhenReady
        pendingPlayWhenReady = playWhenReady
        if (playWhenReady && state == STATE_ENDED) {
            videoId?.let { load(it, title, author, 0) }
        } else {
            send(if (playWhenReady) TvLinkMessage.Play else TvLinkMessage.Pause)
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        val position = positionMs.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0) ?: 0
        if (state == STATE_ENDED) {
            videoId?.let { load(it, title, author, position, autoplay = wanted) }
        } else {
            send(TvLinkMessage.Seek(position))
        }
        this.positionMs = position
        sampledAt = clock()
        return Futures.immediateVoidFuture()
    }

    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
        repeat = repeatMode
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlaybackParameters(playbackParameters: PlaybackParameters): ListenableFuture<*> {
        positionMs = estimatedPosition()
        sampledAt = clock()
        parameters = PlaybackParameters(playbackParameters.speed.coerceIn(0.25f, 2f))
        send(TvLinkMessage.Speed(parameters.speed))
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        send(TvLinkMessage.Stop)
        state = STATE_IDLE
        playing = false
        wanted = false
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> = Futures.immediateVoidFuture()

    override fun handleIncreaseDeviceVolume(flags: Int): ListenableFuture<*> = stepVolume(1)
    override fun handleDecreaseDeviceVolume(flags: Int): ListenableFuture<*> = stepVolume(-1)

    override fun handleSetDeviceVolume(deviceVolume: Int, flags: Int): ListenableFuture<*> {
        send(TvLinkMessage.Volume(level = deviceVolume.coerceAtLeast(0)))
        if (maxVolume > 0) volume = deviceVolume.coerceIn(0, maxVolume)
        return Futures.immediateVoidFuture()
    }

    private fun stepVolume(step: Int): ListenableFuture<*> {
        send(TvLinkMessage.Volume(step = step))
        if (maxVolume > 0 && volume >= 0) volume = (volume + step).coerceIn(0, maxVolume)
        return Futures.immediateVoidFuture()
    }

    override fun setRemoteQuality(label: String?) = send(TvLinkMessage.SetQuality(label))
    override fun setRemoteCaptions(key: String?) = send(TvLinkMessage.SetCaptions(key))
    override fun setRemoteAudio(key: String?) = send(TvLinkMessage.SetAudio(key))
}
