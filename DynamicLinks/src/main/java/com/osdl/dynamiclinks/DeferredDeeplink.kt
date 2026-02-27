package com.osdl.dynamiclinks

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.webkit.WebSettings

/**
 * Deferred deeplink data class.
 *
 * When a user is redirected to the app store via a deeplink and opens the app
 * for the first time, the original link data can be retrieved.
 */
data class DeferredDeeplinkData(
    /** Whether a deferred deeplink was found. */
    val found: Boolean,
    /** Original link data returned from the backend. */
    val linkData: Map<String, Any>?
) {
    /** Returns the original deeplink ID. */
    val deeplinkId: String?
        get() = linkData?.get("deeplink_id") as? String

    /** Returns the project ID. */
    val projectId: String?
        get() = linkData?.get("project_id") as? String

    /** Returns the original URL. */
    val originalUrl: String?
        get() = linkData?.get("original_url") as? String

    /** Returns the UTM source. */
    val utmSource: String?
        get() = linkData?.get("utm_source") as? String

    /** Returns the UTM medium. */
    val utmMedium: String?
        get() = linkData?.get("utm_medium") as? String

    /** Returns the UTM campaign. */
    val utmCampaign: String?
        get() = linkData?.get("utm_campaign") as? String

    /** Returns the referer. */
    val referer: String?
        get() = linkData?.get("referer") as? String

    /** Returns custom data by key. */
    fun getCustomData(key: String): Any? = linkData?.get(key)
}

/**
 * Deferred deeplink state manager and device information helper.
 *
 * Fingerprint matching is performed on the server (based on IP + User-Agent);
 * the SDK neither generates nor sends a fingerprint ID.
 */
internal object DeviceFingerprint {

    private const val PREFS_NAME = "grivn_deferred_deeplink"
    private const val KEY_FIRST_LAUNCH_CHECKED = "first_launch_checked"

    /**
     * Checks whether this is the first launch (deferred deeplink not checked yet).
     */
    fun isFirstLaunch(context: Context): Boolean {
        val prefs = getPrefs(context)
        return !prefs.getBoolean(KEY_FIRST_LAUNCH_CHECKED, false)
    }

    /**
     * Marks that the first-launch check has been performed.
     */
    fun markFirstLaunchChecked(context: Context) {
        getPrefs(context).edit().putBoolean(KEY_FIRST_LAUNCH_CHECKED, true).apply()
    }

    /**
     * Resets the first-launch flag (for testing).
     */
    fun resetFirstLaunch(context: Context) {
        getPrefs(context).edit().remove(KEY_FIRST_LAUNCH_CHECKED).apply()
    }

    /**
     * Clears all cached data (for testing).
     */
    fun clearAll(context: Context) {
        getPrefs(context).edit().clear().apply()
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Returns the default User-Agent string.
     */
    fun getDefaultUserAgent(context: Context): String {
        return try {
            WebSettings.getDefaultUserAgent(context)
        } catch (e: Exception) {
            "Android/${Build.VERSION.RELEASE} (${Build.MANUFACTURER}; ${Build.MODEL})"
        }
    }
}
