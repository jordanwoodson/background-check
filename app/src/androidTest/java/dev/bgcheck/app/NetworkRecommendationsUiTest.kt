package dev.bgcheck.app

import android.content.Context
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.bgcheck.app.ui.BackgroundCheckTheme
import dev.bgcheck.app.ui.network.InternetAdviceCard
import dev.bgcheck.app.ui.network.NetworkAccessControls
import dev.bgcheck.app.ui.network.internetRecommendation
import dev.bgcheck.network.De1984Application
import dev.bgcheck.network.domain.firewall.BlockingContext
import dev.bgcheck.network.domain.model.InternetRecommendation
import dev.bgcheck.network.domain.model.InternetRecommendations
import dev.bgcheck.network.domain.model.NetworkPackage
import dev.bgcheck.network.domain.model.Package
import dev.bgcheck.network.domain.model.PackageType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NetworkRecommendationsUiTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(NetworkTestPreferences()).around(compose)
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun successfulAndroidMetadataSurvivesBothRepositoryMappings() = runBlocking {
        val dependencies = (context as De1984Application).dependencies
        val installed = dependencies.packageRepository.getPackage("com.google.android.gms", 0).getOrThrow()
        assertTrue(installed.isInstalled)
        assertTrue(installed.permissionsKnown)
        assertTrue(installed.permissions.contains("android.permission.INTERNET"))
        val network = dependencies.networkPackageRepository.getNetworkPackage("com.google.android.gms", 0).getOrThrow()
        assertTrue(network.permissionsKnown)
        assertTrue(network.hasInternetPermission)
    }

    @Test fun realFirewallEntryShowsRecommendationAlongsideItsControls() {
        compose.activityRule.scenario.onActivity { it.deliverNetworkIntent(Intent(context, MainActivity::class.java)
            .putExtra("dev.bgcheck.network.PACKAGE", "com.google.android.gms")) }
        compose.awaitNetworkNode("network-sheet")
        compose.onNode(hasText("com.google.android.gms") and hasAnyAncestor(hasTestTag("network-sheet"))).assertExists()
        compose.onNode(hasText("Keep internet allowed", substring = true) and hasAnyAncestor(hasTestTag("network-sheet")))
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Internet advice").performScrollTo().performClick()
        compose.onNodeWithText("not the current firewall status", substring = true).performScrollTo().assertIsDisplayed()
        compose.closeNetworkAppSheet()
        val row = "network-row-${android.os.Process.myUid() / 100000}:com.google.android.gms"
        compose.onNodeWithTag("network-list").performScrollToNode(hasTestTag(row))
        compose.onNode(hasText("Internet advice: Keep internet allowed") and hasAnyAncestor(hasTestTag(row)), useUnmergedTree = true).assertExists()
    }

    @Test fun allSheetControlsHandleMissingPermissionMetadataAndRetainedRecords() {
        val unknown = NetworkPackage("example.unknown", uid = -1, name = "Unknown", icon = "",
            isEnabled = true, type = PackageType.USER)
        listOf(false, true).forEach { granular ->
            showAdvice(unknown.internetRecommendation(), unknown, granular)
            expandAdvice()
            compose.onNodeWithText("Review before blocking", substring = true).assertExists()
            compose.onNode(hasText("could not be verified", substring = true) and hasAnyAncestor(hasTestTag("internet-advice")))
                .assertExists()
            compose.onNodeWithText("No direct internet permission", substring = true).assertDoesNotExist()
        }
        val retained = Package("com.google.android.calculator", uid = 1000, name = "Calculator", icon = "",
            isEnabled = false, type = PackageType.SYSTEM, isInstalled = false)
        showAdvice(packageAdvice(retained))
        expandAdvice()
        compose.onNodeWithText("No rule needed while uninstalled", substring = true).assertExists()
        compose.onNodeWithText("If reinstalled: Consider blocking internet", substring = true).assertExists()
        showAdvice(packageAdvice(retained.copy(uid = -1, isEnabled = true, isInstalled = true, permissionsKnown = true, versionCode = 1L,
            permissions = listOf("android.permission.ACCESS_NETWORK_STATE"), hasNetworkAccess = true)))
        compose.onNodeWithText("No direct internet permission", substring = true).assertExists()
    }

    @Test fun disabledInstalledPackageWithoutVersionNameStillGetsLiveAdvice() {
        showAdvice(packageAdvice(Package("org.mozilla.firefox", uid = -1, name = "Browser", icon = "",
            isEnabled = false, type = PackageType.SYSTEM, versionCode = 1,
            permissions = listOf("android.permission.INTERNET"), permissionsKnown = true)))
        compose.onNodeWithText("No rule needed while uninstalled", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Allow if you use online features", substring = true).assertExists()
    }

    @Test fun versionMetadataDoesNotProvePermissionReadSucceeded() {
        val unknown = NetworkPackage("example.unknown", uid = -1, name = "Unknown", icon = "",
            isEnabled = true, type = PackageType.USER, versionCode = 1)
        showAdvice(unknown.internetRecommendation(), unknown)
        expandAdvice()
        compose.onNode(hasText("could not be verified", substring = true) and hasAnyAncestor(hasTestTag("internet-advice")))
            .assertExists()
        compose.onNodeWithText("No direct internet permission", substring = true).assertDoesNotExist()
    }

    private fun packageAdvice(pkg: Package) = InternetRecommendations.forPackage(pkg.packageName, pkg.type == PackageType.SYSTEM,
        installed = pkg.isInstalled, uid = pkg.uid, hasInternetPermission = pkg.permissions.contains("android.permission.INTERNET").takeIf { pkg.permissionsKnown },
        enabled = pkg.isEnabled)

    private fun showAdvice(advice: InternetRecommendation, pkg: NetworkPackage? = null, granular: Boolean = false) {
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                BackgroundCheckTheme {
                    key(advice, granular) {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            InternetAdviceCard(advice)
                            if (pkg != null) NetworkAccessControls(pkg, null, BlockingContext(), granular, false, true, { _, _ -> }, {})
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }
    private fun expandAdvice() = compose.onNodeWithText("Internet advice").performClick()
}
