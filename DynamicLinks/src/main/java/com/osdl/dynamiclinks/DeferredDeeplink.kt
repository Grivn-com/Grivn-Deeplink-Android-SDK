package com.osdl.dynamiclinks

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.webkit.WebSettings

/**
 * Deferred Deeplink 数据类
 * 
 * 当用户通过 Deeplink 跳转到应用商店下载 App，首次打开时可以获取原始链接数据
 */
data class DeferredDeeplinkData(
    /** 是否找到延迟深链 */
    val found: Boolean,
    /** 原始链接数据 */
    val linkData: Map<String, Any>?
) {
    /** 获取原始 Deeplink ID */
    val deeplinkId: String?
        get() = linkData?.get("deeplink_id") as? String

    /** 获取 Project ID */
    val projectId: String?
        get() = linkData?.get("project_id") as? String

    /** 获取原始 URL */
    val originalUrl: String?
        get() = linkData?.get("original_url") as? String

    /** 获取 UTM Source */
    val utmSource: String?
        get() = linkData?.get("utm_source") as? String

    /** 获取 UTM Medium */
    val utmMedium: String?
        get() = linkData?.get("utm_medium") as? String

    /** 获取 UTM Campaign */
    val utmCampaign: String?
        get() = linkData?.get("utm_campaign") as? String

    /** 获取 Referer */
    val referer: String?
        get() = linkData?.get("referer") as? String

    /** 获取自定义数据 */
    fun getCustomData(key: String): Any? = linkData?.get(key)
}

/**
 * Deferred Deeplink 状态管理和设备信息辅助工具
 *
 * 指纹匹配由服务端完成（基于 IP + User-Agent），SDK 不生成也不发送指纹 ID。
 */
internal object DeviceFingerprint {

    private const val PREFS_NAME = "grivn_deferred_deeplink"
    private const val KEY_FIRST_LAUNCH_CHECKED = "first_launch_checked"

    /**
     * 检查是否是首次启动（未检查过 Deferred Deeplink）
     */
    fun isFirstLaunch(context: Context): Boolean {
        val prefs = getPrefs(context)
        return !prefs.getBoolean(KEY_FIRST_LAUNCH_CHECKED, false)
    }

    /**
     * 标记已检查过首次启动
     */
    fun markFirstLaunchChecked(context: Context) {
        getPrefs(context).edit().putBoolean(KEY_FIRST_LAUNCH_CHECKED, true).apply()
    }

    /**
     * 重置首次启动标记（用于测试）
     */
    fun resetFirstLaunch(context: Context) {
        getPrefs(context).edit().remove(KEY_FIRST_LAUNCH_CHECKED).apply()
    }

    /**
     * 清除所有缓存数据（用于测试）
     */
    fun clearAll(context: Context) {
        getPrefs(context).edit().clear().apply()
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 获取默认 User-Agent
     */
    fun getDefaultUserAgent(context: Context): String {
        return try {
            WebSettings.getDefaultUserAgent(context)
        } catch (e: Exception) {
            "Android/${Build.VERSION.RELEASE} (${Build.MANUFACTURER}; ${Build.MODEL})"
        }
    }
}
