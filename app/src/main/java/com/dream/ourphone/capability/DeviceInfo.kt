package com.dream.ourphone.capability

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.provider.Settings

class DeviceInfo(private val context: Context) {

    fun collect(): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()
        result["model"] = android.os.Build.MODEL
        result["android_version"] = android.os.Build.VERSION.RELEASE
        result["sdk"] = android.os.Build.VERSION.SDK_INT
        fun safe(key: String, block: () -> Any?) {
            try { result[key] = block() } catch (e: Exception) {
                result[key] = "error: ${e.message}"
                android.util.Log.e("DeviceInfo", "$key failed", e)
            }
        }
        safe("battery") { getBattery() }
        safe("storage") { getStorage() }
        safe("memory") { getMemory() }
        safe("screen") { getScreen() }
        safe("network") { getNetwork() }
        safe("brightness") { getBrightness() }
        safe("volume") { getVolume() }
        return result
    }

    private fun getBattery(): Map<String, Any?> {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = bm.isCharging
        return mapOf(
            "level" to level,
            "charging" to charging
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
