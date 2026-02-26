package com.osdl.dynamiclinks

import android.util.Log

/**
 * SDK 日志级别
 */
public enum class LogLevel(internal val priority: Int) {
    /** 详细调试信息（API 请求/响应 body、指纹详情等） */
    DEBUG(0),
    /** 关键流程信息（初始化、匹配结果等） */
    INFO(1),
    /** 可恢复的异常（网络超时重试、非关键失败等） */
    WARN(2),
    /** 不可恢复的错误 */
    ERROR(3),
    /** 完全静默 */
    NONE(4)
}

/**
 * SDK 日志接口
 *
 * 开发者可实现此接口接入自己的日志系统（如 Timber、Crashlytics 等）。
 *
 * 使用示例:
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
     * 输出日志
     *
     * @param level 日志级别
     * @param tag 日志标签（固定为 "GrivnSDK"）
     * @param message 日志内容
     * @param throwable 关联的异常（可选）
     */
    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null)
}

/**
 * 默认日志实现，使用 Android Log
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
 * SDK 内部日志工具
 *
 * 生产模式（默认）完全静默。调用 `DynamicLinksSDK.setDebugMode(true)` 后
 * 以 `[GrivnSDK]` 标签输出调试信息。
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
