package com.dream.ourphone.capability

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.os.Bundle
import android.provider.AlarmClock
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class ActionExecutor(private val service: AccessibilityService) {

    suspend fun tap(x: Int, y: Int): Boolean {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture)
    }

    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long = 300): Boolean {
        val path = Path().apply {
            moveTo(x1.toFloat(), y1.toFloat())
            lineTo(x2.toFloat(), y2.toFloat())
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture)
    }

    suspend fun longPress(x: Int, y: Int, durationMs: Long = 1000): Boolean {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture)
    }

    fun tapText(targetText: String): Boolean {
        val rootNode = service.rootInActiveWindow ?: return false
        val found = findNodeByText(rootNode, targetText)
        if (found != null) {
            val result = found.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            found.recycle()
            rootNode.recycle()
            return result
        }
        rootNode.recycle()
        return false
    }

    fun inputText(text: String): Boolean {
        val rootNode = service.rootInActiveWindow ?: return false
        val focused = findFocusedInput(rootNode)
        if (focused != null) {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            val result = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            focused.recycle()
            rootNode.recycle()
            return result
        }
        rootNode.recycle()
        return false
    }

    fun openApp(packageName: String): Boolean {
        val intent = service.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        service.startActivity(intent)
        return true
    }

    fun goHome() {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    }

    fun goBack() {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    }

    fun openRecents() {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
    }

    fun openNotifications() {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
    }

    fun openQuickSettings() {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
    }

    fun setAlarm(hour: Int, minute: Int, message: String? = null) {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            message?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        service.startActivity(intent)
    }

    fun setWallpaper(text: String, isLockScreen: Boolean = true) {
        val width = service.resources.displayMetrics.widthPixels
        val height = service.resources.displayMetrics.heightPixels
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#1a1a2e"))

        val paint = Paint().apply {
            color = Color.parseColor("#e8d5b7")
            textSize = 48f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT
        }
        canvas.drawText(text, width / 2f, height / 2f, paint)

        val wm = WallpaperManager.getInstance(service)
        val flag = if (isLockScreen) {
            WallpaperManager.FLAG_LOCK
        } else {
            WallpaperManager.FLAG_SYSTEM
        }
        wm.setBitmap(bitmap, null, true, flag)
        bitmap.recycle()
    }

    fun takeScreenshot(callback: (Bitmap?) -> Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            service.takeScreenshot(
                android.view.Display.DEFAULT_DISPLAY,
                service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        val hwBitmap = Bitmap.wrapHardwareBuffer(
                            result.hardwareBuffer, result.colorSpace
                        )
                        val bitmap = hwBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                        hwBitmap?.recycle()
                        result.hardwareBuffer.close()
                        callback(bitmap)
                    }
                    override fun onFailure(errorCode: Int) {
                        callback(null)
                    }
                }
            )
        } else {
            callback(null)
        }
    }

    private suspend fun dispatchGesture(gesture: GestureDescription): Boolean {
        return suspendCancellableCoroutine { cont ->
            val dispatched = service.dispatchGesture(
                gesture,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(true)
                    }
                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(false)
                    }
                },
                null
            )
            if (!dispatched && cont.isActive) cont.resume(false)
        }
    }

    private fun findNodeByText(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        if (node.text?.toString()?.contains(text, ignoreCase = true) == true
            || node.contentDescription?.toString()?.contains(text, ignoreCase = true) == true) {
            return AccessibilityNodeInfo.obtain(node)
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                val found = findNodeByText(child, text)
                if (found != null) {
                    child.recycle()
                    return found
                }
                child.recycle()
            }
        }
        return null
    }

    private fun findFocusedInput(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isFocused && node.isEditable) {
            return AccessibilityNodeInfo.obtain(node)
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                val found = findFocusedInput(child)
                if (found != null) {
                    child.recycle()
                    return found
                }
                child.recycle()
            }
        }
        return null
    }
}
