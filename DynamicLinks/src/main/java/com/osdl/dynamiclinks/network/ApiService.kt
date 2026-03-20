package com.osdl.dynamiclinks.network

import android.net.Uri
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.osdl.dynamiclinks.DynamicLinkComponents
import com.osdl.dynamiclinks.DynamicLinkShortenResponse
import com.osdl.dynamiclinks.DynamicLinksSDKError
import com.osdl.dynamiclinks.ExchangeLinkResponse
import com.osdl.dynamiclinks.SDKLogger
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * API service used by the SDK.
 *
 * Authentication is performed via the `X-API-Key` header.
 */
internal class ApiService(
    private val baseUrl: String,
    private val secretKey: String,
    private val timeout: Long = 30,
    private val trustAllCerts: Boolean = false
) {
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    
    private val client: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(timeout, TimeUnit.SECONDS)
            .readTimeout(timeout, TimeUnit.SECONDS)
            .writeTimeout(timeout, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val original = chain.request()
                val request = original.newBuilder()
                    .header("X-API-Key", secretKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
        
        if (trustAllCerts) {
            configureUnsafeSsl(builder)
        }
        
        builder.build()
    }
    
    /**
     * Configures insecure SSL (development only).
     */
    private fun configureUnsafeSsl(builder: OkHttpClient.Builder) {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, SecureRandom())

        builder
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
    }
    
    /**
     * Creates a short link from the given components.
     */
    fun shortenUrl(
        projectId: String,
        components: DynamicLinkComponents
    ): ApiResponse<DynamicLinkShortenResponse> {
        val url = "$baseUrl/api/v1/deeplinks"
        
        val body = DeeplinkCreateRequest(
            projectId = projectId,
            name = components.link.toString(),
            link = components.link.toString(),
            // Android Parameters
            apn = components.androidParameters?.packageName,
            afl = components.androidParameters?.fallbackURL,
            amv = components.androidParameters?.minimumVersion?.toString(),
            // iOS Parameters
            isi = components.iOSParameters.appStoreID,
            ifl = components.iOSParameters.fallbackURL,
            ipfl = components.iOSParameters.iPadFallbackURL,
            imv = components.iOSParameters.minimumAppVersion,
            // Other Platform
            ofl = components.otherPlatformParameters?.fallbackURL,
            // Social Meta Tags
            st = components.socialMetaTagParameters?.title,
            sd = components.socialMetaTagParameters?.descriptionText,
            si = components.socialMetaTagParameters?.imageURL,
            // Analytics (UTM)
            utmSource = components.analyticsParameters?.source,
            utmMedium = components.analyticsParameters?.medium,
            utmCampaign = components.analyticsParameters?.campaign,
            utmContent = components.analyticsParameters?.content,
            utmTerm = components.analyticsParameters?.term,
            // iTunes Connect
            at = components.iTunesConnectParameters?.affiliateToken,
            ct = components.iTunesConnectParameters?.campaignToken,
            pt = components.iTunesConnectParameters?.providerToken
        )
        
        return post(url, body)
    }
    
    /**
     * Resolves a short link back to its long link representation.
     */
    fun exchangeShortLink(requestedLink: Uri): ApiResponse<ExchangeLinkResponse> {
        val url = "$baseUrl/api/v1/deeplinks/exchangeShortLink"
        
        val body = ExchangeShortLinkRequest(
            requestedLink = requestedLink.toString()
        )
        
        return post(url, body)
    }
    
    /**
     * Retrieves a deferred deeplink.
     *
     * Fingerprint matching is performed on the server based on IP + User-Agent.
     * The SDK does not send a `fingerprint_id`.
     */
    fun getDeferredDeeplink(
        userAgent: String,
        screenResolution: String? = null,
        timezone: String? = null,
        language: String? = null
    ): ApiResponse<DeferredDeeplinkResponse> {
        val url = "$baseUrl/api/v1/analytics/deferred"

        val body = DeferredDeeplinkRequest(
            userAgent = userAgent,
            screenResolution = screenResolution,
            timezone = timezone,
            language = language
        )

        return post(url, body)
    }

    /**
     * Retrieves a deferred deeplink via the Install Referrer API.
     *
     * Accuracy is >95%, so this is preferred over fingerprint matching.
     */
    fun getDeferredByReferrer(
        referrerString: String? = null,
        deeplinkId: String? = null,
        userAgent: String
    ): ApiResponse<DeferredDeeplinkResponse> {
        val url = "$baseUrl/api/v1/analytics/install-referrer"

        val body = InstallReferrerApiRequest(
            referrerString = referrerString,
            deeplinkId = deeplinkId,
            userAgent = userAgent
        )

        return post(url, body)
    }

    /**
     * Sends a batch of SDK events.
     */
    fun trackEvents(request: EventBatchRequest): ApiResponse<EventBatchResponse> {
        val url = "$baseUrl/api/v1/events"
        return post(url, request)
    }

    /**
     * Confirms an install.
     *
     * Fingerprint matching is performed on the server based on IP + User-Agent.
     * The SDK does not send a `fingerprint_id`.
     */
    fun confirmInstall(
        userAgent: String,
        deviceModel: String? = null,
        osVersion: String? = null,
        appVersion: String? = null
    ): ApiResponse<ConfirmInstallResponse> {
        val url = "$baseUrl/api/v1/analytics/confirm-install"

        val body = ConfirmInstallRequest(
            userAgent = userAgent,
            deviceModel = deviceModel,
            osVersion = osVersion,
            appVersion = appVersion
        )

        return post(url, body)
    }
    
    /**
     * Masks the API key: keeps the first 4 and last 3 characters, replaces the middle with ***.
     */
    private fun maskApiKey(key: String): String {
        if (key.length <= 10) return "***"
        return "${key.take(4)}***${key.takeLast(3)}"
    }

    /**
     * Issues a POST request.
     */
    private inline fun <reified T> post(url: String, body: Any): ApiResponse<T> {
        val jsonBody = gson.toJson(body)

        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody(jsonMediaType))
            .build()

        return execute(request)
    }

    /**
     * Executes an HTTP request.
     */
    private inline fun <reified T> execute(request: Request): ApiResponse<T> {
        SDKLogger.debug("→ ${request.method} ${request.url} [key=${maskApiKey(secretKey)}]")

        return try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                val bodyLen = responseBody?.length ?: 0

                SDKLogger.debug("← ${response.code} ($bodyLen bytes)")

                if (!response.isSuccessful) {
                    val err = DynamicLinksSDKError.NetworkError("Server error: ${response.code}", null)
                    SDKLogger.error("HTTP error ${response.code}", err)
                    return@use ApiResponse.error(err)
                }

                if (responseBody.isNullOrEmpty()) {
                    val err = DynamicLinksSDKError.NetworkError("Empty response", null)
                    SDKLogger.error("Empty response body")
                    return@use ApiResponse.error(err)
                }

                try {
                    val baseResponse = gson.fromJson(responseBody, BaseApiResponse::class.java)
                    if (baseResponse.code != 0) {
                        val err = DynamicLinksSDKError.ServerError(
                            baseResponse.message ?: "Server error",
                            baseResponse.code
                        )
                        SDKLogger.error("Server error code=${baseResponse.code}: ${baseResponse.message}")
                        return@use ApiResponse.error(err)
                    }

                    val dataJson = gson.toJson(baseResponse.data)
                    val data = gson.fromJson(dataJson, T::class.java)
                    ApiResponse.success(data)
                } catch (e: Exception) {
                    SDKLogger.error("Response parse error", e)
                    ApiResponse.error(DynamicLinksSDKError.ParseError("Failed to parse response", e))
                }
            }
        } catch (e: IOException) {
            SDKLogger.error("Network I/O error: ${e.message}", e)
            ApiResponse.error(DynamicLinksSDKError.NetworkError("Network error: ${e.message}", e))
        } catch (e: Exception) {
            SDKLogger.error("Unknown network error: ${e.message}", e)
            ApiResponse.error(DynamicLinksSDKError.NetworkError("Unknown error: ${e.message}", e))
        }
    }
}

/**
 * Base API response from the backend.
 */
internal data class BaseApiResponse(
    val code: Int,
    val message: String?,
    val data: Any?
)

/**
 * Request payload for creating a deeplink.
 */
internal data class DeeplinkCreateRequest(
    @SerializedName("projectId") val projectId: String,
    @SerializedName("name") val name: String,
    @SerializedName("link") val link: String,
    // Android Parameters
    @SerializedName("apn") val apn: String? = null,
    @SerializedName("afl") val afl: String? = null,
    @SerializedName("amv") val amv: String? = null,
    // iOS Parameters
    @SerializedName("ifl") val ifl: String? = null,
    @SerializedName("ipfl") val ipfl: String? = null,
    @SerializedName("isi") val isi: String? = null,
    @SerializedName("imv") val imv: String? = null,
    // Other Platform
    @SerializedName("ofl") val ofl: String? = null,
    // Social Meta Tags
    @SerializedName("st") val st: String? = null,
    @SerializedName("sd") val sd: String? = null,
    @SerializedName("si") val si: String? = null,
    // Analytics (UTM)
    @SerializedName("utm_source") val utmSource: String? = null,
    @SerializedName("utm_medium") val utmMedium: String? = null,
    @SerializedName("utm_campaign") val utmCampaign: String? = null,
    @SerializedName("utm_content") val utmContent: String? = null,
    @SerializedName("utm_term") val utmTerm: String? = null,
    // iTunes Connect
    @SerializedName("at") val at: String? = null,
    @SerializedName("ct") val ct: String? = null,
    @SerializedName("mt") val mt: String? = null,
    @SerializedName("pt") val pt: String? = null
)

/**
 * Request payload for resolving a short link.
 */
internal data class ExchangeShortLinkRequest(
    @SerializedName("requestedLink") val requestedLink: String
)

/**
 * Request payload for retrieving a deferred deeplink.
 *
 * Note: `fingerprint_id` is generated on the server based on IP + User-Agent;
 * the SDK does not need to send it.
 */
internal data class DeferredDeeplinkRequest(
    @SerializedName("user_agent") val userAgent: String,
    @SerializedName("screen_resolution") val screenResolution: String? = null,
    @SerializedName("timezone") val timezone: String? = null,
    @SerializedName("language") val language: String? = null
)

/**
 * Response payload for a deferred deeplink.
 */
internal data class DeferredDeeplinkResponse(
    @SerializedName("found") val found: Boolean,
    @SerializedName("link_data") val linkData: Map<String, Any>?,
    @SerializedName("match_tier") val matchTier: String?
)

/**
 * Request payload for the Install Referrer API.
 */
internal data class InstallReferrerApiRequest(
    @SerializedName("referrer_string") val referrerString: String? = null,
    @SerializedName("deeplink_id") val deeplinkId: String? = null,
    @SerializedName("user_agent") val userAgent: String
)

/**
 * Request payload for confirming an install.
 *
 * Note: `fingerprint_id` is generated on the server based on IP + User-Agent;
 * the SDK does not need to send it.
 */
internal data class ConfirmInstallRequest(
    @SerializedName("user_agent") val userAgent: String,
    @SerializedName("device_model") val deviceModel: String? = null,
    @SerializedName("os_version") val osVersion: String? = null,
    @SerializedName("app_version") val appVersion: String? = null
)

/**
 * Response payload for confirming an install.
 */
internal data class ConfirmInstallResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String?
)

/**
 * Request payload for batching analytics events.
 */
internal data class EventBatchRequest(
    @SerializedName("project_id") val projectId: String,
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("device_type") val deviceType: String,
    @SerializedName("os_version") val osVersion: String,
    @SerializedName("app_version") val appVersion: String,
    @SerializedName("sdk_version") val sdkVersion: String,
    @SerializedName("events") val events: List<EventItemRequest>
)

/**
 * Single analytics event within a batch.
 */
internal data class EventItemRequest(
    @SerializedName("event_name") val eventName: String,
    @SerializedName("params") val params: Map<String, Any>? = null,
    @SerializedName("timestamp") val timestamp: Long? = null,
    @SerializedName("deeplink_id") val deeplinkId: String = "",
    @SerializedName("user_id") val userId: String = ""
)

/**
 * Response payload for a batched event submission.
 */
internal data class EventBatchResponse(
    @SerializedName("accepted") val accepted: Int
)

/**
 * Deeplink response returned by the backend.
 */
internal data class DeeplinkResponse(
    @SerializedName("id") val id: String,
    @SerializedName("short_link") val shortLink: String,
    @SerializedName("link") val link: String,
    @SerializedName("name") val name: String?,
    @SerializedName("apn") val apn: String?,
    @SerializedName("afl") val afl: String?,
    @SerializedName("amv") val amv: String?,
    @SerializedName("ifl") val ifl: String?,
    @SerializedName("isi") val isi: String?,
    @SerializedName("imv") val imv: String?,
    @SerializedName("ofl") val ofl: String?,
    @SerializedName("st") val st: String?,
    @SerializedName("sd") val sd: String?,
    @SerializedName("si") val si: String?,
    @SerializedName("utm_source") val utmSource: String?,
    @SerializedName("utm_medium") val utmMedium: String?,
    @SerializedName("utm_campaign") val utmCampaign: String?,
    @SerializedName("utm_content") val utmContent: String?,
    @SerializedName("utm_term") val utmTerm: String?
)

/**
 * Wrapper type for API responses.
 */
sealed class ApiResponse<T> {
    data class Success<T>(val data: T) : ApiResponse<T>()
    data class Error<T>(val exception: DynamicLinksSDKError) : ApiResponse<T>()
    
    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
    
    fun getOrNull(): T? = (this as? Success)?.data
    fun exceptionOrNull(): DynamicLinksSDKError? = (this as? Error)?.exception
    
    companion object {
        fun <T> success(data: T): ApiResponse<T> = Success(data)
        fun <T> error(exception: DynamicLinksSDKError): ApiResponse<T> = Error(exception)
    }
}

