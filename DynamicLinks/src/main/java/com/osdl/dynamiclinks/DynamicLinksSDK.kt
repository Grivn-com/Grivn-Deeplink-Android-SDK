package com.osdl.dynamiclinks

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.osdl.dynamiclinks.network.ApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean

/**
 * DynamicLinks SDK 主入口
 * 
 * 使用示例：
 * ```kotlin
 * // 初始化（仅处理链接时）
 * DynamicLinksSDK.init(
 *     baseUrl = "https://api.grivn.com",
 *     secretKey = "your_secret_key"
 * )
 * 
 * // 初始化（需要创建链接时，需要提供 projectId）
 * DynamicLinksSDK.init(
 *     baseUrl = "https://api.grivn.com",
 *     secretKey = "your_secret_key",
 *     projectId = "your_project_id"
 * )
 * 
 * // 可选配置
 * DynamicLinksSDK.configure(allowedHosts = listOf("acme.wayp.link"))
 * 
 * // 处理动态链接
 * val dynamicLink = DynamicLinksSDK.handleDynamicLink(intent)
 * 
 * // 缩短链接（需要 projectId）
 * val response = DynamicLinksSDK.shorten(dynamicLinkComponents)
 * ```
 */
public object DynamicLinksSDK {

    /**
     * Returns the SDK version.
     * This value should be passed as a header to your backend, it's purpose is to enable schema breaking changes
     */
    public val sdkVersion: String
        get() = BuildConfig.SDK_VERSION

    private val isInitialized = AtomicBoolean(false)
    private var allowedHosts: List<String> = emptyList()
    private var trustAllCerts: Boolean = false
    
    // SDK 配置
    private var baseUrl: String = ""
    private var secretKey: String = ""
    private var projectId: String? = null
    
    private lateinit var apiService: ApiService
    
    // 用于自动检查的协程 Scope
    private val sdkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Deferred Deeplink 回调
    private var deferredDeeplinkCallback: ((DeferredDeeplinkData) -> Unit)? = null
    
    // Application Context (用于自动检查)
    private var appContext: Context? = null

    // ============ 初始化 ============

    /**
     * 初始化 SDK（简单版本，不自动检查 Deferred Deeplink）
     * 
     * @param baseUrl 后端 API Base URL (例如 "https://api.grivn.com")
     * @param secretKey Secret Key（通过 X-API-Key header 发送）
     * @param projectId 项目 ID（可选，用于创建链接时指定所属项目）
     */
    @JvmStatic
    @JvmOverloads
    public fun init(
        baseUrl: String,
        secretKey: String,
        projectId: String? = null
    ) {
        initInternal(baseUrl, secretKey, projectId, null, null)
    }
    
    /**
     * 初始化 SDK 并自动检查 Deferred Deeplink
     * 
     * SDK 会在首次启动时自动检查是否有延迟深链，并通过回调返回结果。
     * 开发者无需额外调用 checkDeferredDeeplink()。
     * 
     * 使用示例：
     * ```kotlin
     * // 在 Application.onCreate 中调用
     * DynamicLinksSDK.init(
     *     context = this,
     *     baseUrl = "https://api.grivn.com",
     *     secretKey = "your_secret_key",
     *     projectId = "your_project_id"
     * ) { result ->
     *     if (result.found) {
     *         // 处理延迟深链
     *         Log.d("DeferredDeeplink", "Found: ${result.originalUrl}")
     *     }
     * }
     * ```
     * 
     * @param context Application Context（用于自动检查 Deferred Deeplink）
     * @param baseUrl 后端 API Base URL
     * @param secretKey Secret Key
     * @param projectId 项目 ID（可选）
     * @param onDeferredDeeplink 延迟深链回调（可选，不传则静默上报安装）
     */
    @JvmStatic
    @JvmOverloads
    public fun init(
        context: Context,
        baseUrl: String,
        secretKey: String,
        projectId: String? = null,
        onDeferredDeeplink: ((DeferredDeeplinkData) -> Unit)? = null
    ) {
        initInternal(baseUrl, secretKey, projectId, context.applicationContext, onDeferredDeeplink)
    }
    
    private fun initInternal(
        baseUrl: String,
        secretKey: String,
        projectId: String?,
        context: Context?,
        callback: ((DeferredDeeplinkData) -> Unit)?
    ) {
        require(baseUrl.isNotBlank()) { "baseUrl cannot be blank" }
        require(secretKey.isNotBlank()) { "secretKey cannot be blank" }
        
        this.baseUrl = baseUrl.trimEnd('/')
        this.secretKey = secretKey
        this.projectId = projectId
        this.appContext = context
        this.deferredDeeplinkCallback = callback
        
        apiService = ApiService(
            baseUrl = this.baseUrl,
            secretKey = this.secretKey,
            timeout = 30,
            trustAllCerts = trustAllCerts
        )
        
        isInitialized.set(true)
        
        // 自动检查 Deferred Deeplink
        context?.let { ctx ->
            sdkScope.launch {
                try {
                    val result = checkDeferredDeeplink(ctx, forceCheck = false)
                    callback?.invoke(result)
                } catch (e: Exception) {
                    // 静默失败，不影响 App 启动
                }
            }
        }
    }
    
    /**
     * 设置项目 ID（可在 init() 后单独设置）
     * 
     * @param projectId 项目 ID（用于创建链接）
     */
    @JvmStatic
    public fun setProjectId(projectId: String): DynamicLinksSDK {
        this.projectId = projectId
        return this
    }

    /**
     * 检查是否已初始化
     */
    @JvmStatic
    public fun isInitialized(): Boolean = isInitialized.get()

    /**
     * 设置是否信任所有证书（仅开发环境使用）
     * 必须在 init() 之前调用
     */
    @JvmStatic
    public fun setTrustAllCerts(enabled: Boolean): DynamicLinksSDK {
        trustAllCerts = enabled
        return this
    }

    /**
     * Configures the DynamicLinks SDK by providing a list of allowed hosts. eg. (acme.wayp.link, acme-preview.wayp.link, preview.acme.wayp.link)
     *
     * @param allowedHosts The list of domains that the SDK will support.
     */
    @Synchronized
    @JvmStatic
    public fun configure(allowedHosts: List<String>) {
        DynamicLinksSDK.allowedHosts = allowedHosts
    }
    
    private fun ensureInitialized() {
        if (!isInitialized.get()) {
            throw DynamicLinksSDKError.NotInitialized
        }
    }

    // ============ 处理动态链接 ============

    /**
     * Handles the Dynamic Link passed within the intent and returns a DynamicLink object.
     *
     * @param intent The Intent containing the Dynamic Link URI.
     * @return The DynamicLink object extracted from the URI in the intent.
     * @throws DynamicLinksSDKError.InvalidDynamicLink if the intent data is null or invalid.
     */
    @Throws(DynamicLinksSDKError::class)
    public suspend fun handleDynamicLink(intent: Intent): DynamicLink {
        val uri = intent.data ?: throw DynamicLinksSDKError.InvalidDynamicLink
        return handleDynamicLink(uri)
    }

    /**
     * Handles the Dynamic Link passed in the form of a URI and returns a DynamicLink object.
     *
     * @param incomingUrl The URI representing the Dynamic Link.
     * @return The DynamicLink object.
     * @throws DynamicLinksSDKError.InvalidDynamicLink if the URL is not valid.
     * @throws DynamicLinksSDKError.NotInitialized if the SDK is not initialized.
     */
    @Throws(DynamicLinksSDKError::class)
    public suspend fun handleDynamicLink(incomingUrl: Uri): DynamicLink {
        ensureInitialized()
        
        if (!isValidDynamicLink(incomingUrl)) {
            throw DynamicLinksSDKError.InvalidDynamicLink
        }

        return withContext(Dispatchers.IO) {
            val response = apiService.exchangeShortLink(incomingUrl)
            
            when {
                response.isSuccess -> {
                    val exchangeResponse = response.getOrNull()!!
                    DynamicLink(exchangeResponse.longLink)
                }
                else -> {
                    throw response.exceptionOrNull() ?: DynamicLinksSDKError.InvalidDynamicLink
                }
            }
        }
    }

    // ============ 缩短链接 ============

    /**
     * Shortens a Dynamic Link and returns the response containing the shortened URL.
     *
     * @param dynamicLink The DynamicLinkComponents that will be used to build the URI.
     * @param projectId 项目 ID（可选，如果未在 init() 或 setProjectId() 中设置，则必须在此传入）
     * @return A DynamicLinkShortenResponse containing the shortened link.
     * @throws DynamicLinksSDKError.NotInitialized if the SDK is not initialized.
     * @throws DynamicLinksSDKError.ProjectIdNotSet if projectId is not configured.
     * @throws DynamicLinksSDKError.InvalidDynamicLink if the link is invalid.
     */
    @Throws(DynamicLinksSDKError::class)
    @JvmOverloads
    public suspend fun shorten(
        dynamicLink: DynamicLinkComponents,
        projectId: String? = null
    ): DynamicLinkShortenResponse {
        ensureInitialized()
        
        val effectiveProjectId = projectId ?: this.projectId
            ?: throw DynamicLinksSDKError.ProjectIdNotSet
        
        return withContext(Dispatchers.IO) {
            val response = apiService.shortenUrl(effectiveProjectId, dynamicLink)
            
            when {
                response.isSuccess -> {
                    response.getOrNull()!!
                }
                else -> {
                    throw response.exceptionOrNull() ?: DynamicLinksSDKError.InvalidDynamicLink
                }
            }
        }
    }

    // ============ 验证链接 ============

    /**
     * Checks if the given intent contains a valid Dynamic Link.
     *
     * @param intent The Intent that may contain a Dynamic Link.
     * @return True if the intent data is a valid Dynamic Link, false otherwise.
     */
    @JvmStatic
    public fun isValidDynamicLink(intent: Intent): Boolean {
        val uri = intent.data ?: return false
        return isValidDynamicLink(uri)
    }

    /**
     * Checks if the given URI represents a valid Dynamic Link.
     *
     * @param url The URI representing the Dynamic Link.
     * @return True if the URL's host matches one of the allowed hosts and the path matches the expected format, false otherwise.
     */
    @JvmStatic
    public fun isValidDynamicLink(url: Uri): Boolean {
        val host = url.host ?: return false
        val pathMatches = Regex("/[^/]+").containsMatchIn(url.path ?: "")
        return allowedHosts.contains(host) && pathMatches
    }

    // ============ Deferred Deeplink ============

    /**
     * 检查并获取延迟深链（首次安装后的 Deeplink）
     * 
     * 当用户通过 Deeplink 跳转到应用商店下载 App 后，首次打开时调用此方法获取原始链接数据。
     * 此方法只会在首次启动时返回数据，后续调用将返回 found=false。
     * 
     * 使用示例：
     * ```kotlin
     * // 在 Activity.onCreate 或 Application.onCreate 中调用
     * lifecycleScope.launch {
     *     val result = DynamicLinksSDK.checkDeferredDeeplink(context)
     *     if (result.found) {
     *         // 处理延迟深链
     *         val deeplinkId = result.deeplinkId
     *         val utmSource = result.utmSource
     *         // 导航到目标页面...
     *     }
     * }
     * ```
     * 
     * @param context Android Context
     * @param forceCheck 是否强制检查（忽略首次启动标记，用于测试）
     * @return DeferredDeeplinkData 包含是否找到延迟深链及链接数据
     * @throws DynamicLinksSDKError.NotInitialized 如果 SDK 未初始化
     */
    @JvmStatic
    @JvmOverloads
    public suspend fun checkDeferredDeeplink(
        context: Context,
        forceCheck: Boolean = false
    ): DeferredDeeplinkData {
        ensureInitialized()
        
        // 检查是否是首次启动
        if (!forceCheck && !DeviceFingerprint.isFirstLaunch(context)) {
            return DeferredDeeplinkData(found = false, linkData = null)
        }
        
        return withContext(Dispatchers.IO) {
            try {
                // 标记已检查
                DeviceFingerprint.markFirstLaunchChecked(context)
                
                val fingerprintId = DeviceFingerprint.getFingerprint(context)
                val userAgent = DeviceFingerprint.getDefaultUserAgent(context)
                val metrics = context.resources.displayMetrics
                val screenResolution = "${metrics.widthPixels}x${metrics.heightPixels}"
                val timezone = TimeZone.getDefault().id
                val language = Locale.getDefault().language
                
                val response = apiService.getDeferredDeeplink(
                    fingerprintId = fingerprintId,
                    userAgent = userAgent,
                    screenResolution = screenResolution,
                    timezone = timezone,
                    language = language
                )
                
                when {
                    response.isSuccess -> {
                        val data = response.getOrNull()!!
                        if (data.found) {
                            // 确认安装
                            confirmInstallInternal(context)
                        }
                        DeferredDeeplinkData(
                            found = data.found,
                            linkData = data.linkData
                        )
                    }
                    else -> {
                        DeferredDeeplinkData(found = false, linkData = null)
                    }
                }
            } catch (e: Exception) {
                DeferredDeeplinkData(found = false, linkData = null)
            }
        }
    }
    
    /**
     * 手动确认安装（通常由 checkDeferredDeeplink 自动调用）
     * 
     * @param context Android Context
     */
    @JvmStatic
    public suspend fun confirmInstall(context: Context) {
        ensureInitialized()
        confirmInstallInternal(context)
    }
    
    private suspend fun confirmInstallInternal(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val fingerprintId = DeviceFingerprint.getFingerprint(context)
                val userAgent = DeviceFingerprint.getDefaultUserAgent(context)
                val deviceModel = Build.MODEL
                val osVersion = Build.VERSION.RELEASE
                val appVersion = try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                } catch (e: Exception) {
                    null
                }
                
                apiService.confirmInstall(
                    fingerprintId = fingerprintId,
                    userAgent = userAgent,
                    deviceModel = deviceModel,
                    osVersion = osVersion,
                    appVersion = appVersion
                )
            } catch (e: Exception) {
                // Silently fail - install confirmation is best-effort
            }
        }
    }
    
    /**
     * 重置首次启动状态（用于测试）
     * 
     * @param context Android Context
     */
    @JvmStatic
    public fun resetDeferredDeeplinkState(context: Context) {
        DeviceFingerprint.resetFirstLaunch(context)
    }

}
