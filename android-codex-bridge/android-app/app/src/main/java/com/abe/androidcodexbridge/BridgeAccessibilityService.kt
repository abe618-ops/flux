package com.abe.androidcodexbridge

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject

class BridgeAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var instance: BridgeAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    fun performBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun performHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun performRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)

    fun tap(x: Float, y: Float, durationMs: Long = 80L): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 350L): Boolean {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun uiTreeJson(maxDepth: Int = 30): JSONObject {
        val root = rootInActiveWindow ?: return JSONObject().put("available", false)
        return JSONObject()
            .put("available", true)
            .put("package", root.packageName?.toString())
            .put("root", nodeToJson(root, 0, maxDepth))
    }

    private fun nodeToJson(node: AccessibilityNodeInfo, depth: Int, maxDepth: Int): JSONObject {
        val bounds = android.graphics.Rect().also(node::getBoundsInScreen)
        val json = JSONObject()
            .put("class", node.className?.toString())
            .put("text", node.text?.toString())
            .put("description", node.contentDescription?.toString())
            .put("viewId", node.viewIdResourceName)
            .put("clickable", node.isClickable)
            .put("editable", node.isEditable)
            .put("enabled", node.isEnabled)
            .put("bounds", JSONArray(listOf(bounds.left, bounds.top, bounds.right, bounds.bottom)))

        if (depth >= maxDepth) return json
        val children = JSONArray()
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                children.put(nodeToJson(child, depth + 1, maxDepth))
                child.recycle()
            }
        }
        json.put("children", children)
        return json
    }
}
