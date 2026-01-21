package com.osdl.dynamiclinks

import android.net.Uri

public interface DynamicLinksClient {
    public fun shortenURL(
        longUrl: Uri,
        callback: (DynamicLinkShortenResponse?, Exception?) -> Unit
    )

    public fun exchangeShortCode(
        requestedLink: Uri,
        callback: (ExchangeLinkResponse?, Exception?) -> Unit
    )
}