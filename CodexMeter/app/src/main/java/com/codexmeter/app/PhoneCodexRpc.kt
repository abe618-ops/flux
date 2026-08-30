package com.codexmeter.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

data class DeviceCodeLogin(
    val loginId: String,
    val verificationUrl: String,
    val userCode: String,
)

class PhoneCodexRpcClient(
    private val runtime: PhoneRuntimeManager,
) {
    private val http = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
    private val nextId = AtomicLong(1)
    private val pending = ConcurrentHashMap<Long, Pending>()
    @Volatile private var socket: WebSocket? = null
    @Volatile private var connected = false

    private data class Pending(
        val latch: CountDownLatch = CountDownLatch(1),
        @Volatile var result: JSONObject? = null,
        @Volatile var error: Throwable? = null,
    )

    suspend fun ensureConnected(): Result<Unit> = withContext(Dispatchers.IO) {
        runtime.ensureBackend().getOrElse { return@withContext Result.failure(it) }
        if (connected && socket != null) return@withContext Result.success(Unit)
        val latch = CountDownLatch(1)
        var connectError: Throwable? = null
        val req = Request.Builder().url("ws://127.0.0.1:8765").build()
        socket = http.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                connected = true
                latch.countDown()
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                connected = false
                connectError = t
                pending.values.forEach { p -> p.error = t; p.latch.countDown() }
                latch.countDown()
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                connected = false
            }
        })
        if (!latch.await(6, TimeUnit.SECONDS) || !connected) {
            return@withContext Result.failure(connectError ?: IllegalStateException("无法连接手机本地 Codex app-server"))
        }
        try {
            callBlocking("initialize", JSONObject()
                .put("clientInfo", JSONObject()
                    .put("name", "codexmeter-android")
                    .put("title", "CodexMeter Android")
                    .put("version", "0.4.0"))
                .put("capabilities", JSONObject()), 8_000)
            socket?.send(JSONObject().put("method", "initialized").put("params", JSONObject()).toString())
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun readUsage(): Result<UsageSnapshot> = withContext(Dispatchers.IO) {
        ensureConnected().getOrElse { return@withContext Result.failure(it) }
        runCatching {
            val account = callBlocking("account/read", JSONObject().put("refreshToken", false))
            val rate = callBlocking("account/rateLimits/read", JSONObject())
            val usage = runCatching { callBlocking("account/usage/read", JSONObject()) }.getOrElse { JSONObject() }
            normalize(account, rate, usage)
        }
    }

    suspend fun startDeviceCodeLogin(): Result<DeviceCodeLogin> = withContext(Dispatchers.IO) {
        ensureConnected().getOrElse { return@withContext Result.failure(it) }
        runCatching {
            val result = callBlocking("account/login/start", JSONObject().put("type", "chatgptDeviceCode"), 15_000)
            require(result.optString("type") == "chatgptDeviceCode") { "Codex 未返回设备码登录响应" }
            DeviceCodeLogin(
                loginId = result.optString("loginId"),
                verificationUrl = result.optString("verificationUrl"),
                userCode = result.optString("userCode"),
            )
        }
    }

    suspend fun accountReady(): Boolean = withContext(Dispatchers.IO) {
        if (ensureConnected().isFailure) return@withContext false
        runCatching {
            val r = callBlocking("account/read", JSONObject().put("refreshToken", true), 12_000)
            r.optJSONObject("account") != null
        }.getOrDefault(false)
    }

    private fun callBlocking(method: String, params: JSONObject, timeoutMs: Long = 10_000): JSONObject {
        val id = nextId.getAndIncrement()
        val p = Pending()
        pending[id] = p
        val msg = JSONObject().put("method", method).put("id", id).put("params", params)
        if (socket?.send(msg.toString()) != true) {
            pending.remove(id)
            error("Codex WebSocket 未连接")
        }
        if (!p.latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            pending.remove(id)
            error("Codex RPC 超时: " + method)
        }
        p.error?.let { throw it }
        return p.result ?: JSONObject()
    }

    private fun handleMessage(text: String) {
        val msg = runCatching { JSONObject(text) }.getOrNull() ?: return
        if (!msg.has("id")) return
        val id = msg.optLong("id", -1)
        if (id < 0) return
        val p = pending.remove(id) ?: return
        if (msg.has("error")) {
            p.error = IllegalStateException(msg.optJSONObject("error")?.optString("message") ?: msg.opt("error").toString())
        } else {
            p.result = when (val r = msg.opt("result")) {
                is JSONObject -> r
                null, JSONObject.NULL -> JSONObject()
                else -> JSONObject().put("value", r)
            }
        }
        p.latch.countDown()
    }

    private fun normalize(accountResp: JSONObject, rateResp: JSONObject, usageResp: JSONObject): UsageSnapshot {
        val snap = selectCodexSnapshot(rateResp)
        val windows = listOfNotNull(snap.optJSONObject("primary"), snap.optJSONObject("secondary"))
        fun mins(w: JSONObject) = w.optInt("windowDurationMins", 0)
        val five = windows.minByOrNull { kotlin.math.abs(mins(it) - 300) }
        val week = windows.minByOrNull { kotlin.math.abs(mins(it) - 10080) }
        fun used(w: JSONObject?) = if (w == null || !w.has("usedPercent")) -1f else w.optDouble("usedPercent").toFloat()
        fun reset(w: JSONObject?) = w?.optLong("resetsAt", 0) ?: 0L

        val account = accountResp.optJSONObject("account")
        val summary = usageResp.optJSONObject("summary") ?: JSONObject()
        val buckets = usageResp.optJSONArray("dailyUsageBuckets") ?: JSONArray()
        val daily = mutableListOf<DailyToken>()
        for (i in 0 until buckets.length()) {
            val b = buckets.optJSONObject(i) ?: continue
            daily += DailyToken(b.optString("startDate"), b.optLong("tokens"))
        }
        val today = LocalDate.now()
        fun since(days: Long): Long = daily.sumOf { d ->
            val date = runCatching { LocalDate.parse(d.date.take(10)) }.getOrNull()
            if (date != null && !date.isBefore(today.minusDays(days - 1))) d.tokens else 0L
        }
        val resetCredits = rateResp.optJSONObject("rateLimitResetCredits")?.optInt("availableCount", 0) ?: 0
        val creditsObj = snap.optJSONObject("credits")
        val creditsBalance = creditsObj?.let {
            when {
                it.has("balance") -> it.optDouble("balance")
                it.has("balanceUsd") -> it.optDouble("balanceUsd")
                else -> null
            }
        }
        return UsageSnapshot(
            fiveHourUsed = used(five),
            weeklyUsed = used(week),
            fiveHourResetAt = reset(five),
            weeklyResetAt = reset(week),
            tokensToday = since(1),
            tokensWeek = since(7),
            tokens30d = since(30),
            lifetimeTokens = summary.optLong("lifetimeTokens", 0),
            inputTokens = 0,
            cachedInputTokens = 0,
            outputTokens = 0,
            creditsBalance = creditsBalance,
            resetCredits = resetCredits,
            plan = account?.optString("planType", snap.optString("planType", "ChatGPT")) ?: "ChatGPT",
            account = account?.optString("email", "Codex account") ?: "Codex account",
            source = "PHONE CODEX",
            updatedAt = System.currentTimeMillis() / 1000,
            daily = daily,
            tasks = emptyList(),
        )
    }

    private fun selectCodexSnapshot(rateResp: JSONObject): JSONObject {
        val byId = rateResp.optJSONObject("rateLimitsByLimitId")
        val exact = byId?.optJSONObject("codex")
        if (exact != null) return exact
        return rateResp.optJSONObject("rateLimits") ?: JSONObject()
    }
}
