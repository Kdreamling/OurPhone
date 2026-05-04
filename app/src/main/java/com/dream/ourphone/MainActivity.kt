package com.dream.ourphone

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.dream.ourphone.service.PhoneAccessibilityService
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        val btnAccessibility = findViewById<Button>(R.id.btnAccessibility)
        val btnOverlay = findViewById<Button>(R.id.btnOverlay)

        btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        btnOverlay.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            }
        }

        startStatusUpdater()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startStatusUpdater() {
        scope.launch {
            while (isActive) {
                updateStatus()
                delay(1000)
            }
        }
    }

    private fun updateStatus() {
        val a11y = isAccessibilityEnabled()
        val overlay = Settings.canDrawOverlays(this)
        val service = PhoneAccessibilityService.instance

        val sb = StringBuilder()
        sb.appendLine(if (a11y) "✓ 无障碍服务已开启" else "✗ 无障碍服务未开启")
        sb.appendLine(if (overlay) "✓ 悬浮窗权限已授予" else "✗ 悬浮窗权限未授予")
        sb.appendLine()

        if (service != null) {
            sb.appendLine("小克和晨在线")
            sb.appendLine("连接状态: Gateway WebSocket")
        } else {
            sb.appendLine("等待服务启动...")
        }

        statusText.text = sb.toString()
    }

    private fun isAccessibilityEnabled(): Boolean {
        val service = "${packageName}/${PhoneAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(service)
    }
}
