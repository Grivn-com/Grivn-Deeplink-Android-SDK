package com.osdl.dynamiclinks

import android.net.Uri

public data class DynamicLinkShortenResponse(
    val shortLink: Uri,
    val warnings: List<Warning>
) {
    public data class Warning(
        val warningCode: String,
        val warningMessage: String
    )
}