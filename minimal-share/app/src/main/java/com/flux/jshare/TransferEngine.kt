package com.flux.jshare

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class TransferEngine(
    private val context: Context,
    private val onReceived: (ReceiveResult) -> Unit,
    private val onReceiveError: (String) -> Unit
) {
    data class ReceiveResult(
        val fileName: String,
        val mime: String,
        val cacheFile: File,
        val publicUri: Uri?,
        val publicPath: String?
    )

    private val running = AtomicBoolean(false)
    private var server: ServerSocket? = null
    private var serverExecutor = Executors.newCachedThreadPool()

    fun startServer() {
        if (!running.compareAndSet(false, true)) return
        if (serverExecutor.isShutdown) serverExecutor = Executors.newCachedThreadPool()
        serverExecutor.execute {
            try {
                val ss = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(DiscoveryManager.TRANSFER_PORT))
                }
                server = ss
                while (running.get()) {
                    val socket = runCatching { ss.accept() }.getOrNull() ?: break
                    serverExecutor.execute { receiveOne(socket) }
                }
            } catch (e: Throwable) {
                if (running.get()) onReceiveError(e.message ?: "接收服务启动失败")
            }
        }
    }

    fun stopServer() {
        if (!running.compareAndSet(true, false)) return
        runCatching { server?.close() }
        server = null
        serverExecutor.shutdownNow()
    }

    fun sendUri(
        peer: Peer,
        uri: Uri,
        onProgress: (Long, Long) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        Thread {
            try {
                val name = UriTools.displayName(context, uri)
                val size = UriTools.size(context, uri)
                val mime = UriTools.mime(context, uri, name)
                if (size < 0L) {
                    val prepared = UriTools.copyUriToCache(context, uri, name)
                    sendFileBlocking(peer, prepared, name, mime, onProgress)
                } else {
                    context.contentResolver.openInputStream(uri).use { input ->
                        requireNotNull(input) { "无法打开文件" }
                        sendStreamBlocking(peer, input.buffered(128 * 1024), name, mime, size, onProgress)
                    }
                }
                onDone()
            } catch (e: Throwable) {
                onError(e.message ?: "发送失败")
            }
        }.start()
    }

    fun sendFile(
        peer: Peer,
        file: File,
        displayName: String = file.name,
        mime: String = UriTools.mimeForName(file.name),
        onProgress: (Long, Long) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        Thread {
            try {
                sendFileBlocking(peer, file, displayName, mime, onProgress)
                onDone()
            } catch (e: Throwable) {
                onError(e.message ?: "发送失败")
            }
        }.start()
    }

    private fun sendFileBlocking(peer: Peer, file: File, displayName: String, mime: String, onProgress: (Long, Long) -> Unit) {
        FileInputStream(file).buffered(128 * 1024).use { input ->
            sendStreamBlocking(peer, input, displayName, mime, file.length(), onProgress)
        }
    }

    private fun sendStreamBlocking(
        peer: Peer,
        inputStream: java.io.InputStream,
        displayName: String,
        mime: String,
        size: Long,
        onProgress: (Long, Long) -> Unit
    ) {
        Socket().use { socket ->
            socket.tcpNoDelay = true
            socket.sendBufferSize = 1024 * 1024
            socket.receiveBufferSize = 256 * 1024
            socket.connect(InetSocketAddress(peer.host, peer.port), 4000)
            socket.soTimeout = 120_000
            val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream(), 128 * 1024))
            val input = DataInputStream(BufferedInputStream(socket.getInputStream(), 16 * 1024))
            output.writeUTF("JSHARE1")
            output.writeUTF(UUID.randomUUID().toString())
            output.writeUTF(UriTools.sanitize(displayName))
            output.writeUTF(mime)
            output.writeLong(size)
            output.flush()
            val ready = input.readUTF()
            check(ready == "READY") { "接收端未准备好：$ready" }

            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(128 * 1024)
            var sent = 0L
            while (sent < size) {
                val want = minOf(buffer.size.toLong(), size - sent).toInt()
                val read = inputStream.read(buffer, 0, want)
                if (read < 0) throw IllegalStateException("文件读取提前结束")
                output.write(buffer, 0, read)
                digest.update(buffer, 0, read)
                sent += read
                onProgress(sent, size)
            }
            output.write(digest.digest())
            output.flush()
            val result = input.readUTF()
            check(result == "DONE") { if (result == "HASH_MISMATCH") "文件校验失败" else result }
        }
    }

    private fun receiveOne(socket: Socket) {
        socket.use { s ->
            try {
                s.soTimeout = 120_000
                s.receiveBufferSize = 1024 * 1024
                val input = DataInputStream(BufferedInputStream(s.getInputStream(), 128 * 1024))
                val output = DataOutputStream(BufferedOutputStream(s.getOutputStream(), 16 * 1024))
                if (input.readUTF() != "JSHARE1") return
                input.readUTF()
                val fileName = UriTools.sanitize(input.readUTF())
                val mime = input.readUTF().ifBlank { UriTools.mimeForName(fileName) }
                val size = input.readLong()
                require(size >= 0L && size <= 40L * 1024 * 1024 * 1024) { "文件大小异常" }

                val incomingDir = File(context.cacheDir, "incoming").apply { mkdirs() }
                val cacheFile = File(incomingDir, "${System.currentTimeMillis()}-$fileName")
                output.writeUTF("READY")
                output.flush()

                val digest = MessageDigest.getInstance("SHA-256")
                FileOutputStream(cacheFile).buffered(128 * 1024).use { fileOut ->
                    val buffer = ByteArray(128 * 1024)
                    var received = 0L
                    while (received < size) {
                        val want = minOf(buffer.size.toLong(), size - received).toInt()
                        val read = input.read(buffer, 0, want)
                        if (read < 0) throw IllegalStateException("连接提前中断")
                        fileOut.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        received += read
                    }
                }
                val expected = ByteArray(32)
                input.readFully(expected)
                val ok = MessageDigest.isEqual(expected, digest.digest())
                if (!ok) {
                    cacheFile.delete()
                    output.writeUTF("HASH_MISMATCH")
                    output.flush()
                    return
                }
                val saved = saveToDownloads(cacheFile, fileName, mime)
                output.writeUTF("DONE")
                output.flush()
                onReceived(ReceiveResult(fileName, mime, cacheFile, saved.first, saved.second))
            } catch (e: Throwable) {
                onReceiveError(e.message ?: "接收失败")
            }
        }
    }

    private fun saveToDownloads(source: File, fileName: String, mime: String): Pair<Uri?, String?> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/JShare")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return null to null
            try {
                context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                    FileInputStream(source).buffered(128 * 1024).use { input -> input.copyTo(output, 128 * 1024) }
                } ?: throw IllegalStateException("无法写入下载目录")
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                uri to "Download/JShare/$fileName"
            } catch (e: Throwable) {
                runCatching { context.contentResolver.delete(uri, null, null) }
                throw e
            }
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "JShare").apply { mkdirs() }
            val target = File(dir, fileName)
            source.copyTo(target, overwrite = true)
            null to target.absolutePath
        }
    }
}
