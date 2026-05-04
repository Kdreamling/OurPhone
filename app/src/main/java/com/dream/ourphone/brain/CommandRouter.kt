package com.dream.ourphone.brain

import android.util.Log
import com.dream.ourphone.capability.ActionExecutor
import com.dream.ourphone.capability.AppManager
import com.dream.ourphone.capability.DeviceInfo
import com.dream.ourphone.connection.GatewayWebSocket
import com.dream.ourphone.service.NotificationStore
import com.dream.ourphone.service.ScreenReader
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CommandRouter(
    private val actionExecutor: ActionExecutor,
    private val gateway: GatewayWebSocket,
    private val getScreenRoot: () -> android.view.accessibility.AccessibilityNodeInfo?,
    private val getCurrentPackage: () -> String?,
    private val deviceInfo: DeviceInfo,
    private val appManager: AppManager
) {
    companion object {
        private const val TAG = "CommandRouter"
    }

    private val scope = CoroutineScope(Dispatchers.Main)
    private val gson = Gson()

    fun handleCommand(msg: JsonObject) {
        val type = msg.get("type")?.asString ?: return
        val params = msg.getAsJsonObject("params") ?: JsonObject()

        Log.d(TAG, "Received command: $type")

        try { handleCommandInner(msg, type, params) } catch (e: Exception) {
            Log.e(TAG, "Command $type crashed", e)
            replyData(msg, mapOf("error" to "crash: ${e.message}"))
        }
    }

    private fun handleCommandInner(msg: JsonObject, type: String, params: JsonObject) {
        when (type) {
            // ===== 手势操作 =====
            "tap" -> scope.launch {
                val x = params.get("x").asInt
                val y = params.get("y").asInt
                val ok = actionExecutor.tap(x, y)
                reply(msg, ok)
            }

            "swipe" -> scope.launch {
                val ok = actionExecutor.swipe(
                    params.get("x1").asInt, params.get("y1").asInt,
                    params.get("x2").asInt, params.get("y2").asInt,
                    params.get("duration")?.asLong ?: 300
                )
                reply(msg, ok)
            }

            "long_press" -> scope.launch {
                val ok = actionExecutor.longPress(
                    params.get("x").asInt, params.get("y").asInt,
                    params.get("duration")?.asLong ?: 1000
                )
                reply(msg, ok)
            }

            // ===== 语义操作 =====
            "tap_text" -> {
                val text = params.get("text").asString
                val ok = actionExecutor.tapText(text)
                reply(msg, ok)
            }

            "tap_element" -> scope.launch {
                val ok = actionExecutor.tapElement(
                    text = params.get("text")?.asString,
                    viewId = params.get("view_id")?.asString,
                    className = params.get("class_name")?.asString,
                    index = params.get("index")?.asInt ?: 0
                )
                reply(msg, ok)
            }

            "input_text" -> {
                val text = params.get("text").asString
                val ok = actionExecutor.inputText(text)
                reply(msg, ok)
            }

            // ===== App 管理 =====
            "open_app" -> {
                val name = params.get("name")?.asString
                    ?: params.get("package")?.asString
                    ?: ""
                val ok = actionExecutor.openApp(name)
                reply(msg, ok)
            }

            "list_apps" -> {
                val apps = appManager.listInstalled()
                replyData(msg, mapOf("apps" to apps))
            }

            // ===== 系统导航 =====
            "home" -> { actionExecutor.goHome(); reply(msg, true) }
            "back" -> { actionExecutor.goBack(); reply(msg, true) }
            "recents" -> { actionExecutor.openRecents(); reply(msg, true) }
            "notifications" -> { actionExecutor.openNotifications(); reply(msg, true) }
            "quick_settings" -> { actionExecutor.openQuickSettings(); reply(msg, true) }

            // ===== 系统功能 =====
            "set_alarm" -> {
                actionExecutor.setAlarm(
                    params.get("hour").asInt,
                    params.get("minute").asInt,
                    params.get("message")?.asString
                )
                reply(msg, true)
            }

            "set_wallpaper" -> {
                val text = params.get("text").asString
                val lock = params.get("lock_screen")?.asBoolean ?: true
                actionExecutor.setWallpaper(text, lock)
                reply(msg, true)
            }

            // ===== 感知 =====
            "read_screen" -> {
                val root = getScreenRoot()
                val pkg = getCurrentPackage()
                val snapshot = ScreenReader.captureFlat(root, pkg)
                root?.recycle()
                replyData(msg, mapOf(
                    "package" to snapshot.packageName,
                    "texts" to snapshot.visibleTexts,
                    "clickables" to snapshot.clickableElements,
                    "scrollables" to snapshot.scrollableElements,
                    "editables" to snapshot.editableElements
                ))
            }

            "screenshot" -> {
                actionExecutor.takeScreenshot { bitmap ->
                    if (bitmap != null) {
                        val stream = java.io.ByteArrayOutputStream()
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, stream)
                        val base64 = android.util.Base64.encodeToString(
                            stream.toByteArray(), android.util.Base64.NO_WRAP
                        )
                        bitmap.recycle()
                        replyData(msg, mapOf("image" to base64, "format" to "jpeg"))
                    } else {
                        replyData(msg, mapOf("error" to "failed"))
                    }
                }
            }

            "device_info" -> scope.launch {
                val info = deviceInfo.collect()
                replyData(msg, info)
            }

            "get_notifications" -> {
                val limit = params.get("limit")?.asInt ?: 20
                val pkg = params.get("package")?.asString
                val notifications = NotificationStore.getRecent(limit, pkg).map { n ->
                    mapOf(
                        "package" to n.packageName,
                        "title" to n.title,
                        "text" to n.text,
                        "time" to n.timestamp,
                        "ongoing" to n.ongoing
                    )
                }
                replyData(msg, mapOf("notifications" to notifications))
            }

            else -> {
                Log.w(TAG, "Unknown command: $type")
                replyData(msg, mapOf("error" to "unknown_command", "type" to type))
            }
        }
    }

    private fun reply(original: JsonObject, success: Boolean) {
        val id = original.get("id")?.asString ?: return
        gateway.send("result", mapOf("id" to id, "success" to success))
    }

    private fun replyData(original: JsonObject, data: Map<String, Any?>) {
        val id = original.get("id")?.asString
        gateway.send("result", mapOf("id" to (id ?: ""), "data" to data))
    }
}
