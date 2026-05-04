package com.dream.ourphone.connection

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import okhttp3.*

class GatewayWebSocket(
    private val gatewayUrl: String,
    private val onCommand: (JsonObject) -> Unit
) {
    companion object {
        private const val TAG = "GatewayWS"
        private const val RECONNECT_DELAY_MS = 5000L
        private const val PING_INTERVAL_MS = 30000L
    }

    private val client = OkHttpClient.Builder()
        .pingInterval(PING_INTERVAL_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
        .build()
    private val gson = Gson()
    private var webSocket: WebSocket? = null
    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var shouldReconnect = true

    fun connect() {
        shouldReconnect = true
        doConnect()
    }

    fun disconnect() {
        shouldReconnect = false
        webSocket?.close(1000, "bye")
        webSocket = null
        scope.cancel()
    }

    fun send(type: String, data: Map<String, Any?>) {
        val msg = mapOf("type" to type, "data" to data)
        val json = gson.toJson(msg)
        webSocket?.send(json) ?: Log.w(TAG, "WebSocket not connected, dropping: $type")
    }

    fun sendScreenUpdate(packageName: String, flatSnapshot: Map<String, Any>) {
        send("screen_update", flatSnapshot)
    }

    fun sendNotification(pkg: String, title: String?, text: String?, time: Long) {
        send("notification", mapOf(
            "package" to pkg,
            "title" to title,
            "text" to text,
            "time" to time
        ))
    }

    fun sendHeartbeat() {
        send("heartbeat", mapOf("timestamp" to System.currentTimeMillis()))
    }

    private fun doConnect() {
        val request = Request.Builder().url(gatewayUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.i(TAG, "Connected to Gateway")
                send("hello", mapOf(
                    "device" to "xiaomi9",
                    "version" to "0.1.0",
                    "capabilities" to listOf(
                        "screen_read", "tap", "swipe", "type", "open_app",
                        "home", "back", "recents", "notifications",
                        "set_alarm", "set_wallpaper", "screenshot"
                    )
                ))
            }

            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val msg = gson.fromJson(text, JsonObject::class.java)
                    onCommand(msg)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse message: $text", e)
                }
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "Connection closing: $code $reason")
                ws.close(code, reason)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "Connection closed: $code $reason")
                scheduleReconnect()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Connection failed", t)
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (!shouldReconnect) return
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope.launch {
            delay(RECONNECT_DELAY_MS)
            Log.i(TAG, "Reconnecting...")
            doConnect()
        }
    }
}
