package com.osdl.dynamiclinks

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.RemoteException
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.osdl.dynamiclinks.network.ApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Main entry point for the DynamicLinks SDK.
 *
 * Usage examples:
 * ```kotlin
 * // Initialize (only handling links)
 * DynamicLinksSDK.init(
 *     baseUrl = "https://api.grivn.com",
 *     secretKey = "your_secret_key"
 * )
 *
 * // Initialize (also creating links, requires projectId)
 * DynamicLinksSDK.init(
 *     baseUrl = "https://api.grivn.com",
 *     secretKey = "your_secret_key",
 *     projectId = "your_project_id"
 * )
 *
 * // Optional configuration
 * DynamicLinksSDK.configure(allowedHosts = listOf("acme.wayp.link"))
 *
 * // Handle a dynamic link
 * val dynamicLink = DynamicLinksSDK.handleDynamicLink(intent)
 *
 * // Shorten a link (requires projectId)
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

    /**
     * Whether analytics data collection is enabled (deferred deeplink & install confirmation).
     * When disabled, regular deep link handling (App Links) continues to work normally.
     */
    @JvmStatic
    public var analyticsEnabled: Boolean = true
    
    // SDK configuration
    private var baseUrl: String = ""
    private var secretKey: String = ""
    private var projectId: String? = null
    
    private lateinit var apiService: ApiService

    // Event tracker
    private var eventTracker: EventTracker? = null

    // Coroutine scope used for automatic checks
    private val sdkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Deferred deeplink callback
    private var deferredDeeplinkCallback: ((DeferredDeeplinkData) -> Unit)? = null

    // Application context (used for automatic checks)
    private var appContext: Context? = null

    // ============ Initialization ============

    /**
     * Initializes the SDK (simple version, without automatic deferred deeplink check).
     *
     * @param baseUrl Backend API base URL (e.g. "https://api.grivn.com").
     * @param secretKey Secret key (sent via the X-API-Key header).
     * @param projectId Optional project ID (used when creating links).
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
     * Initializes the SDK and automatically checks for deferred deeplinks.
     *
     * On first launch, the SDK will automatically check whether there is a
     * deferred deeplink and return the result via the callback. You do not
     * need to call `checkDeferredDeeplink()` manually.
     *
     * Usage example:
     * ```kotlin
     * // Call from Application.onCreate
     * DynamicLinksSDK.init(
     *     context = this,
     *     baseUrl = "https://api.grivn.com",
     *     secretKey = "your_secret_key",
     *     projectId = "your_project_id"
     * ) { result ->
     *     if (result.found) {
     *         // Handle deferred deeplink
     *         Log.d("DeferredDeeplink", "Found: ${result.originalUrl}")
     *     }
     * }
     * ```
     *
     * @param context Application context (used for automatic deferred deeplink check).
     * @param baseUrl Backend API base URL.
     * @param secretKey Secret key.
     * @param projectId Optional project ID.
     * @param onDeferredDeeplink Optional deferred deeplink callback; if null,
     *   the SDK will silently report the install.
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

        // Initialize event tracker if analytics is enabled and projectId is available
        if (analyticsEnabled && projectId != null && context != null) {
            val deviceId = android.provider.Settings.Secure.getString(
                context.contentResolver, android.provider.Settings.Secure.ANDROID_ID
            ) ?: "unknown"
            val osVersion = Build.VERSION.RELEASE
            val appVersion = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
            } catch (_: Exception) { "" }

            eventTracker = EventTracker(
                apiService = apiService,
                projectId = projectId,
                deviceId = deviceId,
                deviceType = "android",
                osVersion = osVersion,
                appVersion = appVersion,
                sdkVersion = sdkVersion
            )
            eventTracker?.start()
        }

        SDKLogger.info("SDK initialized — baseUrl=${this.baseUrl}, projectId=${projectId ?: "(none)"}, analyticsEnabled=$analyticsEnabled")

        // Automatically check deferred deeplink
        context?.let { ctx ->
            sdkScope.launch {
                try {
                    val result = checkDeferredDeeplink(ctx, forceCheck = false)
                    callback?.invoke(result)
                } catch (e: Exception) {
                    SDKLogger.warn("Auto deferred-deeplink check failed", e)
                }
            }
        }
    }
    
    /**
     * Sets the project ID (can be called after `init()`).
     *
     * @param projectId Project ID used when creating links.
     */
    @JvmStatic
    public fun setProjectId(projectId: String): DynamicLinksSDK {
        this.projectId = projectId
        return this
    }

    /**
     * Returns whether the SDK has been initialized.
     */
    @JvmStatic
    public fun isInitialized(): Boolean = isInitialized.get()

    /**
     * Enable or disable analytics data collection.
     * When disabled, `checkDeferredDeeplink` returns not-found and `confirmInstall` is a no-op.
     * Regular deep link handling (App Links) is not affected.
     *
     * @param enabled true to enable (default), false to disable
     */
    @JvmStatic
    public fun setAnalyticsEnabled(enabled: Boolean): DynamicLinksSDK {
        analyticsEnabled = enabled
        return this
    }

    /**
     * Enables or disables debug mode.
     *
     * When enabled, the SDK outputs DEBUG-level logs with the `GrivnSDK` tag
     * (initialization, network requests, deferred deeplink checks, etc.).
     * When disabled, logging is completely silent (NONE).
     *
     * @param enabled true to enable DEBUG level, false for NONE.
     */
    @JvmStatic
    public fun setDebugMode(enabled: Boolean): DynamicLinksSDK {
        SDKLogger.logLevel = if (enabled) LogLevel.DEBUG else LogLevel.NONE
        return this
    }

    /**
     * Sets the log level.
     *
     * Available levels: DEBUG, INFO, WARN, ERROR, NONE (default is NONE,
     * completely silent).
     *
     * @param level Log level.
     */
    @JvmStatic
    public fun setLogLevel(level: LogLevel): DynamicLinksSDK {
        SDKLogger.logLevel = level
        return this
    }

    /**
     * Registers a custom log handler.
     *
     * Implement [DynamicLinksLogHandler] to forward SDK logs to Timber,
     * Crashlytics, or any other logging system.
     *
     * @param handler Custom log handler.
     */
    @JvmStatic
    public fun setLogger(handler: DynamicLinksLogHandler): DynamicLinksSDK {
        SDKLogger.handler = handler
        return this
    }

    /**
     * Controls whether to trust all SSL certificates (development only).
     *
     * Must be called before `init()`.
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

    // ============ Dynamic link handling ============

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

        SDKLogger.debug("handleDynamicLink — url=$incomingUrl")

        if (!isValidDynamicLink(incomingUrl)) {
            SDKLogger.warn("Invalid dynamic link: $incomingUrl")
            throw DynamicLinksSDKError.InvalidDynamicLink
        }

        return withContext(Dispatchers.IO) {
            val response = apiService.exchangeShortLink(incomingUrl)

            when {
                response.isSuccess -> {
                    val exchangeResponse = response.getOrNull()!!
                    SDKLogger.info("exchangeShortLink succeeded — longLink=${exchangeResponse.longLink}")

                    // Auto-event: deeplink_first_open or deeplink_reopen
                    if (analyticsEnabled) {
                        val prefs = appContext?.getSharedPreferences("grivn_events", android.content.Context.MODE_PRIVATE)
                        val key = "deeplink_opened_${incomingUrl}"
                        if (prefs != null && !prefs.getBoolean(key, false)) {
                            prefs.edit().putBoolean(key, true).apply()
                            trackEvent("deeplink_first_open", mapOf("url" to incomingUrl.toString()))
                        } else {
                            trackEvent("deeplink_reopen", mapOf("url" to incomingUrl.toString()))
                        }
                    }

                    DynamicLink(exchangeResponse.longLink)
                }
                else -> {
                    val ex = response.exceptionOrNull() ?: DynamicLinksSDKError.InvalidDynamicLink
                    SDKLogger.error("exchangeShortLink failed", ex)
                    throw ex
                }
            }
        }
    }

    // ============ Shortening links ============

    /**
     * Shortens a Dynamic Link and returns the response containing the shortened URL.
     *
     * @param dynamicLink The DynamicLinkComponents that will be used to build the URI.
     * @param projectId Optional project ID. If not set via init() or setProjectId(), it must be passed here.
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

    // ============ Link validation ============

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

    // ============ Deferred deeplink ============

    /**
     * Checks for and retrieves a deferred deeplink (post-install deeplink).
     *
     * When a user goes to the app store via a deeplink and installs the app,
     * call this method on first launch to obtain the original link data.
     * This method only returns data on the first launch; subsequent calls
     * will return `found = false`.
     *
     * Usage example:
     * ```kotlin
     * // Call from Activity.onCreate or Application.onCreate
     * lifecycleScope.launch {
     *     val result = DynamicLinksSDK.checkDeferredDeeplink(context)
     *     if (result.found) {
     *         // Handle deferred deeplink
     *         val deeplinkId = result.deeplinkId
     *         val utmSource = result.utmSource
     *         // Navigate to the target screen...
     *     }
     * }
     * ```
     *
     * @param context Android context.
     * @param forceCheck Whether to force a check (ignores the first-launch flag, useful for testing).
     * @return DeferredDeeplinkData containing whether a deferred deeplink was found and its data.
     * @throws DynamicLinksSDKError.NotInitialized if the SDK has not been initialized.
     */
    @JvmStatic
    @JvmOverloads
    public suspend fun checkDeferredDeeplink(
        context: Context,
        forceCheck: Boolean = false
    ): DeferredDeeplinkData {
        ensureInitialized()

        // Analytics opt-out: skip deferred deeplink check
        if (!analyticsEnabled) {
            return DeferredDeeplinkData(found = false, linkData = null)
        }

        // Check whether this is the first launch
        if (!forceCheck && !DeviceFingerprint.isFirstLaunch(context)) {
            return DeferredDeeplinkData(found = false, linkData = null)
        }

        SDKLogger.info("Checking deferred deeplink (forceCheck=$forceCheck)")

        return withContext(Dispatchers.IO) {
            try {
                // Mark that the first-launch check has been performed
                DeviceFingerprint.markFirstLaunchChecked(context)

                val userAgent = DeviceFingerprint.getDefaultUserAgent(context)

                // Priority 1: try the Install Referrer API (>95% accuracy).
                SDKLogger.debug("Trying Install Referrer API first...")
                val referrerResult = tryInstallReferrer(context, userAgent)
                if (referrerResult != null && referrerResult.found) {
                    SDKLogger.info("Deferred deeplink found via Install Referrer")
                    trackEvent("deferred_deeplink_match", mapOf("match_tier" to "referrer"))
                    confirmInstallInternal(context)
                    return@withContext referrerResult
                }

                // Priority 2: fall back to fingerprint matching (50-70% accuracy).
                val metrics = context.resources.displayMetrics
                val screenResolution = "${metrics.widthPixels}x${metrics.heightPixels}"
                val timezone = TimeZone.getDefault().id
                val language = Locale.getDefault().language

                SDKLogger.debug("Falling back to fingerprint matching — screen=$screenResolution, tz=$timezone, lang=$language")

                val response = apiService.getDeferredDeeplink(
                    userAgent = userAgent,
                    screenResolution = screenResolution,
                    timezone = timezone,
                    language = language
                )

                when {
                    response.isSuccess -> {
                        val data = response.getOrNull()!!
                        if (data.found) {
                            SDKLogger.info("Deferred deeplink found via fingerprint (tier=${data.matchTier})")
                            trackEvent("deferred_deeplink_match", mapOf("match_tier" to (data.matchTier ?: "unknown")))
                            confirmInstallInternal(context)
                        } else {
                            SDKLogger.info("No deferred deeplink found")
                        }
                        DeferredDeeplinkData(
                            found = data.found,
                            linkData = data.linkData
                        )
                    }
                    else -> {
                        SDKLogger.warn("Deferred deeplink request failed", response.exceptionOrNull())
                        DeferredDeeplinkData(found = false, linkData = null)
                    }
                }
            } catch (e: Exception) {
                SDKLogger.error("checkDeferredDeeplink exception", e)
                DeferredDeeplinkData(found = false, linkData = null)
            }
        }
    }

    /**
     * Tries to obtain a deferred deeplink via the Install Referrer API.
     *
     * Returns null if Install Referrer is not available or does not contain
     * a `deeplink_id`.
     */
    private suspend fun tryInstallReferrer(context: Context, userAgent: String): DeferredDeeplinkData? {
        val referrerString = readInstallReferrer(context)
        if (referrerString == null) {
            SDKLogger.debug("Install Referrer not available")
            return null
        }

        SDKLogger.debug("Install Referrer string: $referrerString")

        // Parse deeplink_id from referrer string (format: "deeplink_id=xxx&utm_source=grivn")
        val params = referrerString.split("&").associate { param ->
            val parts = param.split("=", limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else parts[0] to ""
        }
        val deeplinkId = params["deeplink_id"]
        if (deeplinkId.isNullOrBlank()) {
            SDKLogger.debug("No deeplink_id in referrer, skipping")
            return null
        }

        SDKLogger.debug("Querying deferred deeplink by referrer — deeplinkId=$deeplinkId")

        val response = apiService.getDeferredByReferrer(
            referrerString = referrerString,
            deeplinkId = deeplinkId,
            userAgent = userAgent
        )

        return when {
            response.isSuccess -> {
                val data = response.getOrNull()!!
                DeferredDeeplinkData(
                    found = data.found,
                    linkData = data.linkData
                )
            }
            else -> {
                SDKLogger.warn("Install Referrer API request failed", response.exceptionOrNull())
                null
            }
        }
    }

    /**
     * Reads the Google Play Install Referrer.
     *
     * @return The referrer string, or null if not available.
     */
    private suspend fun readInstallReferrer(context: Context): String? {
        return try {
            suspendCancellableCoroutine { continuation ->
                val client = InstallReferrerClient.newBuilder(context).build()
                client.startConnection(object : InstallReferrerStateListener {
                    override fun onInstallReferrerSetupFinished(responseCode: Int) {
                        SDKLogger.debug("Install Referrer setup finished — responseCode=$responseCode")
                        when (responseCode) {
                            InstallReferrerClient.InstallReferrerResponse.OK -> {
                                try {
                                    val referrer = client.installReferrer.installReferrer
                                    client.endConnection()
                                    if (continuation.isActive) continuation.resume(referrer)
                                } catch (e: RemoteException) {
                                    SDKLogger.warn("Install Referrer RemoteException", e)
                                    client.endConnection()
                                    if (continuation.isActive) continuation.resume(null)
                                }
                            }
                            else -> {
                                SDKLogger.debug("Install Referrer unavailable (code=$responseCode)")
                                client.endConnection()
                                if (continuation.isActive) continuation.resume(null)
                            }
                        }
                    }

                    override fun onInstallReferrerServiceDisconnected() {
                        SDKLogger.debug("Install Referrer service disconnected")
                        if (continuation.isActive) continuation.resume(null)
                    }
                })

                continuation.invokeOnCancellation {
                    try { client.endConnection() } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            SDKLogger.warn("readInstallReferrer exception", e)
            null
        }
    }
    
    /**
     * Manually confirms an install (normally called automatically from `checkDeferredDeeplink`).
     *
     * @param context Android context.
     */
    @JvmStatic
    public suspend fun confirmInstall(context: Context) {
        ensureInitialized()
        confirmInstallInternal(context)
    }
    
    private suspend fun confirmInstallInternal(context: Context) {
        // Analytics opt-out: skip install confirmation
        if (!analyticsEnabled) return

        withContext(Dispatchers.IO) {
            try {
                val userAgent = DeviceFingerprint.getDefaultUserAgent(context)
                val deviceModel = Build.MODEL
                val osVersion = Build.VERSION.RELEASE
                val appVersion = try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                } catch (e: Exception) {
                    null
                }

                SDKLogger.debug("confirmInstall — device=$deviceModel, os=$osVersion, appVersion=$appVersion")

                apiService.confirmInstall(
                    userAgent = userAgent,
                    deviceModel = deviceModel,
                    osVersion = osVersion,
                    appVersion = appVersion
                )
                trackEvent("app_install")
                SDKLogger.info("Install confirmed")
            } catch (e: Exception) {
                SDKLogger.warn("confirmInstall failed (best-effort)", e)
            }
        }
    }
    
    /**
     * Resets the first-launch state (for testing).
     *
     * @param context Android context.
     */
    @JvmStatic
    public fun resetDeferredDeeplinkState(context: Context) {
        DeviceFingerprint.resetFirstLaunch(context)
    }

    // ============ Event Tracking ============

    /**
     * Track a custom event.
     *
     * Events are batched locally and flushed to the server every 30 seconds
     * or when 20 events accumulate.
     *
     * @param name Event name (e.g. "purchase", "sign_up")
     * @param params Optional event parameters
     */
    @JvmStatic
    @JvmOverloads
    public fun trackEvent(name: String, params: Map<String, Any>? = null) {
        if (!analyticsEnabled) return
        eventTracker?.trackEvent(name, params)
            ?: SDKLogger.warn("EventTracker not initialized — call init() with context and projectId first")
    }

    /**
     * Set the user ID for event attribution.
     *
     * @param userId User identifier (e.g. your app's user ID)
     */
    @JvmStatic
    public fun setUserId(userId: String) {
        eventTracker?.setUserId(userId)
            ?: SDKLogger.warn("EventTracker not initialized — call init() with context and projectId first")
    }

    /**
     * Flush pending events to the server immediately.
     */
    @JvmStatic
    public fun flushEvents() {
        sdkScope.launch {
            eventTracker?.flush()
        }
    }

}
