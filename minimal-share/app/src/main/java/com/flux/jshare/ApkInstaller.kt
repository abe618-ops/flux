package com.flux.jshare

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipFile

object ApkInstaller {
    fun install(activity: Activity, file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(activity, "请先允许“极简分享”安装未知应用，然后返回再次点击安装。", Toast.LENGTH_LONG).show()
            activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")))
            return
        }
        Thread {
            try {
                installWithSession(activity, file)
            } catch (e: Throwable) {
                activity.runOnUiThread {
                    Toast.makeText(activity, "安装准备失败：${e.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun installWithSession(context: Context, file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setSize(estimateSize(file))
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            if (file.extension.equals("apks", true)) {
                ZipFile(file).use { zip ->
                    val entries = zip.entries().asSequence().filter { !it.isDirectory && it.name.endsWith(".apk", true) }.toList()
                    require(entries.isNotEmpty()) { "APKS 中没有 APK" }
                    entries.forEachIndexed { index, entry ->
                        val safeName = entry.name.substringAfterLast('/').ifBlank { "split-$index.apk" }
                        session.openWrite(safeName, 0, entry.size.coerceAtLeast(-1L)).use { out ->
                            zip.getInputStream(entry).buffered(128 * 1024).use { input -> input.copyTo(out, 128 * 1024) }
                            session.fsync(out)
                        }
                    }
                }
            } else {
                session.openWrite("base.apk", 0, file.length()).use { out ->
                    FileInputStream(file).buffered(128 * 1024).use { input -> input.copyTo(out, 128 * 1024) }
                    session.fsync(out)
                }
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val pending = PendingIntent.getBroadcast(
                context,
                sessionId,
                Intent(context, InstallResultReceiver::class.java).putExtra("session_id", sessionId),
                flags
            )
            session.commit(pending.intentSender)
        }
    }

    private fun estimateSize(file: File): Long {
        if (!file.extension.equals("apks", true)) return file.length()
        return runCatching {
            ZipFile(file).use { zip ->
                zip.entries().asSequence().filter { !it.isDirectory && it.name.endsWith(".apk", true) }.sumOf { it.size.coerceAtLeast(0L) }
            }
        }.getOrDefault(file.length())
    }
}

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (confirm != null) context.startActivity(confirm)
            }
            PackageInstaller.STATUS_SUCCESS -> Toast.makeText(context, "安装完成", Toast.LENGTH_LONG).show()
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "状态码 $status"
                Toast.makeText(context, "安装失败：$message", Toast.LENGTH_LONG).show()
            }
        }
    }
}
