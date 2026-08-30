package com.codexmeter.app

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class BridgeClient(private val context: Context) {
    private val storage = Storage(context)

    fun fetch(): Result<UsageSnapshot> {
        val existing = fetchSaved()
        if (existing.isSuccess) return existing

        val discovered = BridgeDiscovery(context).discoverAndPair()
        if (discovered.isFailure) {
            return Result.failure(existing.exceptionOrNull() ?: discovered.exceptionOrNull() ?: IllegalStateException("Bridge unavailable"))
        }
        return fetchSaved()
    }

    private fun fetchSaved(): Result<UsageSnapshot> = runCatching {
        val base = storage.bridgeUrl.trim().trimEnd('/')
        require(base.isNotBlank()) { "Bridge 尚未自动发现" }
        val conn = URL("$base/v1/usage").openConnection() as HttpURLConnection
        conn.connectTimeout=3500; conn.readTimeout=8000; conn.requestMethod="GET"
        conn.setRequestProperty("Accept","application/json")
        if(storage.bridgeToken.isNotBlank()) conn.setRequestProperty("Authorization","Bearer ${storage.bridgeToken}")
        val code=conn.responseCode
        val body=(if(code in 200..299) conn.inputStream else conn.errorStream).bufferedReader().use { it.readText() }
        require(code in 200..299) { "Bridge HTTP $code: ${body.take(120)}" }
        UsageSnapshot.fromJson(JSONObject(body)).also { storage.save(it) }
    }

    fun rediscover(): Result<UsageSnapshot> {
        storage.clearBridge()
        return fetch()
    }
}
