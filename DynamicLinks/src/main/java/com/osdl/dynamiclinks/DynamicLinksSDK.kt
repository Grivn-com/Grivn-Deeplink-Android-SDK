package com.osdl.dynamiclinks

import android.content.Intent
import android.net.Uri
import com.osdl.dynamiclinks.network.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    // ============ 初始化 ============

    /**
     * 初始化 SDK
     * 
     * @param baseUrl 后端 API Base URL (例如 "https://api.grivn.com")
     * @param secretKey Secret Key（通过 X-API-Key header 发送）
     * @param projectId 项目 ID（可选，用于创建链接时指定所属项目。如果只处理链接可以不传）
     */
    @JvmStatic
    @JvmOverloads
    public fun init(
        baseUrl: String,
        secretKey: String,
        projectId: String? = null
    ) {
        require(baseUrl.isNotBlank()) { "baseUrl cannot be blank" }
        require(secretKey.isNotBlank()) { "secretKey cannot be blank" }
        
        this.baseUrl = baseUrl.trimEnd('/')
        this.secretKey = secretKey
        this.projectId = projectId
        
        apiService = ApiService(
            baseUrl = this.baseUrl,
            secretKey = this.secretKey,
            timeout = 30,
            trustAllCerts = trustAllCerts
        )
        
        isInitialized.set(true)
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

}
