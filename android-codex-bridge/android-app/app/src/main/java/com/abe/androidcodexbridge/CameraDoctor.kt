package com.abe.androidcodexbridge

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CameraDoctor {
    private var startedAt: Long? = null
    private var baselineImageCount: Long? = null

    fun start(context: Context) {
        startedAt = System.currentTimeMillis()
        baselineImageCount = queryImageCount(context)
    }

    fun isRunning(): Boolean = startedAt != null

    fun finish(context: Context): File {
        val end = System.currentTimeMillis()
        val start = startedAt ?: end
        val before = baselineImageCount
        val after = queryImageCount(context)
        val stat = StatFs(Environment.getDataDirectory().absolutePath)
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo().also(am::getMemoryInfo)
        val accessibility = BridgeAccessibilityService.instance

        val report = JSONObject().apply {
            put("schema", "android-codex-bridge.camera-doctor.v1")
            put("startedAt", start)
            put("finishedAt", end)
            put("durationMs", end - start)
            put("manufacturer", Build.MANUFACTURER)
            put("model", Build.MODEL)
            put("android", Build.VERSION.RELEASE)
            put("sdk", Build.VERSION.SDK_INT)
            put("accessibilityConnected", accessibility != null)
            put("foregroundUiTree", accessibility?.uiTreeJson() ?: JSONObject().put("available", false))
            put("mediaImageCountBefore", before ?: JSONObject.NULL)
            put("mediaImageCountAfter", after ?: JSONObject.NULL)
            put("mediaImageDelta", if (before != null && after != null) after - before else JSONObject.NULL)
            put("freeStorageBytes", stat.availableBytes)
            put("availableMemoryBytes", memoryInfo.availMem)
            put("lowMemory", memoryInfo.lowMemory)
            put("deepSystemLogs", "unavailable_without_ADB_or_authorized_privileged_bridge")
            put("interpretationHint", when {
                before != null && after != null && after <= before -> "No new MediaStore image detected during the session. This supports a save/index failure, but does not identify the root cause without system logs."
                else -> "A new MediaStore image was detected. If the camera still reported failure, inspect file integrity and system logs."
            })
        }

        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(end))
        val dir = File(context.getExternalFilesDir(null), "diagnostics").apply { mkdirs() }
        return File(dir, "camera-doctor-$stamp.json").also { it.writeText(report.toString(2)) }.also {
            startedAt = null
            baselineImageCount = null
        }
    }

    private fun queryImageCount(context: Context): Long? = try {
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            null,
            null,
            null
        )?.use { it.count.toLong() }
    } catch (_: Exception) {
        null
    }
}
