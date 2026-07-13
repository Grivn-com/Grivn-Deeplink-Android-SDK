package com.osdl.dynamiclinks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic smoke tests for the client-facing surface: the parameter -> wire-key
 * mapping that `shorten()` sends to the backend (create contract), and the
 * `DeferredDeeplinkData` accessors used for first-launch attribution.
 *
 * These run on the JVM (no emulator, no Robolectric). The `android.net.Uri`-based
 * link parser (`DynamicLink` / `isValidDynamicLink`) is a non-functional stub under
 * plain JVM unit tests; it is covered by the iOS smoke suite (same parse logic) and
 * left for a Robolectric follow-up (GRIVN-102).
 */
class DynamicLinkSmokeTest {

    @Test
    fun androidParameters_mapsToWireKeys_andOmitsNullFallback() {
        val map = AndroidParameters(packageName = "com.example.app", minimumVersion = 7).toMap()
        assertEquals("com.example.app", map["apn"])
        assertEquals("7", map["amv"])
        assertFalse("afl omitted when fallbackURL is null", map.containsKey("afl"))
    }

    @Test
    fun androidParameters_includesFallbackWhenPresent() {
        val map = AndroidParameters(
            packageName = "com.example.app",
            fallbackURL = "https://example.com/fallback",
            minimumVersion = 0
        ).toMap()
        assertEquals("https://example.com/fallback", map["afl"])
        assertEquals("0", map["amv"])
    }

    @Test
    fun analyticsParameters_mapUtmKeys_andOmitNulls() {
        val map = AnalyticsParameters(source = "google", medium = "cpc").toMap()
        assertEquals("google", map["utm_source"])
        assertEquals("cpc", map["utm_medium"])
        assertFalse(map.containsKey("utm_campaign"))
        assertFalse(map.containsKey("utm_term"))
        assertFalse(map.containsKey("utm_content"))
    }

    @Test
    fun iosParameters_mapToWireKeys() {
        val map = IosParameters(
            appStoreID = "123456",
            fallbackURL = "https://example.com/ios",
            iPadFallbackURL = "https://example.com/ipad",
            minimumAppVersion = "1.3.0"
        ).toMap()
        assertEquals("123456", map["isi"])
        assertEquals("https://example.com/ios", map["ifl"])
        assertEquals("https://example.com/ipad", map["ipfl"])
        assertEquals("1.3.0", map["imv"])
    }

    @Test
    fun pathLength_wireValue() {
        assertEquals("SHORT", DynamicLinkPathLength.SHORT.toPathMode())
        assertEquals("UNGUESSABLE", DynamicLinkPathLength.UNGUESSABLE.toPathMode())
    }

    @Test
    fun deferredDeeplinkData_readsFieldsFromLinkData() {
        val data = DeferredDeeplinkData(
            found = true,
            linkData = mapOf(
                "deeplink_id" to "dl_abc",
                "project_id" to "proj_1",
                "original_url" to "https://app.example.com/welcome",
                "utm_source" to "newsletter",
                "custom_key" to "custom_value"
            )
        )
        assertTrue(data.found)
        assertEquals("dl_abc", data.deeplinkId)
        assertEquals("proj_1", data.projectId)
        assertEquals("https://app.example.com/welcome", data.originalUrl)
        assertEquals("newsletter", data.utmSource)
        assertEquals("custom_value", data.getCustomData("custom_key"))
    }

    @Test
    fun deferredDeeplinkData_nullLinkData_returnsNullsWithoutCrashing() {
        val data = DeferredDeeplinkData(found = false, linkData = null)
        assertFalse(data.found)
        assertNull(data.deeplinkId)
        assertNull(data.utmSource)
        assertNull(data.getCustomData("anything"))
    }
}
