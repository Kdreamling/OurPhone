package com.dream.ourphone.brain

import android.util.Log
import com.dream.ourphone.capability.ActionExecutor
import com.dream.ourphone.connection.GatewayWebSocket
import com.dream.ourphone.service.ScreenReader
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CommandRouter(
    private val actionExecutor: ActionExecutor,
    private val gateway: GatewayWebSocket,
    private val getScreenRoot: () -> android.view.accessibility.AccessibilityNodeInfo?,
    private val getCurrentPackage: () -> String?
) {
    companion object {
        private const val TAG = "CommandRouter"
    }

    private val scope = CoroutineScope(Dispatchers.Main)

    fun handleCommand(msg: JsonObject) {
        val type = msg.get("type")?.asString ?: return
        val params = msg.getAsJsonObject("params") ?: JsonObject()

        Log.d(TAG, "Received command: $type")

        when (type) {
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

            "tap_text" -> {
                val text = params.get("text").asString
                val ok = actionExecutor.tapText(text)
                reply(msg, ok)
            }

            "input_text" -> {
                val text = params.get("text").asString
                val ok = actionExecutor.inputText(text)
                reply(msg, ok)
            }

            "open_app" -> {
                val pkg = params.get("package").asString
                val ok = actionExecutor.openApp(pkg)
                reply(msg, ok)
            }

            "home" -> { actionExecutor.goHome(); reply(msg, true) }
            "back" -> { actionExecutor.goBack(); reply(msg, true) }
            "recents" -> { actionExecutor.openRecents(); reply(msg, true) }
            "notifications" -> { actionExecutor.openNotifications(); reply(msg, true) }
            "quick_settings" -> { actionExecutor.openQuickSettings(); reply(msg, true) }

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

            "read_screen" -> {
                val root = getScreenRoot()
                val pkg = getCurrentPackage()
                val snapshot = ScreenReader.captureFlat(root, pkg)
                root?.recycle()
                gateway.send("screen_content", snapshot)
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
                        gateway.send("screenshot_result", mapOf(
                            "image" to base64,
                            "format" to "jpeg"
                        ))
                    } else {
                        gateway.send("screenshot_result", mapOf("error" to "failed"))
                    }
                }
            }

            else -> Log.w(TAG, "Unknown command: $type")
        }
    }

    private fun reply(original: JsonObject, success: Boolean) {
        val id = original.get("id")?.asString ?: return
        gateway.send("result", mapOf("id" to id, "success" to success))
    }
}
