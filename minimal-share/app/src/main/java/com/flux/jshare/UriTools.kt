package com.flux.jshare

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File

object UriTools {
    fun displayName(context: Context, uri: Uri): String {
        if (uri.scheme == "content") {
            var cursor: Cursor? = null
            try {
                cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                if (cursor != null && cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return cursor.getString(idx) ?: "文件"
                }
            } finally {
                cursor?.close()
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "文件"
    }

    fun size(context: Context, uri: Uri): Long {
        if (uri.scheme == "content") {
            var cursor: Cursor? = null
            try {
                cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
                if (cursor != null && cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0 && !cursor.isNull(idx)) return cursor.getLong(idx)
                }
            } finally {
                cursor?.close()
            }
        }
        return runCatching { context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } }.getOrNull() ?: -1L
    }

    fun mime(context: Context, uri: Uri, fileName: String): String {
        context.contentResolver.getType(uri)?.let { if (it.isNotBlank()) return it }
        return mimeForName(fileName)
    }

    fun mimeForName(name: String): String {
        if (name.endsWith(".apk", true)) return "application/vnd.android.package-archive"
        if (name.endsWith(".apks", true)) return "application/vnd.jshare.apks"
        val ext = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    fun sanitize(name: String): String {
        val cleaned = name.replace(Regex("[\\/:*?\"<>|\u0000-\u001f]"), "_").trim()
        return cleaned.take(160).ifBlank { "文件" }
    }

    fun copyUriToCache(context: Context, uri: Uri, preferredName: String): File {
        val dir = File(context.cacheDir, "prepared").apply { mkdirs() }
        val file = File(dir, "${System.currentTimeMillis()}-${sanitize(preferredName)}")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "无法读取文件" }
            file.outputStream().buffered().use { output -> input.copyTo(output, 128 * 1024) }
        }
        return file
    }
}
