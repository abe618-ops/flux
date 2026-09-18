package com.flux.jshare

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

class MainActivity : Activity() {
    companion object {
        private const val REQ_FILES = 1001
        private const val REQ_MEDIA = 1002
        private const val REQ_APP = 1003
    }

    private sealed class PendingItem {
        data class UriItem(val uri: Uri, val name: String, val mime: String) : PendingItem()
        data class FileItem(val file: File, val name: String, val mime: String) : PendingItem()
    }

    private val pending = mutableListOf<PendingItem>()
    private val sending = AtomicBoolean(false)
    private lateinit var discovery: DiscoveryManager
    private lateinit var transfer: TransferEngine
    private lateinit var peersBox: LinearLayout
    private lateinit var selectedView: TextView
    private lateinit var progressView: TextView
    private lateinit var nearbyCaption: TextView
    private var peers: List<Peer> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        discovery = DiscoveryManager(this) { list ->
            peers = list
            renderPeers()
        }
        transfer = TransferEngine(
            this,
            onReceived = { result -> runOnUiThread { showReceived(result) } },
            onReceiveError = { msg -> runOnUiThread { progressView.text = "接收异常：$msg" } }
        )
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        discovery.start()
        transfer.startServer()
        progressView.text = "同一 Wi‑Fi 或热点下会自动发现设备"
    }

    override fun onStop() {
        discovery.stop()
        transfer.stopServer()
        super.onStop()
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(247, 249, 252)) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(28))
        }
        val title = TextView(this).apply {
            text = "极简分享"
            textSize = 29f
            setTextColor(Color.rgb(23, 32, 51))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val subtitle = TextView(this).apply {
            text = "${Build.MODEL} · 局域网直传 · 不用账号"
            textSize = 14f
            setTextColor(Color.rgb(102, 112, 133))
            setPadding(0, dp(4), 0, dp(18))
        }
        root.addView(title)
        root.addView(subtitle)

        root.addView(sectionTitle("选择要发送的内容"))
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(actionButton("文件") { chooseFiles("*/*", REQ_FILES) }, weightParams())
        buttons.addView(space(dp(8)))
        buttons.addView(actionButton("照片/视频") { chooseFiles("image/*", REQ_MEDIA, media = true) }, weightParams())
        buttons.addView(space(dp(8)))
        buttons.addView(actionButton("应用") { startActivityForResult(Intent(this@MainActivity, AppPickerActivity::class.java), REQ_APP) }, weightParams())
        root.addView(buttons)

        selectedView = TextView(this).apply {
            text = "尚未选择文件"
            textSize = 15f
            setTextColor(Color.rgb(102, 112, 133))
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setBackgroundColor(Color.WHITE)
            setOnLongClickListener {
                if (pending.isEmpty() || sending.get()) return@setOnLongClickListener false
                val clip = ClipData.newPlainText("jshare", "send")
                startDragAndDrop(clip, View.DragShadowBuilder(this), null, 0)
                Toast.makeText(this@MainActivity, "拖到设备上即可发送", Toast.LENGTH_SHORT).show()
                true
            }
        }
        val selectedLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(10)
        }
        root.addView(selectedView, selectedLp)

        nearbyCaption = sectionTitle("附近设备").apply { setPadding(0, dp(22), 0, dp(8)) }
        root.addView(nearbyCaption)
        peersBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(peersBox)

        progressView = TextView(this).apply {
            text = "打开另一台手机的“极简分享”即可自动发现"
            textSize = 13f
            setTextColor(Color.rgb(102, 112, 133))
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(22), 0, 0)
        }
        root.addView(progressView)
        scroll.addView(root)
        setContentView(scroll)
        renderPeers()
    }

    private fun renderPeers() {
        if (!::peersBox.isInitialized) return
        peersBox.removeAllViews()
        nearbyCaption.text = if (peers.isEmpty()) "附近设备" else "附近设备 · ${peers.size}"
        if (peers.isEmpty()) {
            peersBox.addView(TextView(this).apply {
                text = "正在搜索…\n请让另一台手机保持在同一 Wi‑Fi / 热点，并打开极简分享。"
                textSize = 15f
                setTextColor(Color.rgb(102, 112, 133))
                setPadding(dp(14), dp(18), dp(14), dp(18))
                setBackgroundColor(Color.WHITE)
            })
            return
        }
        peers.forEach { peer ->
            val button = Button(this).apply {
                text = "${peer.name}\n${peer.host}"
                textSize = 16f
                isAllCaps = false
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(10), dp(16), dp(10))
                setOnClickListener { sendSelected(peer) }
                setOnDragListener { _, event ->
                    when (event.action) {
                        DragEvent.ACTION_DRAG_STARTED -> pending.isNotEmpty() && !sending.get()
                        DragEvent.ACTION_DRAG_ENTERED -> { alpha = 0.72f; true }
                        DragEvent.ACTION_DRAG_EXITED -> { alpha = 1f; true }
                        DragEvent.ACTION_DROP -> { alpha = 1f; sendSelected(peer); true }
                        DragEvent.ACTION_DRAG_ENDED -> { alpha = 1f; true }
                        else -> true
                    }
                }
            }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(62)).apply { bottomMargin = dp(8) }
            peersBox.addView(button, lp)
        }
    }

    private fun chooseFiles(type: String, requestCode: Int, media: Boolean = false) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            this.type = type
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            if (media) putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
        }
        startActivityForResult(intent, requestCode)
    }

    @Deprecated("Deprecated in Android API, kept for a dependency-free activity result flow")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        when (requestCode) {
            REQ_FILES, REQ_MEDIA -> {
                val uris = mutableListOf<Uri>()
                data.clipData?.let { clip -> for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let(uris::add) }
                data.data?.let(uris::add)
                uris.distinct().forEach { addUri(it, data.flags) }
                updateSelected()
            }
            REQ_APP -> {
                val path = data.getStringExtra("path") ?: return
                val file = File(path)
                if (!file.exists()) return
                pending.clear()
                pending += PendingItem.FileItem(
                    file,
                    data.getStringExtra("name") ?: file.name,
                    data.getStringExtra("mime") ?: UriTools.mimeForName(file.name)
                )
                updateSelected()
            }
        }
    }

    private fun addUri(uri: Uri, grantFlags: Int = 0) {
        runCatching {
            val takeFlags = grantFlags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            if (takeFlags != 0) contentResolver.takePersistableUriPermission(uri, takeFlags)
        }
        val name = UriTools.displayName(this, uri)
        pending += PendingItem.UriItem(uri, name, UriTools.mime(this, uri, name))
    }

    private fun handleIncomingIntent(incoming: Intent?) {
        if (incoming == null) return
        when (incoming.action) {
            Intent.ACTION_SEND -> {
                pending.clear()
                val uri = getStreamUri(incoming)
                if (uri != null) {
                    addUri(uri, incoming.flags)
                } else {
                    incoming.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let { text ->
                        val dir = File(cacheDir, "shared").apply { mkdirs() }
                        val file = File(dir, "分享文字-${System.currentTimeMillis()}.txt").apply { writeText(text) }
                        pending += PendingItem.FileItem(file, file.name, "text/plain")
                    }
                }
                updateSelected()
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                pending.clear()
                val list = getStreamUris(incoming)
                list.orEmpty().forEach { addUri(it, incoming.flags) }
                updateSelected()
            }
        }
    }

    private fun updateSelected() {
        selectedView.text = when (pending.size) {
            0 -> "尚未选择文件"
            1 -> "已选择：${itemName(pending.first())}\n长按这里可拖到目标设备"
            else -> "已选择 ${pending.size} 个文件\n长按这里可拖到目标设备"
        }
        selectedView.setTextColor(if (pending.isEmpty()) Color.rgb(102, 112, 133) else Color.rgb(23, 32, 51))
    }

    private fun itemName(item: PendingItem): String = when (item) {
        is PendingItem.UriItem -> item.name
        is PendingItem.FileItem -> item.name
    }

    private fun sendSelected(peer: Peer) {
        if (pending.isEmpty()) {
            Toast.makeText(this, "先选择文件、照片或应用", Toast.LENGTH_SHORT).show()
            return
        }
        if (!sending.compareAndSet(false, true)) return
        val items = pending.toList()
        sendNext(peer, items, 0)
    }

    private fun sendNext(peer: Peer, items: List<PendingItem>, index: Int) {
        if (index >= items.size) {
            sending.set(false)
            runOnUiThread {
                progressView.text = "已发送到 ${peer.name} · ${items.size} 个文件"
                Toast.makeText(this, "发送完成", Toast.LENGTH_SHORT).show()
            }
            return
        }
        val item = items[index]
        var lastPercent = -1
        val progress: (Long, Long) -> Unit = { sent, total ->
            val percent = if (total > 0) ((sent * 100.0) / total).roundToInt().coerceIn(0, 100) else 0
            if (percent != lastPercent && (percent % 2 == 0 || percent == 100)) {
                lastPercent = percent
                runOnUiThread { progressView.text = "${peer.name} · ${index + 1}/${items.size} · $percent%" }
            }
        }
        val done = { sendNext(peer, items, index + 1) }
        val error: (String) -> Unit = { message ->
            sending.set(false)
            runOnUiThread {
                progressView.text = "发送失败：$message"
                Toast.makeText(this, "发送失败：$message", Toast.LENGTH_LONG).show()
            }
        }
        runOnUiThread { progressView.text = "正在连接 ${peer.name}…" }
        when (item) {
            is PendingItem.UriItem -> transfer.sendUri(peer, item.uri, progress, done, error)
            is PendingItem.FileItem -> transfer.sendFile(peer, item.file, item.name, item.mime, progress, done, error)
        }
    }

    private fun showReceived(result: TransferEngine.ReceiveResult) {
        progressView.text = "已接收：${result.fileName}"
        val isPackage = result.fileName.endsWith(".apk", true) || result.fileName.endsWith(".apks", true)
        val builder = AlertDialog.Builder(this)
            .setTitle("接收完成")
            .setMessage("${result.fileName}\n已保存到 ${result.publicPath ?: "应用目录"}")
            .setNegativeButton("完成", null)
        if (isPackage) {
            builder.setPositiveButton("安装") { _, _ -> ApkInstaller.install(this, result.cacheFile) }
        } else if (result.publicUri != null) {
            builder.setPositiveButton("打开") { _, _ ->
                val view = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(result.publicUri, result.mime)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runCatching { startActivity(view) }.onFailure {
                    Toast.makeText(this, "没有可打开此文件的应用", Toast.LENGTH_LONG).show()
                }
            }
        }
        builder.show()
    }

    private fun getStreamUri(intent: Intent): Uri? {
        return if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        }
    }

    private fun getStreamUris(intent: Intent): ArrayList<Uri>? {
        return if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
        }
    }

    private fun sectionTitle(textValue: String) = TextView(this).apply {
        text = textValue
        textSize = 17f
        setTextColor(Color.rgb(23, 32, 51))
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, 0, 0, dp(8))
    }

    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 15f
        isAllCaps = false
        setOnClickListener { action() }
    }

    private fun weightParams() = LinearLayout.LayoutParams(0, dp(52), 1f)
    private fun space(width: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(width, 1) }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
