package com.codexmeter.app

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

data class DailyToken(val date: String, val tokens: Long)
data class TaskUsage(val title: String, val tokens: Long, val creditsMicros: Long = 0, val model: String = "Codex", val threadId: String = "")

data class UsageSnapshot(
    val fiveHourUsed: Float,
    val weeklyUsed: Float,
    val fiveHourResetAt: Long,
    val weeklyResetAt: Long,
    val tokensToday: Long,
    val tokensWeek: Long,
    val tokens30d: Long,
    val lifetimeTokens: Long,
    val inputTokens: Long,
    val cachedInputTokens: Long,
    val outputTokens: Long,
    val creditsBalance: Double?,
    val resetCredits: Int,
    val plan: String,
    val account: String,
    val source: String,
    val updatedAt: Long,
    val daily: List<DailyToken> = emptyList(),
    val tasks: List<TaskUsage> = emptyList()
) {
    val fiveHourRemaining: Float get() = if (fiveHourUsed < 0f) -1f else (100f - fiveHourUsed).coerceIn(0f, 100f)
    val weeklyRemaining: Float get() = if (weeklyUsed < 0f) -1f else (100f - weeklyUsed).coerceIn(0f, 100f)
    val hasLiveQuota: Boolean get() = fiveHourUsed >= 0f && weeklyUsed >= 0f && source != "UNCONNECTED"
    val healthScore: Int get() {
        if (!hasLiveQuota) return -1
        val pressure = maxOf(fiveHourUsed, weeklyUsed)
        return (100f - pressure * .86f).toInt().coerceIn(0, 100)
    }
    val cacheRate: Float get() {
        val denom = inputTokens + cachedInputTokens
        return if (denom <= 0) 0f else cachedInputTokens.toFloat() / denom.toFloat()
    }
    fun toJson(): JSONObject = JSONObject().apply {
        put("fiveHourUsed", fiveHourUsed); put("weeklyUsed", weeklyUsed)
        put("fiveHourResetAt", fiveHourResetAt); put("weeklyResetAt", weeklyResetAt)
        put("tokensToday", tokensToday); put("tokensWeek", tokensWeek); put("tokens30d", tokens30d)
        put("lifetimeTokens", lifetimeTokens); put("inputTokens", inputTokens)
        put("cachedInputTokens", cachedInputTokens); put("outputTokens", outputTokens)
        put("creditsBalance", creditsBalance ?: JSONObject.NULL); put("resetCredits", resetCredits)
        put("plan", plan); put("account", account); put("source", source); put("updatedAt", updatedAt)
        put("daily", JSONArray().apply { daily.forEach { put(JSONObject().put("date", it.date).put("tokens", it.tokens)) } })
        put("tasks", JSONArray().apply { tasks.forEach { put(JSONObject().put("title", it.title).put("tokens", it.tokens).put("creditsMicros", it.creditsMicros).put("model", it.model).put("threadId", it.threadId)) } })
    }
    companion object {
        fun fromJson(o: JSONObject): UsageSnapshot {
            fun obj(name: String) = o.optJSONObject(name) ?: JSONObject()
            val limits = obj("limits")
            val five = limits.optJSONObject("five_hour") ?: limits.optJSONObject("primary") ?: JSONObject()
            val week = limits.optJSONObject("weekly") ?: limits.optJSONObject("secondary") ?: JSONObject()
            val tok = obj("tokens")
            val acc = obj("account")
            val credits = obj("credits")
            val dailyArray = tok.optJSONArray("daily") ?: o.optJSONArray("daily") ?: JSONArray()
            val daily = (0 until dailyArray.length()).mapNotNull { i ->
                dailyArray.optJSONObject(i)?.let { DailyToken(it.optString("date", it.optString("startDate")), it.optLong("tokens")) }
            }
            val taskArray = o.optJSONArray("tasks") ?: JSONArray()
            val tasks = (0 until taskArray.length()).mapNotNull { i ->
                taskArray.optJSONObject(i)?.let { TaskUsage(it.optString("title", "Codex thread"), it.optLong("tokens"), it.optLong("creditsMicros"), it.optString("model", "Codex"), it.optString("threadId")) }
            }
            return UsageSnapshot(
                fiveHourUsed = quotaNum(o, "fiveHourUsed", five, "used_percent", "usedPercent"),
                weeklyUsed = quotaNum(o, "weeklyUsed", week, "used_percent", "usedPercent"),
                fiveHourResetAt = longNum(o, "fiveHourResetAt", five, "resets_at", "resetsAt"),
                weeklyResetAt = longNum(o, "weeklyResetAt", week, "resets_at", "resetsAt"),
                tokensToday = longNum(o, "tokensToday", tok, "today", "tokensToday"),
                tokensWeek = longNum(o, "tokensWeek", tok, "week", "tokensWeek"),
                tokens30d = longNum(o, "tokens30d", tok, "thirty_days", "tokens30d"),
                lifetimeTokens = longNum(o, "lifetimeTokens", tok, "lifetime", "lifetimeTokens"),
                inputTokens = longNum(o, "inputTokens", tok, "input", "inputTokens"),
                cachedInputTokens = longNum(o, "cachedInputTokens", tok, "cached_input", "cachedInputTokens"),
                outputTokens = longNum(o, "outputTokens", tok, "output", "outputTokens"),
                creditsBalance = if (o.has("creditsBalance")) o.optDouble("creditsBalance") else if (credits.has("balance")) credits.optDouble("balance") else null,
                resetCredits = if (o.has("resetCredits")) o.optInt("resetCredits") else credits.optInt("reset_credits", credits.optInt("resetCredits", 0)),
                plan = o.optString("plan", acc.optString("plan", acc.optString("planType", "ChatGPT"))),
                account = o.optString("account", acc.optString("email", "Codex account")),
                source = o.optString("source", "CodexMeter Bridge"),
                updatedAt = o.optLong("updatedAt", System.currentTimeMillis()/1000),
                daily = daily, tasks = tasks
            )
        }
        private fun quotaNum(root: JSONObject, rootKey: String, nested: JSONObject, vararg keys: String): Float {
            if (root.has(rootKey)) return root.optDouble(rootKey, -1.0).toFloat()
            keys.forEach { if (nested.has(it)) return nested.optDouble(it, -1.0).toFloat() }
            return -1f
        }
        private fun num(root: JSONObject, rootKey: String, nested: JSONObject, vararg keys: String): Double {
            if (root.has(rootKey)) return root.optDouble(rootKey)
            keys.forEach { if (nested.has(it)) return nested.optDouble(it) }
            return 0.0
        }
        private fun longNum(root: JSONObject, rootKey: String, nested: JSONObject, vararg keys: String): Long {
            if (root.has(rootKey)) return root.optLong(rootKey)
            keys.forEach { if (nested.has(it)) return nested.optLong(it) }
            return 0L
        }
        fun demo(): UsageSnapshot {
            return UsageSnapshot(
                -1f, -1f, 0, 0,
                0, 0, 0, 0, 0, 0, 0,
                null, 0, "—", "尚未连接真实 Codex 数据", "UNCONNECTED",
                System.currentTimeMillis()/1000, emptyList(), emptyList()
            )
        }
    }
}
fun formatTokens(v: Long): String = when {
    v >= 1_000_000_000L -> "%.2fB".format(v/1_000_000_000.0)
    v >= 1_000_000L -> "%.1fM".format(v/1_000_000.0)
    v >= 1_000L -> "%.1fK".format(v/1_000.0)
    else -> v.toString()
}
fun resetText(epoch: Long): String {
    if (epoch <= 0) return "reset unavailable"
    val sec = epoch - System.currentTimeMillis()/1000
    if (sec <= 0) return "resetting"
    val d=sec/86400; val h=(sec%86400)/3600; val m=(sec%3600)/60
    return when { d>0 -> "${d}d ${h}h"; h>0 -> "${h}h ${m}m"; else -> "${m}m" }
}
