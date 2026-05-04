package com.dream.ourphone

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
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

        val btnBattery = findViewById<Button>(R.id.btnBattery)
        btnBattery.setOnClickListener {
            requestBatteryWhitelist()
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
        val battery = isBatteryOptimized()
        val service = PhoneAccessibilityService.instance

        val sb = StringBuilder()
        sb.appendLine(if (a11y) "✓ 无障碍服务已开启" else "✗ 无障碍服务未开启")
        sb.appendLine(if (overlay) "✓ 悬浮窗权限已授予" else "✗ 悬浮窗权限未授予")
        sb.appendLine(if (!battery) "✓ 已忽略电池优化" else "✗ 未关闭电池优化（容易被杀）")
        sb.appendLine()

        if (service != null) {
            val wsConnected = PhoneAccessibilityService.gatewayConnected
            if (wsConnected) {
                sb.appendLine("小克和晨在线 ✓")
                sb.appendLine("Gateway 已连接")
            } else {
                sb.appendLine("服务运行中")
                sb.appendLine("Gateway 连接中...")
            }
        } else {
            sb.appendLine("等待服务启动...")
        }

        statusText.text = sb.toString()
    }

    private fun isBatteryOptimized(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        return !pm.isIgnoringBatteryOptimizations(packageName)
    }

    @android.annotation.SuppressLint("BatteryLife")
    private fun requestBatteryWhitelist() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        startActivity(intent)
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
