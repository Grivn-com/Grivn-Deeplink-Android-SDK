package com.osdl.dynamiclinks

import android.util.Log

/**
 * SDK log levels.
 */
public enum class LogLevel(internal val priority: Int) {
    /** Verbose debug information (API request/response bodies, fingerprint details, etc.). */
    DEBUG(0),
    /** Key flow information (initialization, matching results, etc.). */
    INFO(1),
    /** Recoverable issues (network timeouts with retries, non-critical failures, etc.). */
    WARN(2),
    /** Non-recoverable errors. */
    ERROR(3),
    /** Completely silent. */
    NONE(4)
}

/**
 * SDK logging interface.
 *
 * You can implement this interface to plug the SDK logs into your own
 * logging system (e.g. Timber, Crashlytics, etc.).
 *
 * Example:
 * ```kotlin
 * DynamicLinksSDK.setLogger(object : DynamicLinksLogHandler {
 *     override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
 *         Timber.tag(tag).d(throwable, message)
 *     }
 * })
 * ```
 */
public interface DynamicLinksLogHandler {
    /**
     * Outputs a log entry.
     *
     * @param level Log level.
     * @param tag Log tag (fixed to "GrivnSDK").
     * @param message Log message.
     * @param throwable Optional associated exception.
     */
    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null)
}

/**
 * Default log implementation using Android's `Log`.
 */
internal class DefaultLogHandler : DynamicLinksLogHandler {
    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        when (level) {
            LogLevel.DEBUG -> if (throwable != null) Log.d(tag, message, throwable) else Log.d(tag, message)
            LogLevel.INFO -> if (throwable != null) Log.i(tag, message, throwable) else Log.i(tag, message)
            LogLevel.WARN -> if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
            LogLevel.ERROR -> if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
            LogLevel.NONE -> { /* no-op */ }
        }
    }
}

/**
 * Internal SDK logging utility.
 *
 * In production mode (default) it is completely silent. After calling
 * `DynamicLinksSDK.setDebugMode(true)` it outputs debug information with
 * the `[GrivnSDK]` tag.
 */
internal object SDKLogger {
    private const val TAG = "GrivnSDK"

    @Volatile
    var logLevel: LogLevel = LogLevel.NONE

    @Volatile
    var handler: DynamicLinksLogHandler = DefaultLogHandler()

    fun debug(message: String) {
        if (logLevel.priority <= LogLevel.DEBUG.priority) {
            handler.log(LogLevel.DEBUG, TAG, message)
        }
    }

    fun info(message: String) {
        if (logLevel.priority <= LogLevel.INFO.priority) {
            handler.log(LogLevel.INFO, TAG, message)
        }
    }

    fun warn(message: String, throwable: Throwable? = null) {
        if (logLevel.priority <= LogLevel.WARN.priority) {
            handler.log(LogLevel.WARN, TAG, message, throwable)
        }
    }

    fun error(message: String, throwable: Throwable? = null) {
        if (logLevel.priority <= LogLevel.ERROR.priority) {
            handler.log(LogLevel.ERROR, TAG, message, throwable)
        }
    }
}
