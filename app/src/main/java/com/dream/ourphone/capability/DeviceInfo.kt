package com.dream.ourphone.capability

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.provider.Settings

class DeviceInfo(private val context: Context) {

    fun collect(): Map<String, Any?> {
        return mapOf(
            "battery" to getBattery(),
            "storage" to getStorage(),
            "memory" to getMemory(),
            "screen" to getScreen(),
            "network" to getNetwork(),
            "brightness" to getBrightness(),
            "volume" to getVolume(),
            "model" to android.os.Build.MODEL,
            "android_version" to android.os.Build.VERSION.RELEASE,
            "sdk" to android.os.Build.VERSION.SDK_INT
        )
    }

    private fun getBattery(): Map<String, Any?> {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        return mapOf(
            "level" to if (scale > 0) (level * 100 / scale) else -1,
            "charging" to (status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL),
            "plugged" to when (plugged) {
                BatteryManager.BATTERY_PLUGGED_AC -> "ac"
                BatteryManager.BATTERY_PLUGGED_USB -> "usb"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
                else -> "none"
            }
        )
    }

    private fun getStorage(): Map<String, String> {
        val stat = StatFs(Environment.getExternalStorageDirectory().path)
        val total = stat.totalBytes
        val available = stat.availableBytes
        return mapOf(
            "total" to formatSize(total),
            "available" to formatSize(available),
            "used_percent" to "${((total - available) * 100 / total)}%"
        )
    }

    private fun getMemory(): Map<String, String> {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return mapOf(
            "total" to formatSize(info.totalMem),
            "available" to formatSize(info.availMem),
            "low_memory" to info.lowMemory.toString()
        )
    }

    private fun getScreen(): Map<String, Any> {
        val dm = context.resources.displayMetrics
        return mapOf(
            "width" to dm.widthPixels,
            "height" to dm.heightPixels,
            "density" to dm.density,
            "dpi" to dm.densityDpi
        )
    }

    private fun getNetwork(): Map<String, Any?> {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(network)
        return mapOf(
            "connected" to (caps != null),
            "wifi" to (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true),
            "cellular" to (caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true),
            "vpn" to (caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true)
        )
    }

    private fun getBrightness(): Int {
        return try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        } catch (e: Exception) { -1 }
    }

    private fun getVolume(): Map<String, Int> {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        return mapOf(
            "media" to am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC),
            "ring" to am.getStreamVolume(android.media.AudioManager.STREAM_RING),
            "alarm" to am.getStreamVolume(android.media.AudioManager.STREAM_ALARM),
            "media_max" to am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
        )
    }

    private fun formatSize(bytes: Long): String {
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        return if (gb >= 1) "%.1f GB".format(gb)
        else "%.0f MB".format(bytes / (1024.0 * 1024.0))
    }
}
