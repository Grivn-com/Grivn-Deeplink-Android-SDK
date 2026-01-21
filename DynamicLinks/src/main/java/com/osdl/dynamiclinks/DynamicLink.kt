package com.osdl.dynamiclinks

import android.net.Uri
import androidx.core.net.toUri

/**
 * Represents a Dynamic Link.
 * This class is responsible for parsing the long link to extract the deep link, UTM parameters,
 * and the minimum app version required for the link to be opened properly.
 *
 * @property url The extracted deep link (if any) from the provided long URL.
 * @property utmParameters The map of UTM parameters extracted from the long URL, used for tracking purposes.
 * @property minimumAppVersion The minimum version of the app required to open the link, extracted from the "imv" parameter.
 *
 * @param longLink The long URL containing the Dynamic Link and UTM parameters.
 * This URL is parsed to extract the deep link, UTM parameters, and minimum app version.
 */
public class DynamicLink public constructor(longLink: Uri) {

    /**
     * The extracted deep link from the long URL.
     * This represents the target destination for the Dynamic Link.
     */
    public val url: Uri

    /**
     * A map of UTM parameters extracted from the long URL.
     * These parameters are commonly used for tracking the source, medium, and campaign of the link.
     */
    public val utmParameters: Map<String, String>

    /**
     * The minimum app version required to handle the Dynamic Link, extracted from the "imv" parameter.
     * If no version was specified at creation, this value will be null.
     */
    public val minimumAppVersion: Int?

    init {
        val allParams: Map<String, String?> =
            longLink.queryParameterNames.associateWith { longLink.getQueryParameter(it) }
        val rawLink = longLink.getQueryParameter("link")
        url = rawLink?.toUri()
            ?: throw IllegalArgumentException("Missing or invalid 'link' parameter in the URL")
        val amv = longLink.getQueryParameter("amv")
        minimumAppVersion = amv?.toIntOrNull()
        utmParameters = allParams.filterKeys { it.startsWith("utm_") }
            .mapValues { it.value ?: "" }
    }
}
