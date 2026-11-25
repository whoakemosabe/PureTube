package com.hhst.youtubelite.tvlink

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.hhst.youtubelite.cast.CastDevice
import com.hhst.youtubelite.cast.LocalCastNetwork
import com.hhst.youtubelite.cast.protocol.CastV2Discovery
import com.hhst.youtubelite.data.media.MediaSession
import com.hhst.youtubelite.diagnostics.AppLog
import com.hhst.youtubelite.tv.TvLinkServer
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** The code prompt while pairing with a TV, shown in the cast dialog. */
data class TvPairingPrompt(val deviceId: String, val tvName: String, val error: String? = null, val busy: Boolean = false)

data class TvLinkPhoneState(
    val available: Boolean = false,
    val session: Boolean = false,
    val deviceName: String? = null,
    val devices: List<CastDevice> = emptyList(),
    val pairing: TvPairingPrompt? = null,
    /** The connected TV's sign-in, once it has said; null while unknown. */
    val tvSignedIn: Boolean? = null,
    val tvAccountName: String? = null,
)

/**
 * Finds Puretube TVs on the Wi-Fi, pairs with them, and keeps one connection while the
 * phone plays on a TV. Everything the TV needs is the video id and a position: it fetches
 * and plays the video itself.
 */
@UnstableApi
class TvLinkController internal constructor(
    context: Context,
    private val store: TvLinkStore,
) {
    constructor(context: Context) : this(context, AndroidTvLinkStore(context, "phone"))

    sealed interface ConnectResult {
        data object Connected : ConnectResult
        data object NeedsCode : ConnectResult
        data class WrongCode(val newCode: Boolean) : ConnectResult
        data class Failed(val reason: String) : ConnectResult
    }

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(TvLinkPhoneState())
    val state: StateFlow<TvLinkPhoneState> = _state.asStateFlow()

    private data class Endpoint(val name: String, val host: String, val port: Int)
    private val endpoints = linkedMapOf<String, Endpoint>()
    private var discovery: CastV2Discovery? = null
    private var discovering = false
    private var selectedId: String? = null

    private class Connection(val socket: Socket, val channel: SecureChannel)
    @Volatile private var connection: Connection? = null
    private var player: TvRemotePlayer? = null
    private var pinger: ScheduledExecutorService? = null
    /** Sends never block the main thread; one writer keeps frames in order. */
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "TvLinkSend").apply { isDaemon = true } }

    /** The TV went away or stopped playing on its own (Back on the TV remote). */
    var onSessionEnded: (() -> Unit)? = null
    /** Next or previous pressed on the TV remote. */
    var onSkip: ((forward: Boolean) -> Unit)? = null

    fun initialize() {
        if (discovery == null) discovery = CastV2Discovery(appContext, TvLinkServer.SERVICE_TYPE, requireVideoOut = false)
    }

    fun setDiscoveryEnabled(enabled: Boolean) {
        val discovery = discovery ?: return
        if (enabled == discovering) return
        discovering = enabled
        if (enabled) {
            discovery.start { name, host, port ->
                endpoints[ID_PREFIX + "$host:$port"] = Endpoint(name, host, port)
                publishDevices()
            }
        } else discovery.stop()
    }

    fun remotePlayer(): Player? = player.takeIf { connection != null }

    fun selectedName(): String? = selectedId?.let { endpoints[it]?.name }

    /** Connects to [deviceId]; with [code] it pairs first. Runs off the main thread. */
    suspend fun connect(deviceId: String, code: String? = null): ConnectResult {
        val endpoint = endpoints[deviceId] ?: return ConnectResult.Failed("The TV is no longer on this Wi-Fi")
        selectedId = deviceId
        if (code != null) _state.update { it.copy(pairing = it.pairing?.copy(busy = true, error = null)) }
        val opened = withContext(Dispatchers.IO) { open(endpoint, code) }
        when (val result = opened.result) {
            is PhoneHandshake.Result.Connected -> {
                val connected = Connection(checkNotNull(opened.socket), result.channel)
                withContext(Dispatchers.Main) { attach(connected, result.tvName) }
                return ConnectResult.Connected
            }
            is PhoneHandshake.Result.NeedsCode -> {
                _state.update { it.copy(pairing = TvPairingPrompt(deviceId, result.tvName)) }
                return ConnectResult.NeedsCode
            }
            is PhoneHandshake.Result.Refused -> {
                val wrong = result.reason == TvLinkMessage.REFUSED_WRONG_CODE || result.reason == TvLinkMessage.REFUSED_NEW_CODE
                if (!wrong) return failed("The TV refused the connection (${result.reason})")
                val newCode = result.reason == TvLinkMessage.REFUSED_NEW_CODE
                _state.update {
                    it.copy(pairing = TvPairingPrompt(deviceId, endpoint.name,
                        error = if (newCode) "The TV shows a new code now. Type that one." else "That code didn't match the TV. Try again."))
                }
                return ConnectResult.WrongCode(newCode)
            }
            is PhoneHandshake.Result.Failed -> return failed("Couldn't reach the TV (${result.reason})")
        }
    }

    fun cancelPairing() = _state.update { it.copy(pairing = null) }

    /** Signs the connected TV in as this phone's account. False when there is nothing to send. */
    fun sendSignIn(): Boolean {
        if (connection == null) return false
        val handoff = MediaSession.handoff() ?: return false
        send(TvAccountLink.messageOf(handoff))
        return true
    }

    fun signOutTv() {
        if (connection != null) send(TvLinkMessage.SignOut)
    }

    /** Plays [videoId] on the connected TV from [positionMs]. */
    fun startCasting(videoId: String, title: String, author: String?, positionMs: Long, autoplay: Boolean = true): Boolean {
        val remote = player ?: return false
        if (connection == null) return false
        remote.load(videoId, title, author, positionMs, autoplay)
        return true
    }

    /** Stops the TV and returns the last position it reported. */
    fun stopCasting(): Long {
        val remote = player ?: return 0L
        val position = remote.currentPosition
        if (connection != null) send(TvLinkMessage.Stop)
        return position
    }

    fun endSession() {
        val current = connection
        connection = null
        pinger?.shutdownNow()
        pinger = null
        current?.let { runCatching { it.socket.close() } }
        player?.release()
        player = null
        selectedId = null
        _state.update { it.copy(session = false, deviceName = null, tvSignedIn = null, tvAccountName = null) }
        publishDevices()
    }

    private fun open(endpoint: Endpoint, code: String?): OpenResult {
        val socket = Socket()
        return try {
            LocalCastNetwork.select(appContext.getSystemService(ConnectivityManager::class.java), endpoint.host)
                ?.bindSocket(socket)
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(endpoint.host, endpoint.port), CONNECT_TIMEOUT_MS)
            socket.soTimeout = HANDSHAKE_TIMEOUT_MS
            val lines = LineChannel(socket.getInputStream(), socket.getOutputStream()) { socket.close() }
            val result = PhoneHandshake(store, ::phoneName).run(lines, code)
            // The TV sends status every second; a long silence means it is gone.
            if (result is PhoneHandshake.Result.Connected) socket.soTimeout = READ_TIMEOUT_MS
            OpenResult(result, socket.takeIf { result is PhoneHandshake.Result.Connected })
        } catch (error: IOException) {
            runCatching { socket.close() }
            OpenResult(PhoneHandshake.Result.Failed(error.javaClass.simpleName))
        }
    }

    private fun attach(connected: Connection, tvName: String) {
        endSessionQuietly()
        connection = connected
        player = TvRemotePlayer(::send)
        _state.update { it.copy(session = true, deviceName = tvName, pairing = null) }
        publishDevices()
        AppLog.event(AppLog.Category.CAST, "tv_link_connected")
        Thread({ readLoop(connected) }, "TvLinkRead").apply { isDaemon = true }.start()
        pinger = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "TvLinkPing").apply { isDaemon = true } }.also {
            it.scheduleWithFixedDelay({ send(TvLinkMessage.Ping) }, PING_MS, PING_MS, TimeUnit.MILLISECONDS)
        }
    }

    private fun endSessionQuietly() {
        connection?.let { runCatching { it.socket.close() } }
        connection = null
        pinger?.shutdownNow()
        pinger = null
        player?.release()
        player = null
    }

    private fun readLoop(connected: Connection) {
        try {
            while (true) {
                val message = connected.channel.receive() ?: break
                main.post {
                    if (connection !== connected) return@post
                    when (message) {
                        is TvLinkMessage.Status -> if (player?.status(message) == true) onSessionEnded?.invoke()
                        is TvLinkMessage.Skip -> onSkip?.invoke(message.forward)
                        is TvLinkMessage.AccountInfo -> _state.update {
                            it.copy(tvSignedIn = message.signedIn, tvAccountName = message.name?.take(MAX_ACCOUNT_NAME))
                        }
                        else -> Unit
                    }
                }
            }
        } catch (_: IOException) {
            // Closed, timed out, or a frame that didn't open.
        }
        main.post {
            if (connection === connected) {
                AppLog.event(AppLog.Category.CAST, "tv_link_lost")
                onSessionEnded?.invoke()
            }
        }
    }

    private fun send(message: TvLinkMessage) {
        val current = connection ?: return
        runCatching {
            writer.execute {
                try {
                    current.channel.send(message)
                } catch (_: IOException) {
                    runCatching { current.socket.close() }
                }
            }
        }
    }

    private fun failed(reason: String): ConnectResult {
        _state.update { it.copy(pairing = null) }
        return ConnectResult.Failed(reason)
    }

    private fun publishDevices() {
        _state.update { current ->
            current.copy(
                available = endpoints.isNotEmpty(),
                devices = endpoints.map { (id, endpoint) ->
                    CastDevice(id, endpoint.name, selected = id == selectedId && current.session, kind = CastDevice.KIND_PURETUBE)
                },
            )
        }
    }

    private fun phoneName(): String =
        runCatching { Settings.Global.getString(appContext.contentResolver, Settings.Global.DEVICE_NAME) }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: Build.MODEL ?: "Phone"

    /** A handshake result plus the socket it ran on. */
    private class OpenResult(val result: PhoneHandshake.Result, val socket: Socket? = null)

    companion object {
        const val ID_PREFIX = "tv:"
        private const val MAX_ACCOUNT_NAME = 80
        private const val CONNECT_TIMEOUT_MS = 4_000
        private const val HANDSHAKE_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000
        private const val PING_MS = 10_000L
    }
}
