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
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.Socket
import com.hhst.youtubelite.tvlink.TvLinkController
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
    /** This TV's address on the Wi-Fi, for typing into the phone when it isn't listed. */
    val address: String = "",
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
    /**
     * One thread writes to the phone, in order: each frame's counter is sealed and written in
     * turn, so two messages at once (a state change and the tick) can't reach it out of order.
     */
    private var writer: ExecutorService? = null
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
        writer = Executors.newSingleThreadExecutor { r -> Thread(r, "TvLinkWrite").apply { isDaemon = true } }
        pool.execute {
            val socket = bind() ?: return@execute
            // stop() may have run while this was binding: give the port straight back.
            if (io !== pool) {
                runCatching { socket.close() }
                return@execute
            }
            server = socket
            main.post { if (io === pool) register(socket.localPort) }
            refreshAddress()
            main.post { if (io === pool) watchNetwork() }
            while (!socket.isClosed) {
                val client = try { socket.accept() } catch (_: IOException) { break }
                pool.execute { serve(client) }
            }
        }
    }

    /**
     * The fixed port, so the phone needs only the address. A port the last run just let go
     * can still be held for a moment, so it tries again briefly before taking any free port.
     */
    private fun bind(): ServerSocket? {
        repeat(BIND_TRIES) { attempt ->
            val socket = runCatching {
                ServerSocket().apply {
                    reuseAddress = true
                    bind(java.net.InetSocketAddress(TvLinkController.DEFAULT_PORT))
                }
            }.getOrNull()
            if (socket != null) return socket
            if (attempt < BIND_TRIES - 1) Thread.sleep(BIND_RETRY_MS)
        }
        return try {
            ServerSocket(0)
        } catch (error: IOException) {
            AppLog.event(AppLog.Category.CAST, "tv_link_listen_failed", failure = error)
            null
        }
    }

    /** Works out this TV's address again (Wi-Fi that came up late, or a new address). */
    private fun refreshAddress() {
        val socket = server ?: return
        val host = localAddress()
        val address = when {
            host == null -> ""
            socket.localPort == TvLinkController.DEFAULT_PORT -> host
            else -> "$host:${socket.localPort}"
        }
        _state.update { it.copy(listening = true, address = address) }
    }

    private var networkCallback: android.net.ConnectivityManager.NetworkCallback? = null

    /** A network change re-reads the address and announces the TV again. */
    private fun watchNetwork() {
        if (networkCallback != null) return
        val manager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return
        val callback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) = reannounce()
            override fun onLinkPropertiesChanged(network: android.net.Network, linkProperties: android.net.LinkProperties) = reannounce()
        }
        runCatching { manager.registerDefaultNetworkCallback(callback) }.onSuccess { networkCallback = callback }
    }

    /** Only when the address really changed (or the announcement failed) is the TV announced again. */
    private fun reannounce() {
        io?.execute {
            val before = _state.value.address
            refreshAddress()
            val changed = _state.value.address != before
            main.post {
                val socket = server ?: return@post
                if (changed || registration == null) {
                    unregister()
                    register(socket.localPort)
                }
            }
        }
    }

    fun stop() {
        networkCallback?.let { callback ->
            val manager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            runCatching { manager?.unregisterNetworkCallback(callback) }
        }
        networkCallback = null
        unregister()
        runCatching { server?.close() }
        server = null
        active?.let { runCatching { it.socket.close() } }
        active = null
        io?.shutdownNow()
        io = null
        writer?.shutdownNow()
        writer = null
        _state.update { it.copy(listening = false, connectedPhone = null) }
    }

    /** Sends [message] to the connected phone, if any; never blocks the caller. */
    fun send(message: TvLinkMessage) {
        val current = active ?: return
        writer?.execute {
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
                // Try again a little later: the phone finds the TV on its own only while it's announced.
                main.post { if (registration === this) registration = null }
                main.postDelayed({ if (registration == null && server?.localPort == port) register(port) }, REGISTER_RETRY_MS)
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

    /** The TV's IPv4 address on the home network, or null when it has none. */
    private fun localAddress(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }?.hostAddress
    }.getOrNull()

    private fun tvName(): String =
        runCatching { Settings.Global.getString(appContext.contentResolver, Settings.Global.DEVICE_NAME) }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: Build.MODEL ?: "Puretube TV"

    companion object {
        const val SERVICE_TYPE = "_puretube._tcp"
        private const val HANDSHAKE_TIMEOUT_MS = 15_000
        private const val BIND_TRIES = 5
        private const val BIND_RETRY_MS = 400L
        private const val REGISTER_RETRY_MS = 10_000L
        /** The phone pings well inside this; silence this long means it is gone. */
        private const val IDLE_TIMEOUT_MS = 30_000
    }
}
