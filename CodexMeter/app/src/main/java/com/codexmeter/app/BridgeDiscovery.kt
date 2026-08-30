package com.codexmeter.app

import android.content.Context
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.net.URL

data class DiscoveredBridge(val baseUrl: String, val nonce: String, val name: String)

class BridgeDiscovery(private val context: Context) {
    companion object {
        const val DISCOVERY_PORT = 38765
        const val MAGIC = "CODEXMETER_DISCOVERY_V1"
    }

    fun discoverAndPair(timeoutMs: Int = 5500): Result<DiscoveredBridge> = runCatching {
        val deadline = System.currentTimeMillis() + timeoutMs
        val socket = DatagramSocket(null).apply {
            reuseAddress = true
            broadcast = true
            bind(InetSocketAddress(DISCOVERY_PORT))
            soTimeout = 700
        }
        socket.use {
            val buf = ByteArray(2048)
            while (System.currentTimeMillis() < deadline) {
                try {
                    val packet = DatagramPacket(buf, buf.size)
                    it.receive(packet)
                    val raw = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                    val o = JSONObject(raw)
                    if (o.optString("magic") != MAGIC) continue
                    val httpPort = o.optInt("port", 8765)
                    val nonce = o.optString("nonce")
                    if (nonce.isBlank()) continue
                    val host = packet.address.hostAddress ?: continue
                    val base = "http://$host:$httpPort"
                    val token = pair(base, nonce)
                    Storage(context).apply {
                        bridgeUrl = base
                        bridgeToken = token
                        bridgeName = o.optString("name", "CodexMeter Bridge")
                    }
                    return@runCatching DiscoveredBridge(base, nonce, o.optString("name", "CodexMeter Bridge"))
                } catch (_: SocketTimeoutException) {
                }
            }
        }
        error("未发现 CodexMeter Bridge，请确认电脑和手机在同一网络且 Bridge 已启动")
    }

    private fun pair(base: String, nonce: String): String {
        val encoded = java.net.URLEncoder.encode(nonce, "UTF-8")
        val conn = URL("$base/v1/pair?nonce=$encoded").openConnection() as HttpURLConnection
        conn.connectTimeout = 2500
        conn.readTimeout = 3500
        conn.requestMethod = "GET"
        conn.setRequestProperty("Accept", "application/json")
        val code = conn.responseCode
        val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
            .bufferedReader().use { it.readText() }
        require(code in 200..299) { "自动配对失败 HTTP $code" }
        val token = JSONObject(body).optString("token")
        require(token.isNotBlank()) { "Bridge 未返回配对令牌" }
        return token
    }
}
