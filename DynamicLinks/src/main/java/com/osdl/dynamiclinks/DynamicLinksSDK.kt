package com.osdl.dynamiclinks

import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

public object DynamicLinksSDK {

    /**
     * Returns the SDK version.
     * This value should be passed as a header to your backend, it's purpose is to enable schema breaking changes
     */
    public val sdkVersion: String
        get() = BuildConfig.SDK_VERSION

    private var allowedHosts: List<String> = emptyList()

    /**
     * The client-supplied implementation that handles Dynamic Link shortening and exchange logic.
     *
     * This must be set before calling any methods that require network interaction.
     * If not set, {@link DynamicLinksSDKError.DelegateUnavailable} will be thrown.
     */
    @Volatile
    public var delegate: DynamicLinksClient? = null

    /**
     * Configures the DynamicLinks SDK by providing a list of allowed hosts. eg. (acme.wayp.link, acme-preview.wayp.link, preview.acme.wayp.link)
     *
     * @param allowedHosts The list of domains that the SDK will support.
     */
    @Synchronized
    public fun configure(allowedHosts: List<String>) {
        DynamicLinksSDK.allowedHosts = allowedHosts
    }

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
     * @throws DynamicLinksSDKError.DelegateUnavailable if the delegate is not configured.
     * @throws DynamicLinksSDKError.UnknownDelegateResponse if there is an unknown response from the delegate.
     */
    @Throws(DynamicLinksSDKError::class)
    public suspend fun handleDynamicLink(incomingUrl: Uri): DynamicLink {
        if (!isValidDynamicLink(incomingUrl)) {
            throw DynamicLinksSDKError.InvalidDynamicLink
        }

        val shortener = delegate ?: throw DynamicLinksSDKError.DelegateUnavailable

        return suspendCancellableCoroutine { continuation ->
            shortener.exchangeShortCode(incomingUrl) { exchangeLinkResponse, error ->
                when {
                    exchangeLinkResponse != null -> {
                        continuation.resume(DynamicLink(exchangeLinkResponse.longLink))
                    }

                    error != null -> {
                        continuation.resumeWithException(error)
                    }

                    else -> {
                        continuation.resumeWithException(DynamicLinksSDKError.UnknownDelegateResponse)
                    }
                }
            }
        }
    }

    /**
     * Shortens a Dynamic Link and returns the response containing the shortened URL.
     *
     * @param dynamicLink The DynamicLinkComponents that will be used to build the URI.
     * @return A DynamicLinkShortenResponse containing the shortened link.
     * @throws DynamicLinksSDKError.DelegateUnavailable if the delegate is not configured.
     * @throws DynamicLinksSDKError.InvalidDynamicLink if the link is invalid.
     * @throws DynamicLinksSDKError.UnknownDelegateResponse if there is an unknown response from the delegate.
     */
    @Throws(DynamicLinksSDKError::class)
    public suspend fun shorten(dynamicLink: DynamicLinkComponents): DynamicLinkShortenResponse {
        val shortener = delegate ?: throw DynamicLinksSDKError.DelegateUnavailable
        val longUrl = dynamicLink.buildUri() ?: throw DynamicLinksSDKError.InvalidDynamicLink

        return suspendCancellableCoroutine { continuation ->
            shortener.shortenURL(longUrl) { dynamicLinkResponse, error ->
                when {
                    dynamicLinkResponse != null -> continuation.resume(dynamicLinkResponse)
                    error != null -> continuation.resumeWithException(error)
                    else -> continuation.resumeWithException(DynamicLinksSDKError.UnknownDelegateResponse)
                }
            }
        }
    }

    /**
     * Checks if the given intent contains a valid Dynamic Link.
     *
     * @param intent The Intent that may contain a Dynamic Link.
     * @return True if the intent data is a valid Dynamic Link, false otherwise.
     */
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
    public fun isValidDynamicLink(url: Uri): Boolean {
        val host = url.host ?: return false
        val pathMatches = Regex("/[^/]+").containsMatchIn(url.path ?: "")
        return allowedHosts.contains(host) && pathMatches
    }
}
