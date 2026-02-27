package com.osdl.dynamiclinks

import android.net.Uri
import androidx.core.net.toUri
import com.google.gson.annotations.SerializedName

/**
 * Response returned when shortening a dynamic link.
 */
public data class DynamicLinkShortenResponse(
    @SerializedName("id") val id: String,
    @SerializedName("short_link") val shortLinkString: String,
    @SerializedName("link") val link: String,
    @SerializedName("name") val name: String? = null,
    val warnings: List<Warning> = emptyList()
) {
    /** Short link as a `Uri`. */
    val shortLink: Uri get() = shortLinkString.toUri()
    
    public data class Warning(
        val warningCode: String,
        val warningMessage: String
    )
}