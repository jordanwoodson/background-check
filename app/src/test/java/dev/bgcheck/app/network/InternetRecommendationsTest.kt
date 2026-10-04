package dev.bgcheck.app.network

import dev.bgcheck.network.domain.model.InternetAdvice
import dev.bgcheck.network.domain.model.InternetRecommendations
import org.junit.Assert.*
import org.junit.Test

class InternetRecommendationsTest {
    private fun advice(id: String, system: Boolean = false) =
        InternetRecommendations.forPackage(id, system, uid = 10234, hasInternetPermission = true)

    @Test fun essentialConnectivityAndRemoteAccessRemainAllowed() {
        listOf("com.google.android.networkstack", "com.google.android.gms", "com.tailscale.ipn", "sh.haven.app",
            "com.motorola.ccc.ota", "com.android.providers.downloads").forEach {
            assertEquals(it, InternetAdvice.KEEP_ALLOWED, advice(it).advice)
        }
    }

    @Test fun onlineAppsExplainWhatBlockingWouldLose() {
        listOf("org.thoughtcrime.securesms", "com.openai.chatgpt", "org.mozilla.firefox", "dev.imranr.obtainium").forEach {
            val result = advice(it)
            assertEquals(it, InternetAdvice.ALLOW_IF_USED, result.advice)
            assertTrue(it, result.consequence.isNotBlank())
            assertNotNull(it, result.sourceUrl)
        }
    }

    @Test fun offlineToolsCanBeBlockedWithoutTreatingUnknownAppsAsOffline() {
        assertEquals(InternetAdvice.BLOCK_OPTIONAL, advice("com.google.android.calculator").advice)
        assertEquals(InternetAdvice.REVIEW, advice("com.google.android.calculator.fake").advice)
        assertEquals(InternetAdvice.REVIEW, advice("com.example.unknown").advice)
        assertEquals(InternetAdvice.KEEP_ALLOWED, advice("com.vendor.unknown", system = true).advice)
    }

    @Test fun sharedAndCoreUidsOverrideOtherwiseBlockableAdvice() {
        listOf(1000, 101000).forEach { uid ->
            val result = InternetRecommendations.forPackage("com.google.android.calculator", false, uid = uid,
                hasInternetPermission = false)
            assertEquals(InternetAdvice.KEEP_ALLOWED, result.advice)
        }
        val result = InternetRecommendations.forPackage("com.google.android.calculator", false, uid = 10123,
            hasInternetPermission = false, sharedUid = true)
        assertEquals(InternetAdvice.KEEP_ALLOWED, result.advice)
        assertTrue(result.reason.contains("UID"))
    }

    @Test fun verifiedAbsentPermissionIsDifferentFromUnreadablePermission() {
        assertEquals(InternetAdvice.NO_DIRECT_ACCESS, InternetRecommendations.forPackage("com.example.app", false,
            uid = 10123, hasInternetPermission = false).advice)
        assertEquals(InternetAdvice.REVIEW, InternetRecommendations.forPackage("com.example.app", false,
            uid = -1, hasInternetPermission = null).advice)
    }

    @Test fun removedPackageIgnoresStaleUidAndPermissionButKeepsReinstallAdvice() {
        val removed = InternetRecommendations.forPackage("com.google.android.calculator", true,
            installed = false, uid = 1000, hasInternetPermission = false, sharedUid = true, critical = true)
        assertEquals(InternetAdvice.UNINSTALLED, removed.advice)
        assertEquals(InternetAdvice.BLOCK_OPTIONAL, removed.afterInstall?.advice)
        val online = InternetRecommendations.forPackage("org.thoughtcrime.securesms", false, installed = false)
        assertEquals(InternetAdvice.ALLOW_IF_USED, online.afterInstall?.advice)
    }

    @Test fun disabledAppStillHasAdviceForWhenItIsEnabled() {
        val result = InternetRecommendations.forPackage("org.mozilla.firefox", false, uid = 10123,
            hasInternetPermission = true, enabled = false)
        assertEquals(InternetAdvice.ALLOW_IF_USED, result.advice)
        assertTrue(result.notes.any { it.contains("disabled", true) })
    }

    @Test fun backendProtectedAppIsNeverSuggestedForBlocking() {
        assertEquals(InternetAdvice.KEEP_ALLOWED, InternetRecommendations.forPackage("com.google.android.calculator",
            false, uid = 10123, critical = true, hasInternetPermission = true).advice)
    }

}
