package com.dream.ourphone.capability

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

class AppManager(private val context: Context) {

    private val nameToPackage = mutableMapOf<String, String>()

    init {
        loadBuiltinMappings()
        scanInstalledApps()
    }

    fun resolvePackage(nameOrPackage: String): String? {
        if (nameOrPackage.contains('.')) return nameOrPackage
        return nameToPackage[nameOrPackage.lowercase()]
    }

    fun listInstalled(): List<Map<String, String>> {
        val pm = context.packageManager
        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .map { app ->
                mapOf(
                    "name" to pm.getApplicationLabel(app).toString(),
                    "package" to app.packageName
                )
            }
            .sortedBy { it["name"]?.lowercase() }
    }

    fun getRunningPackage(): String? = null

    private fun scanInstalledApps() {
        val pm = context.packageManager
        pm.getInstalledApplications(PackageManager.GET_META_DATA).forEach { app ->
            val label = pm.getApplicationLabel(app).toString().lowercase()
            nameToPackage[label] = app.packageName
        }
    }

    private fun loadBuiltinMappings() {
        val map = mapOf(
            // 社交
            "微信" to "com.tencent.mm",
            "wechat" to "com.tencent.mm",
            "qq" to "com.tencent.mobileqq",
            "钉钉" to "com.alibaba.android.rimet",
            "飞书" to "com.ss.android.lark",
            "telegram" to "org.telegram.messenger",
            "whatsapp" to "com.whatsapp",
            "discord" to "com.discord",

            // 浏览器
            "chrome" to "com.android.chrome",
            "浏览器" to "com.android.browser",
            "firefox" to "org.mozilla.firefox",
            "edge" to "com.microsoft.emmx",
            "via" to "mark.via.gp",

            // 视频/娱乐
            "bilibili" to "tv.danmaku.bili",
            "b站" to "tv.danmaku.bili",
            "抖音" to "com.ss.android.ugc.aweme",
            "tiktok" to "com.zhiliaoapp.musically",
            "youtube" to "com.google.android.youtube",
            "网易云" to "com.netease.cloudmusic",
            "网易云音乐" to "com.netease.cloudmusic",
            "qq音乐" to "com.tencent.qqmusic",
            "spotify" to "com.spotify.music",

            // 购物/外卖
            "淘宝" to "com.taobao.taobao",
            "京东" to "com.jingdong.app.mall",
            "拼多多" to "com.xunmeng.pinduoduo",
            "美团" to "com.sankuai.meituan",
            "饿了么" to "me.ele",

            // 出行
            "高德" to "com.autonavi.minimap",
            "高德地图" to "com.autonavi.minimap",
            "百度地图" to "com.baidu.BaiduMap",
            "滴滴" to "com.sdu.didi.psnger",

            // 工具
            "支付宝" to "com.eg.android.AlipayGphone",
            "alipay" to "com.eg.android.AlipayGphone",
            "闹钟" to "com.android.deskclock",
            "时钟" to "com.android.deskclock",
            "日历" to "com.android.calendar",
            "计算器" to "com.miui.calculator",
            "相机" to "com.android.camera",
            "相册" to "com.miui.gallery",
            "设置" to "com.android.settings",
            "文件管理" to "com.android.fileexplorer",
            "应用商店" to "com.xiaomi.market",

            // 内容
            "小红书" to "com.xingin.xhs",
            "知乎" to "com.zhihu.android",
            "微博" to "com.sina.weibo",
            "豆瓣" to "com.douban.frodo",
            "今日头条" to "com.ss.android.article.news",

            // 效率
            "滴答清单" to "com.ticktick.task",
            "ticktick" to "com.ticktick.task",
            "notion" to "notion.id",

            // 通信
            "电话" to "com.android.dialer",
            "短信" to "com.android.mms",
            "联系人" to "com.android.contacts",

            // Tailscale
            "tailscale" to "com.tailscale.ipn"
        )
        map.forEach { (name, pkg) -> nameToPackage[name] = pkg }
    }
}
