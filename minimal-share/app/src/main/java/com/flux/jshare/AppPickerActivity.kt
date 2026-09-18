package com.flux.jshare

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

class AppPickerActivity : Activity() {
    data class AppRow(val label: String, val packageName: String, val icon: Drawable?)

    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var listView: ListView
    private lateinit var statusView: TextView
    private var rows: List<AppRow> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "选择应用"
        buildUi()
        loadApps()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(12))
            setBackgroundColor(Color.WHITE)
        }
        val title = TextView(this).apply {
            text = "选择要分享的应用"
            textSize = 23f
            setTextColor(Color.rgb(23, 32, 51))
        }
        statusView = TextView(this).apply {
            text = "正在读取可启动应用…"
            textSize = 14f
            setTextColor(Color.rgb(102, 112, 133))
            setPadding(0, dp(6), 0, dp(12))
        }
        listView = ListView(this).apply {
            dividerHeight = 0
            setOnItemClickListener { _, _, position, _ -> export(rows[position]) }
        }
        root.addView(title)
        root.addView(statusView)
        root.addView(listView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun loadApps() {
        executor.execute {
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolved: List<ResolveInfo> = packageManager.queryIntentActivities(launcherIntent, 0)
            val unique = LinkedHashMap<String, AppRow>()
            for (info in resolved) {
                val pkg = info.activityInfo?.packageName ?: continue
                if (unique.containsKey(pkg)) continue
                val label = runCatching { info.loadLabel(packageManager).toString() }.getOrDefault(pkg)
                val icon = runCatching { info.loadIcon(packageManager) }.getOrNull()
                unique[pkg] = AppRow(label, pkg, icon)
            }
            rows = unique.values.sortedBy { it.label.lowercase() }
            runOnUiThread {
                statusView.text = "${rows.size} 个应用 · 点击即可准备发送"
                listView.adapter = AppAdapter(rows)
            }
        }
    }

    private fun export(row: AppRow) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("正在准备")
            .setMessage("正在提取 ${row.label}…")
            .setCancelable(false)
            .create()
        dialog.show()
        executor.execute {
            try {
                val exported = AppExporter.export(this, row.packageName)
                runOnUiThread {
                    dialog.dismiss()
                    setResult(RESULT_OK, Intent().apply {
                        putExtra("path", exported.file.absolutePath)
                        putExtra("name", exported.displayName)
                        putExtra("mime", exported.mime)
                    })
                    finish()
                }
            } catch (e: Throwable) {
                runOnUiThread {
                    dialog.dismiss()
                    Toast.makeText(this, "提取失败：${e.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private inner class AppAdapter(private val data: List<AppRow>) : BaseAdapter() {
        override fun getCount() = data.size
        override fun getItem(position: Int) = data[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = data[position]
            val layout = LinearLayout(this@AppPickerActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(4), dp(9), dp(4), dp(9))
            }
            val icon = ImageView(this@AppPickerActivity).apply {
                setImageDrawable(row.icon)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
            }
            val textBox = LinearLayout(this@AppPickerActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, 0, 0)
            }
            val label = TextView(this@AppPickerActivity).apply {
                text = row.label
                textSize = 17f
                setTextColor(Color.rgb(23, 32, 51))
            }
            val pkg = TextView(this@AppPickerActivity).apply {
                text = row.packageName
                textSize = 12f
                setTextColor(Color.rgb(102, 112, 133))
            }
            textBox.addView(label)
            textBox.addView(pkg)
            layout.addView(icon, LinearLayout.LayoutParams(dp(44), dp(44)))
            layout.addView(textBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            return layout
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
