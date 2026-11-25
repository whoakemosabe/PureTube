package com.hhst.youtubelite.tv

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.hhst.youtubelite.cast.protocol.CastV2Discovery
import com.hhst.youtubelite.diagnostics.AppLog
import com.hhst.youtubelite.tvlink.LineChannel
import com.hhst.youtubelite.tvlink.PairingCode
import com.hhst.youtubelite.tvlink.SecureChannel
import com.hhst.youtubelite.tvlink.TvHandshake
import com.hhst.youtubelite.tvlink.TvLinkMessage
import com.hhst.youtubelite.tvlink.TvLinkStore
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** What the TV's ready screen shows. */
data class TvLinkUiState(
    val tvName: String = "",
    val code: String = "",
    val connectedPhone: String? = null,
    val pairedPhones: List<String> = emptyList(),
    val listening: Boolean = false,
)

/**
 * The TV end of the phone link: advertises this TV on the Wi-Fi, takes one phone at a time,
 * and hands its commands to [onCommand] on the main thread. A newer phone replaces the
 * current one, the way picking a cast device moves playback.
 */
internal class TvLinkServer(
    context: Context,
    private val store: TvLinkStore,
    private val onCommand: (TvLinkMessage) -> Unit,
    private val onPhoneLeft: () -> Unit,
) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val code = PairingCode()
    private val handshake = TvHandshake(store, ::tvName, code)
    private val _state = MutableStateFlow(TvLinkUiState(tvName = tvName(), code = code.current, pairedPhones = pairedNames()))
    val state: StateFlow<TvLinkUiState> = _state.asStateFlow()

    private var io: ExecutorService? = null
    @Volatile private var server: ServerSocket? = null
    @Volatile private var active: Active? = null
    private var registration: NsdManager.RegistrationListener? = null

    private class Active(val socket: Socket, val channel: SecureChannel, val phoneName: String)

    init {
        code.onChanged = { next -> _state.update { it.copy(code = next) } }
    }

    fun start() {
        if (io != null) return
        val pool = Executors.newCachedThreadPool { r -> Thread(r, "TvLink").apply { isDaemon = true } }
        io = pool
        pool.execute {
            val socket = try {
                ServerSocket(0)
            } catch (error: IOException) {
                AppLog.event(AppLog.Category.CAST, "tv_link_listen_failed", failure = error)
                return@execute
            }
            server = socket
            main.post { register(socket.localPort) }
            _state.update { it.copy(listening = true) }
            while (!socket.isClosed) {
                val client = try { socket.accept() } catch (_: IOException) { break }
                pool.execute { serve(client) }
            }
        }
    }

    fun stop() {
        unregister()
        runCatching { server?.close() }
        server = null
        active?.let { runCatching { it.socket.close() } }
        active = null
        io?.shutdownNow()
        io = null
        _state.update { it.copy(listening = false, connectedPhone = null) }
    }

    /** Sends [message] to the connected phone, if any; never blocks the caller. */
    fun send(message: TvLinkMessage) {
        val current = active ?: return
        io?.execute {
            try {
                current.channel.send(message)
            } catch (_: IOException) {
                drop(current)
            }
        }
    }

    fun forgetPhones() {
        store.peers().forEach { store.remove(it.id) }
        active?.let(::drop)
        _state.update { it.copy(pairedPhones = emptyList()) }
    }

    private fun serve(socket: Socket) {
        socket.tcpNoDelay = true
        socket.soTimeout = HANDSHAKE_TIMEOUT_MS
        val lines = LineChannel(socket.getInputStream(), socket.getOutputStream()) { socket.close() }
        val accepted = handshake.run(lines) ?: return
        socket.soTimeout = IDLE_TIMEOUT_MS
        val next = Active(socket, accepted.channel, accepted.phoneName)
        active?.let { previous -> runCatching { previous.socket.close() } }
        active = next
        AppLog.event(AppLog.Category.CAST, "tv_link_phone_connected", mapOf("new_pairing" to accepted.newlyPaired))
        _state.update { it.copy(connectedPhone = accepted.phoneName, pairedPhones = pairedNames()) }
        try {
            while (true) {
                val message = next.channel.receive() ?: break
                if (message == TvLinkMessage.Ping) continue
                main.post { if (active === next) onCommand(message) }
            }
        } catch (_: IOException) {
            // A dead phone, a timeout or a frame that didn't open all end the connection.
        }
        drop(next)
    }

    private fun drop(connection: Active) {
        runCatching { connection.socket.close() }
        if (active !== connection) return
        active = null
        _state.update { it.copy(connectedPhone = null) }
        main.post(onPhoneLeft)
    }

    private fun register(port: Int) {
        val manager = appContext.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        val info = NsdServiceInfo().apply {
            serviceName = tvName()
            serviceType = SERVICE_TYPE
            setPort(port)
            setAttribute(CastV2Discovery.TXT_FRIENDLY_NAME, tvName())
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                AppLog.event(AppLog.Category.CAST, "tv_link_advertised")
            }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                AppLog.event(AppLog.Category.CAST, "tv_link_advertise_failed", mapOf("error_code" to errorCode), critical = true)
            }
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }
        try {
            manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
            registration = listener
        } catch (error: Exception) {
            AppLog.event(AppLog.Category.CAST, "tv_link_advertise_failed", failure = error)
        }
    }

    private fun unregister() {
        val listener = registration ?: return
        registration = null
        val manager = appContext.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        runCatching { manager.unregisterService(listener) }
    }

    private fun pairedNames() = store.peers().map { it.name }

    private fun tvName(): String =
        runCatching { Settings.Global.getString(appContext.contentResolver, Settings.Global.DEVICE_NAME) }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: Build.MODEL ?: "Puretube TV"

    companion object {
        const val SERVICE_TYPE = "_puretube._tcp"
        private const val HANDSHAKE_TIMEOUT_MS = 15_000
        /** The phone pings well inside this; silence this long means it is gone. */
        private const val IDLE_TIMEOUT_MS = 30_000
    }
}
