package com.flux.jshare

import android.content.Context
import android.content.pm.ApplicationInfo
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object AppExporter {
    data class ExportedApp(val file: File, val displayName: String, val mime: String)

    fun export(context: Context, packageName: String): ExportedApp {
        val pm = context.packageManager
        val appInfo: ApplicationInfo = pm.getApplicationInfo(packageName, 0)
        val label = pm.getApplicationLabel(appInfo).toString().ifBlank { packageName }
        val version = runCatching { pm.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty()
        val safeBase = UriTools.sanitize(listOf(label, version).filter { it.isNotBlank() }.joinToString("-"))
        val outDir = File(context.cacheDir, "exports").apply { mkdirs() }
        outDir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24 * 60 * 60 * 1000L }?.forEach { it.delete() }

        val splitPaths = appInfo.splitSourceDirs?.filter { it.isNotBlank() }.orEmpty()
        return if (splitPaths.isEmpty()) {
            val out = File(outDir, "$safeBase.apk")
            File(appInfo.sourceDir).copyTo(out, overwrite = true)
            ExportedApp(out, out.name, "application/vnd.android.package-archive")
        } else {
            val out = File(outDir, "$safeBase.apks")
            ZipOutputStream(FileOutputStream(out).buffered(128 * 1024)).use { zip ->
                addApk(zip, File(appInfo.sourceDir), "base.apk")
                splitPaths.forEachIndexed { index, path ->
                    val src = File(path)
                    val name = src.name.takeIf { it.endsWith(".apk", true) } ?: "split-$index.apk"
                    addApk(zip, src, name)
                }
            }
            ExportedApp(out, out.name, "application/vnd.jshare.apks")
        }
    }

    private fun addApk(zip: ZipOutputStream, file: File, entryName: String) {
        zip.putNextEntry(ZipEntry(entryName))
        FileInputStream(file).buffered(128 * 1024).use { it.copyTo(zip, 128 * 1024) }
        zip.closeEntry()
    }
}
