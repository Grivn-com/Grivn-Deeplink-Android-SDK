package com.osdl.dynamiclinks

import android.net.Uri
import androidx.core.net.toUri

/**
 * 解析短链接的响应
 * 后端返回的是 URL query parameters，SDK 将其组装成长链接
 */
public data class ExchangeLinkResponse(
    // 后端返回的各个参数字段
    val link: String? = null,
    val apn: String? = null,
    val afl: String? = null,
    val amv: String? = null,
    val ibi: String? = null,
    val ifl: String? = null,
    val ius: String? = null,
    val ipfl: String? = null,
    val ipbi: String? = null,
    val isi: String? = null,
    val imv: String? = null,
    val efr: String? = null,
    val ofl: String? = null,
    val st: String? = null,
    val sd: String? = null,
    val si: String? = null,
    val utm_source: String? = null,
    val utm_medium: String? = null,
    val utm_campaign: String? = null,
    val utm_content: String? = null,
    val utm_term: String? = null,
    val at: String? = null,
    val ct: String? = null,
    val mt: String? = null,
    val pt: String? = null
) {
    /**
     * 将后端返回的参数组装成长链接 URI
     */
    val longLink: Uri
        get() {
            val baseLink = link ?: throw IllegalStateException("Missing 'link' parameter")
            val builder = Uri.parse("https://dynamiclinks.local").buildUpon()
            
            // 添加所有非空参数
            builder.appendQueryParameter("link", baseLink)
            apn?.let { builder.appendQueryParameter("apn", it) }
            afl?.let { builder.appendQueryParameter("afl", it) }
            amv?.let { builder.appendQueryParameter("amv", it) }
            ibi?.let { builder.appendQueryParameter("ibi", it) }
            ifl?.let { builder.appendQueryParameter("ifl", it) }
            ius?.let { builder.appendQueryParameter("ius", it) }
            ipfl?.let { builder.appendQueryParameter("ipfl", it) }
            ipbi?.let { builder.appendQueryParameter("ipbi", it) }
            isi?.let { builder.appendQueryParameter("isi", it) }
            imv?.let { builder.appendQueryParameter("imv", it) }
            efr?.let { builder.appendQueryParameter("efr", it) }
            ofl?.let { builder.appendQueryParameter("ofl", it) }
            st?.let { builder.appendQueryParameter("st", it) }
            sd?.let { builder.appendQueryParameter("sd", it) }
            si?.let { builder.appendQueryParameter("si", it) }
            utm_source?.let { builder.appendQueryParameter("utm_source", it) }
            utm_medium?.let { builder.appendQueryParameter("utm_medium", it) }
            utm_campaign?.let { builder.appendQueryParameter("utm_campaign", it) }
            utm_content?.let { builder.appendQueryParameter("utm_content", it) }
            utm_term?.let { builder.appendQueryParameter("utm_term", it) }
            at?.let { builder.appendQueryParameter("at", it) }
            ct?.let { builder.appendQueryParameter("ct", it) }
            mt?.let { builder.appendQueryParameter("mt", it) }
            pt?.let { builder.appendQueryParameter("pt", it) }
            
            return builder.build()
        }
}
