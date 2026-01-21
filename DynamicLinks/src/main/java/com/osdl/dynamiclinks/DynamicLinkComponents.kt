package com.osdl.dynamiclinks

import android.net.Uri
import androidx.core.net.toUri


/**
 * Data class representing the components that make up a Dynamic Link.
 *
 * This class is used to construct a Dynamic Link, containing the base URI (`domainUriPrefix`),
 * query parameters for various platforms (iOS, Android, etc.), and additional parameters like analytics,
 * social media tags, and options.
 *
 * @property link The target URI that the Dynamic Link represents. eg. mydomain.com/signup.
 * @property domainUriPrefix The base URI to be used for the Dynamic Link.
 * @property iOSParameters Parameters specific to iOS devices for the Dynamic Link.
 * @property androidParameters Parameters specific to Android devices for the Dynamic Link.
 * @property iTunesConnectParameters Parameters for iTunes Connect analytics related to the link.
 * @property socialMetaTagParameters Parameters for social media meta tags associated with the link.
 * @property options Additional options related to the Dynamic Link.
 * @property otherPlatformParameters Parameters for other platforms supported by the Dynamic Link.
 * @property analyticsParameters Parameters related to analytics for tracking the Dynamic Link.
 */
public data class DynamicLinkComponents(
    public val link: Uri,
    public val domainUriPrefix: String,
    public val iOSParameters: IosParameters = IosParameters(),
    public val androidParameters: AndroidParameters? = null,
    public val iTunesConnectParameters: ItunesConnectAnalyticsParameters? = null,
    public val socialMetaTagParameters: SocialMetaTagParameters? = null,
    public val options: DynamicLinkOptionsParameters = DynamicLinkOptionsParameters(),
    public val otherPlatformParameters: OtherPlatformParameters? = null,
    public val analyticsParameters: AnalyticsParameters? = null
) {

    /**
     * Builds the final URI for the Dynamic Link by appending query parameters for various platforms and options.
     *
     * This function ensures that the domain URI prefix starts with "https://", then constructs the URI
     * by adding the necessary parameters for the link, including those for iOS, Android, analytics, social
     * media tags, and any other relevant parameters.
     *
     * @return The constructed URI for the Dynamic Link, including all the query parameters.
     * @throws IllegalArgumentException if the domainUriPrefix does not start with "https://".
     */
    @Throws(IllegalArgumentException::class)
    public fun buildUri(): Uri? {
        if (!domainUriPrefix.startsWith("https://")) {
            throw IllegalArgumentException("Domain URI must start with https://")
        }

        val builder = domainUriPrefix.toUri().buildUpon()
            .appendQueryParameter("link", link.toString())

        fun addParams(encodable: DynamicLinkParameter?) {
            encodable?.toMap()?.forEach { (key, value) ->
                val encodedKey = Uri.encode(key)
                val encodedValue = Uri.encode(value.toString())
                builder.appendQueryParameter(encodedKey, encodedValue)
            }
        }

        addParams(analyticsParameters)
        addParams(socialMetaTagParameters)
        addParams(iOSParameters)
        addParams(androidParameters)
        addParams(iTunesConnectParameters)
        addParams(otherPlatformParameters)
        addParams(options)

        return builder.build()
    }
}


/**
 * Represents the parameters specific to Android devices in a Dynamic Link.
 * These parameters are used to configure the behavior of the Dynamic Link on Android devices,
 * including the package name, a fallback URL, and the minimum app version required.
 *
 * @property packageName The package name of the Android app associated with the link.
 * @property fallbackURL The URL to redirect to if the app is not installed on the Android device.
 * By default the user will be taken to your apps Google Play Store listing
 * @property minimumVersion The minimum version of the app required to open the link.
 */
public data class AndroidParameters(
    public val packageName: String,
    public val fallbackURL: String? = null,
    public val minimumVersion: Int = 0
) : DynamicLinkParameter {
    override fun toMap(): Map<String, String> =
        buildMap {
            put("apn", packageName)
            fallbackURL?.let { put("afl", it) }
            put("amv", minimumVersion.toString())
        }
}

/**
 * Represents the UTM parameters used for tracking analytics in a Dynamic Link.
 * These parameters are typically used to track the performance of marketing campaigns
 * and help analyze the source, medium, campaign, term, and content of the traffic.
 *
 * @property source The source of the traffic (e.g., "Google", "Facebook") - typically mapped to "utm_source".
 * @property medium The medium through which the traffic came (e.g., "CPC", "Email") - typically mapped to "utm_medium".
 * @property campaign The name of the marketing campaign - typically mapped to "utm_campaign".
 * @property term The search term or keyword associated with the traffic - typically mapped to "utm_term".
 * @property content The specific content or variation of the campaign - typically mapped to "utm_content".
 */
public data class AnalyticsParameters(
    public val source: String? = null,
    public val medium: String? = null,
    public val campaign: String? = null,
    public val term: String? = null,
    public val content: String? = null
) : DynamicLinkParameter {
    override fun toMap(): Map<String, String> =
        listOfNotNull(
            source?.let { "utm_source" to it },
            medium?.let { "utm_medium" to it },
            campaign?.let { "utm_campaign" to it },
            term?.let { "utm_term" to it },
            content?.let { "utm_content" to it }
        ).toMap()
}


/**
 * Represents the parameters specific to iOS devices in a Dynamic Link.
 * These parameters help configure the behavior of the link on iOS devices, such as defining
 * the App Store ID, fallback URLs, and the minimum app version required to open the link.
 *
 * @property appStoreID The App Store ID for the app associated with the link.
 * @property fallbackURL The URL to redirect to if the app is not installed on the iOS device.
 * By default the user will be taken to your iOS app store listing
 * @property iPadFallbackURL The URL to be used as a fallback on iPad devices when the app is not installed.
 * By default the user will be taken to your iOS app store listing
 * @property minimumAppVersion The minimum version of the iOS app required to open the link.
 */
public data class IosParameters(
    public val appStoreID: String? = null,
    public val fallbackURL: String? = null,
    public val iPadFallbackURL: String? = null,
    public val minimumAppVersion: String? = null
) : DynamicLinkParameter {
    override fun toMap(): Map<String, String> =
        listOfNotNull(
            appStoreID?.let { "isi" to it },
            fallbackURL?.let { "ifl" to it },
            iPadFallbackURL?.let { "ipfl" to it },
            minimumAppVersion?.let { "imv" to it }
        ).toMap()
}

/**
 * Represents the parameters used for iTunes Connect analytics in a Dynamic Link.
 * These parameters are typically used to track affiliate marketing campaigns and measure
 * the effectiveness of marketing efforts for apps in the iTunes Store.
 *
 * @property affiliateToken The affiliate token used for tracking affiliate marketing.
 * @property campaignToken The campaign token used to track specific marketing campaigns.
 * @property providerToken The provider token used to identify the provider for iTunes Connect.
 */
public data class ItunesConnectAnalyticsParameters(
    public val affiliateToken: String? = null,
    public val campaignToken: String? = null,
    public val providerToken: String? = null
) : DynamicLinkParameter {
    override fun toMap(): Map<String, String> =
        listOfNotNull(
            affiliateToken?.let { "at" to it },
            campaignToken?.let { "ct" to it },
            providerToken?.let { "pt" to it }
        ).toMap()
}

/**
 * Represents the options for a Dynamic Link, such as the length of the path used in the link.
 * This class allows setting the path length, which determines whether the path should be easily guessable
 * or more secure (unguessable).
 *
 * @property pathLength The length of the Dynamic Link path. The default value is `UNGUESSABLE`.
 */
public data class DynamicLinkOptionsParameters(
    val pathLength: DynamicLinkPathLength = DynamicLinkPathLength.UNGUESSABLE
) : DynamicLinkParameter {
    override fun toMap(): Map<String, String> =
        mapOf("pathLength" to pathLength.toQueryValue())
}

/**
 * Enum representing the possible lengths for the Dynamic Link path.
 *
 * - `UNGUESSABLE` indicates that the path should be long and secure, making it difficult to guess.
 * - `SHORT` indicates a shorter, easier-to-guess path, suitable for cases where non user specific content is being shared.
 *
 * @property value The integer value representing the path length option.
 */
public enum class DynamicLinkPathLength private constructor(public val value: Int) {
    UNGUESSABLE(0),
    SHORT(1);

    public fun toQueryValue(): String = when (this) {
        SHORT -> "SHORT"
        UNGUESSABLE -> "UNGUESSABLE"
    }
}

/**
 * Represents additional parameters that can be included in a Dynamic Link for other platforms
 *
 * This class is used to provide a fallback URL, which can be used as a secondary link to redirect
 * users if they are not on an Android or iOS device.
 *
 * @property fallbackURL The URL to redirect users to if they are not on an Android or iOS device.
 */
public data class OtherPlatformParameters(
    val fallbackURL: String? = null
) : DynamicLinkParameter {
    override fun toMap(): Map<String, String> =
        fallbackURL?.let { mapOf("ofl" to it) } ?: emptyMap()
}

/**
 * Represents the parameters used for social media meta tags in a Dynamic Link.
 * These parameters are used to customize the content that shows up on the preview page.
 * For example if this Dynamic Link represents a piece of content in your app,
 * the Dynamic Link preview page can represent that with these parameters
 *
 * @property title The title text to be shown on the preview page.
 * @property descriptionText The description text to be shown on the preview page.
 * @property imageURL The URL of the image to be shown on the preview page.
 */
public data class SocialMetaTagParameters(
    public val title: String? = null,
    public val descriptionText: String? = null,
    public val imageURL: String? = null
) : DynamicLinkParameter {
    override fun toMap(): Map<String, String> =
        listOfNotNull(
            title?.let { "st" to it },
            descriptionText?.let { "sd" to it },
            imageURL?.let { "si" to it }
        ).toMap()
}
