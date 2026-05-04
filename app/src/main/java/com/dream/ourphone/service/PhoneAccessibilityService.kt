package com.dream.ourphone.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.dream.ourphone.brain.CommandRouter
import com.dream.ourphone.capability.ActionExecutor
import com.dream.ourphone.capability.AppManager
import com.dream.ourphone.capability.DeviceInfo
import com.dream.ourphone.connection.GatewayWebSocket
import com.google.gson.Gson
import kotlinx.coroutines.*

class PhoneAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "PhoneA11y"
        private const val GATEWAY_URL = "ws://100.87.90.105:8001/ws/phone"
        private const val SCREEN_REPORT_INTERVAL_MS = 2000L

        var instance: PhoneAccessibilityService? = null
            private set
    }

    private lateinit var gateway: GatewayWebSocket
    private lateinit var actionExecutor: ActionExecutor
    private lateinit var commandRouter: CommandRouter
    private lateinit var appManager: AppManager
    private lateinit var deviceInfo: DeviceInfo
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val gson = Gson()

    private var currentPackage: String? = null
    private var lastReportTime = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "Service connected — 小克和晨上线了")

        appManager = AppManager(this)
        deviceInfo = DeviceInfo(this)
        actionExecutor = ActionExecutor(this).apply {
            this.appManager = this@PhoneAccessibilityService.appManager
        }

        gateway = GatewayWebSocket(GATEWAY_URL) { msg ->
            scope.launch { commandRouter.handleCommand(msg) }
        }

        commandRouter = CommandRouter(
            actionExecutor = actionExecutor,
            gateway = gateway,
            getScreenRoot = { rootInActiveWindow },
            getCurrentPackage = { currentPackage },
            deviceInfo = deviceInfo,
            appManager = appManager
        )

        gateway.connect()
        startHeartbeat()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val pkg = event.packageName?.toString()
                if (pkg != null && pkg != currentPackage) {
                    currentPackage = pkg
                    Log.d(TAG, "Window changed: $pkg")
                    reportScreenThrottled()
                }
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                reportScreenThrottled()
            }

            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                val parcel = event.parcelableData
                if (parcel is android.app.Notification) {
                    val title = parcel.extras?.getString("android.title")
                    val text = parcel.extras?.getString("android.text")
                    val pkg = event.packageName?.toString() ?: "unknown"
                    val ongoing = (parcel.flags and android.app.Notification.FLAG_ONGOING_EVENT) != 0

                    NotificationStore.add(StoredNotification(
                        packageName = pkg,
                        title = title,
                        text = text,
                        timestamp = System.currentTimeMillis(),
                        ongoing = ongoing
                    ))

                    gateway.sendNotification(pkg, title, text, System.currentTimeMillis())
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Service interrupted")
    }

    override fun onDestroy() {
        instance = null
        gateway.disconnect()
        scope.cancel()
        super.onDestroy()
    }

    fun getGateway(): GatewayWebSocket = gateway
    fun getExecutor(): ActionExecutor = actionExecutor

    private fun reportScreenThrottled() {
        val now = System.currentTimeMillis()
        if (now - lastReportTime < SCREEN_REPORT_INTERVAL_MS) return
        lastReportTime = now

        scope.launch(Dispatchers.Default) {
            try {
                val root = rootInActiveWindow ?: return@launch
                val snapshot = ScreenReader.captureFlat(root, currentPackage)
                root.recycle()
                gateway.sendScreenUpdate(currentPackage ?: "unknown", mapOf(
                    "package" to snapshot.packageName,
                    "texts" to snapshot.visibleTexts,
                    "clickables" to snapshot.clickableElements,
                    "scrollables" to snapshot.scrollableElements
                ))
            } catch (e: Exception) {
                Log.e(TAG, "Screen report failed", e)
            }
        }
    }

    private fun startHeartbeat() {
        scope.launch {
            while (isActive) {
                delay(30_000)
                gateway.sendHeartbeat()
            }
        }
    }
}
