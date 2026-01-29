package com.osdl.dynamiclinks

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import android.webkit.WebSettings
import java.security.MessageDigest

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
 * 设备指纹生成器
 * 
 * 用于生成设备唯一标识，匹配 Deferred Deeplink
 */
internal object DeviceFingerprint {
    
    private const val PREFS_NAME = "grivn_deferred_deeplink"
    private const val KEY_FIRST_LAUNCH_CHECKED = "first_launch_checked"
    private const val KEY_FINGERPRINT_ID = "fingerprint_id"
    
    /**
     * 获取或生成设备指纹
     * 
     * @param context Android Context
     * @return 设备指纹 ID
     */
    @SuppressLint("HardwareIds")
    fun getFingerprint(context: Context): String {
        val prefs = getPrefs(context)
        
        // 优先使用缓存的指纹
        prefs.getString(KEY_FINGERPRINT_ID, null)?.let { return it }
        
        // 生成新指纹
        val fingerprint = generateFingerprint(context)
        prefs.edit().putString(KEY_FINGERPRINT_ID, fingerprint).apply()
        
        return fingerprint
    }
    
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
     * 生成设备指纹
     * 
     * 使用与后端一致的算法：IP（SDK 端不可用，由后端填充） + 设备类型 + OS 类型
     * SDK 端生成简化指纹，实际匹配由后端根据 IP + User-Agent 完成
     */
    @SuppressLint("HardwareIds")
    private fun generateFingerprint(context: Context): String {
        val components = listOf(
            getDeviceType(context),
            "android",
            Build.MODEL,
            Build.MANUFACTURER
        )
        
        val data = components.joinToString("|")
        return sha256(data)
    }
    
    /**
     * 获取设备类型
     */
    private fun getDeviceType(context: Context): String {
        // 检查是否是平板
        val metrics = context.resources.displayMetrics
        val widthInches = metrics.widthPixels / metrics.xdpi
        val heightInches = metrics.heightPixels / metrics.ydpi
        val diagonalInches = Math.sqrt((widthInches * widthInches + heightInches * heightInches).toDouble())
        
        return if (diagonalInches >= 7.0) "tablet" else "mobile"
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
    
    /**
     * SHA256 哈希
     */
    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
