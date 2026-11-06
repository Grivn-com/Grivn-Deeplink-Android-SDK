package com.osdl.dynamiclinks

import com.google.gson.Gson
import com.osdl.dynamiclinks.network.DeferredDeeplinkRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GRIVN-257: an app Play installed is matched by its install referrer alone;
 * only an install with no referrer to go by is matched on the network.
 */
class DeferredLookupTest {

    private val grivn = "deeplink_id=AbCd1234&grivn_click=6f1c2b9e-1d2a-4c3b-9a8e-0f1e2d3c4b5a&utm_source=grivn"

    @Test
    fun playInstall_fromAGrivnLink_asksByReferrer() {
        assertEquals(DeferredLookup.ByReferrer(grivn), DeferredLookup.of(installedByPlay = true, referrer = grivn))
        // a customer's own values on a Play-listing fallback stay in the referrer
        val withOwn = "utm_source=newsletter&deeplink_id=AbCd1234&grivn_click=x"
        assertEquals(DeferredLookup.ByReferrer(withOwn), DeferredLookup.of(true, withOwn))
    }

    @Test
    fun playInstall_notFromALink_findsNothing() {
        for (referrer in listOf(
            "utm_source=google-play&utm_medium=organic",
            "",
            "deeplink_id=&utm_source=grivn",
            "deeplink_id",
            "my_deeplink_id=AbCd1234",
        )) {
            assertEquals(referrer, DeferredLookup.NotFromALink, DeferredLookup.of(true, referrer))
        }
    }

    @Test
    fun noReferrerToGoBy_matchesOnTheNetwork() {
        // Play couldn't hand over the referrer
        assertEquals(DeferredLookup.ByFingerprint, DeferredLookup.of(true, null))
        // Play didn't install the app (Android Studio, adb, another store): whatever
        // referrer Play reports doesn't describe this install
        assertEquals(DeferredLookup.ByFingerprint, DeferredLookup.of(false, null))
        assertEquals(DeferredLookup.ByFingerprint, DeferredLookup.of(false, "utm_source=google-play&utm_medium=organic"))
        assertEquals(DeferredLookup.ByFingerprint, DeferredLookup.of(false, grivn))
    }

    @Test
    fun fingerprintRequest_saysTheInstallHasNoReferrer() {
        // the backend matches an Android install by fingerprint only with this flag
        val json = Gson().toJson(DeferredDeeplinkRequest(userAgent = "ua", installReferrerUnavailable = true))
        assertTrue(json, json.contains("\"install_referrer_unavailable\":true"))
    }
}
