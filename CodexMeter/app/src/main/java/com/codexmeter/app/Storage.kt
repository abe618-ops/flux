package com.codexmeter.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class Storage(context: Context) {
    private val prefs = context.getSharedPreferences("codexmeter", Context.MODE_PRIVATE)
    var bridgeUrl: String
        get() = prefs.getString("bridge_url", "") ?: ""
        set(v) { prefs.edit().putString("bridge_url", v.trim()).apply() }
    var bridgeToken: String
        get() = prefs.getString("bridge_token", "") ?: ""
        set(v) { prefs.edit().putString("bridge_token", v.trim()).apply() }
    var bridgeName: String
        get() = prefs.getString("bridge_name", "") ?: ""
        set(v) { prefs.edit().putString("bridge_name", v.trim()).apply() }

    fun clearBridge() {
        prefs.edit().remove("bridge_url").remove("bridge_token").remove("bridge_name").apply()
    }

    fun latest(): UsageSnapshot? = prefs.getString("latest", null)?.let { runCatching { UsageSnapshot.fromJson(JSONObject(it)) }.getOrNull() }

    fun save(s: UsageSnapshot) {
        prefs.edit().putString("latest", s.toJson().toString()).apply()
        val arr = JSONArray(prefs.getString("history", "[]"))
        arr.put(JSONObject().put("t", s.updatedAt).put("five", s.fiveHourUsed).put("week", s.weeklyUsed).put("today", s.tokensToday).put("lifetime", s.lifetimeTokens))
        val trimmed = if (arr.length() <= 500) arr else JSONArray().also { out -> for (i in arr.length()-500 until arr.length()) out.put(arr.get(i)) }
        prefs.edit().putString("history", trimmed.toString()).apply()
    }

    fun usageEfficiency(): Pair<Double, Int>? {
        val arr = JSONArray(prefs.getString("history", "[]"))
        if (arr.length() < 2) return null
        val a=arr.optJSONObject(maxOf(0,arr.length()-50)) ?: return null
        val b=arr.optJSONObject(arr.length()-1) ?: return null
        val du=b.optDouble("week")-a.optDouble("week")
        val dt=b.optLong("lifetime")-a.optLong("lifetime")
        if(du <= .2 || dt <= 0) return null
        return (dt/du) to arr.length()
    }
}
