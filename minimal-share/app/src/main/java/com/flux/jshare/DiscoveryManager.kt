package com.flux.jshare

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class DiscoveryManager(
    context: Context,
    private val onPeersChanged: (List<Peer>) -> Unit
) {
    companion object {
        const val DISCOVERY_PORT = 53318
        const val TRANSFER_PORT = 53319
        private const val GROUP = "224.0.0.167"
        private const val PREFIX = "JSHARE|1|"
        private const val STALE_MS = 6500L
    }

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("jshare", Context.MODE_PRIVATE)
    private val deviceId = prefs.getString("device_id", null) ?: UUID.randomUUID().toString().also {
        prefs.edit().putString("device_id", it).apply()
    }
    private val deviceName = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android"
    private val peers = ConcurrentHashMap<String, Peer>()
    private val running = AtomicBoolean(false)
    private var receiverSocket: MulticastSocket? = null
    private var senderSocket: DatagramSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var executor = Executors.newFixedThreadPool(2)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun start() {
        if (!running.compareAndSet(false, true)) return
        if (executor.isShutdown) executor = Executors.newFixedThreadPool(2)
        acquireMulticastLock()
        executor.execute(::listenLoop)
        executor.execute(::announceLoop)
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        runCatching { receiverSocket?.close() }
        runCatching { senderSocket?.close() }
        receiverSocket = null
        senderSocket = null
        peers.clear()
        publishPeers()
        runCatching { multicastLock?.release() }
        multicastLock = null
        executor.shutdownNow()
    }

    private fun acquireMulticastLock() {
        val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
        multicastLock = wifi.createMulticastLock("jshare-discovery").apply {
            setReferenceCounted(false)
            runCatching { acquire() }
        }
    }

    private fun listenLoop() {
        try {
            val group = InetAddress.getByName(GROUP)
            val socket = MulticastSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(DISCOVERY_PORT))
                soTimeout = 1200
                joinGroup(group)
            }
            receiverSocket = socket
            val buffer = ByteArray(2048)
            while (running.get()) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val message = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                    val host = packet.address.hostAddress ?: continue
                    handleAnnouncement(message, host)
                } catch (_: SocketTimeoutException) {
                    purgeStale()
                } catch (_: Throwable) {
                    if (!running.get()) break
                }
            }
            runCatching { socket.leaveGroup(group) }
        } catch (_: Throwable) {
            // Discovery can still work through broadcast on some networks.
        }
    }

    private fun announceLoop() {
        val socket = DatagramSocket().apply { broadcast = true }
        senderSocket = socket
        val name = Base64.encodeToString(deviceName.toByteArray(Charsets.UTF_8), Base64.NO_WRAP or Base64.URL_SAFE)
        val payload = "$PREFIX$name|$TRANSFER_PORT|$deviceId".toByteArray(Charsets.UTF_8)
        val multicast = InetAddress.getByName(GROUP)
        val broadcast = InetAddress.getByName("255.255.255.255")
        while (running.get()) {
            runCatching { socket.send(DatagramPacket(payload, payload.size, multicast, DISCOVERY_PORT)) }
            runCatching { socket.send(DatagramPacket(payload, payload.size, broadcast, DISCOVERY_PORT)) }
            purgeStale()
            try {
                Thread.sleep(1100)
            } catch (_: InterruptedException) {
                break
            }
        }
    }

    private fun handleAnnouncement(message: String, host: String) {
        if (!message.startsWith(PREFIX)) return
        val parts = message.split('|')
        if (parts.size < 5) return
        val id = parts[4]
        if (id == deviceId) return
        val port = parts[3].toIntOrNull() ?: return
        val name = runCatching {
            String(Base64.decode(parts[2], Base64.NO_WRAP or Base64.URL_SAFE), Charsets.UTF_8)
        }.getOrDefault("Android")
        val previous = peers[id]
        peers[id] = Peer(id, name, host, port, System.currentTimeMillis())
        if (previous == null || previous.host != host || previous.name != name || previous.port != port) {
            publishPeers()
        }
    }

    private fun purgeStale() {
        val now = System.currentTimeMillis()
        var changed = false
        val iterator = peers.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value.lastSeen > STALE_MS) {
                peers.remove(entry.key)
                changed = true
            }
        }
        if (changed) publishPeers()
    }

    private fun publishPeers() {
        val snapshot = peers.values.sortedBy { it.name.lowercase() }
        mainHandler.post { onPeersChanged(snapshot) }
    }
}
