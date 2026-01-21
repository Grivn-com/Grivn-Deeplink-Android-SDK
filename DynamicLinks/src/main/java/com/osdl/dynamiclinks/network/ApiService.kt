package com.osdl.dynamiclinks.network

import android.net.Uri
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.osdl.dynamiclinks.DynamicLinkComponents
import com.osdl.dynamiclinks.DynamicLinkShortenResponse
import com.osdl.dynamiclinks.DynamicLinksSDKError
import com.osdl.dynamiclinks.ExchangeLinkResponse
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
 * API 服务类
 * 通过 X-API-Key header 进行认证
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
     * 配置不安全的 SSL（仅开发环境）
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
     * 创建短链接 (缩短链接)
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
     * 解析短链接 (还原长链接)
     */
    fun exchangeShortLink(requestedLink: Uri): ApiResponse<ExchangeLinkResponse> {
        val url = "$baseUrl/api/v1/deeplinks/exchangeShortLink"
        
        val body = ExchangeShortLinkRequest(
            requestedLink = requestedLink.toString()
        )
        
        return post(url, body)
    }
    
    /**
     * POST 请求
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
     * 执行请求
     */
    private inline fun <reified T> execute(request: Request): ApiResponse<T> {
        return try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                
                if (!response.isSuccessful) {
                    return@use ApiResponse.error(
                        DynamicLinksSDKError.NetworkError("Server error: ${response.code}", null)
                    )
                }
                
                if (responseBody.isNullOrEmpty()) {
                    return@use ApiResponse.error(
                        DynamicLinksSDKError.NetworkError("Empty response", null)
                    )
                }
                
                try {
                    val baseResponse = gson.fromJson(responseBody, BaseApiResponse::class.java)
                    if (baseResponse.code != 0) {
                        return@use ApiResponse.error(
                            DynamicLinksSDKError.ServerError(
                                baseResponse.message ?: "Server error",
                                baseResponse.code
                            )
                        )
                    }
                    
                    val dataJson = gson.toJson(baseResponse.data)
                    val data = gson.fromJson(dataJson, T::class.java)
                    ApiResponse.success(data)
                } catch (e: Exception) {
                    ApiResponse.error(DynamicLinksSDKError.ParseError("piwenzi to parse response", e))
                }
            }
        } catch (e: IOException) {
            ApiResponse.error(DynamicLinksSDKError.NetworkError("Network error: ${e.message}", e))
        } catch (e: Exception) {
            ApiResponse.error(DynamicLinksSDKError.NetworkError("Unknown error: ${e.message}", e))
        }
    }
}

/**
 * API 响应基类
 */
internal data class BaseApiResponse(
    val code: Int,
    val message: String?,
    val data: Any?
)

/**
 * 创建 Deeplink 请求
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
    @SerializedName("ibi") val ibi: String? = null,
    @SerializedName("ifl") val ifl: String? = null,
    @SerializedName("ius") val ius: String? = null,
    @SerializedName("ipfl") val ipfl: String? = null,
    @SerializedName("ipbi") val ipbi: String? = null,
    @SerializedName("isi") val isi: String? = null,
    @SerializedName("imv") val imv: String? = null,
    @SerializedName("efr") val efr: Boolean? = null,
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
 * 解析短链接请求
 */
internal data class ExchangeShortLinkRequest(
    @SerializedName("requestedLink") val requestedLink: String
)

/**
 * Deeplink 响应 (后端返回)
 */
internal data class DeeplinkResponse(
    @SerializedName("id") val id: String,
    @SerializedName("short_link") val shortLink: String,
    @SerializedName("link") val link: String,
    @SerializedName("name") val name: String?,
    @SerializedName("apn") val apn: String?,
    @SerializedName("afl") val afl: String?,
    @SerializedName("amv") val amv: String?,
    @SerializedName("ibi") val ibi: String?,
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
 * API 响应封装
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

