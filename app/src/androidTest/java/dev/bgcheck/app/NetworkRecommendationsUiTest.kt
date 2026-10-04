package dev.bgcheck.app

import android.content.Context
import android.content.Intent
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bgcheck.network.databinding.BottomSheetPackageActionGranularBinding
import dev.bgcheck.network.databinding.BottomSheetPackageActionSimpleBinding
import dev.bgcheck.network.databinding.BottomSheetPackageActionsBinding
import dev.bgcheck.network.domain.model.NetworkPackage
import dev.bgcheck.network.domain.model.Package
import dev.bgcheck.network.domain.model.PackageType
import dev.bgcheck.network.ui.common.InternetRecommendationView
import dev.bgcheck.network.utils.Constants
import dev.bgcheck.network.De1984Application
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import dev.bgcheck.network.R as NetworkR

@RunWith(AndroidJUnit4::class)
class NetworkRecommendationsUiTest {
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
        val prefs = context.getSharedPreferences(Constants.Settings.PREFS_NAME, Context.MODE_PRIVATE)
        val startPrompt = prefs.getBoolean(Constants.Settings.KEY_SHOW_FIREWALL_START_PROMPT, true)
        prefs.edit().putBoolean(Constants.Settings.KEY_SHOW_FIREWALL_START_PROMPT, false).commit()
        try {
            ActivityScenario.launch<android.app.Activity>(Intent(context, dev.bgcheck.network.ui.MainActivity::class.java)
                .putExtra("dev.bgcheck.network.PACKAGE", "com.google.android.gms")).use { scenario ->
                // The package shortcut opens its sheet automatically. Verify its recommendation
                // before closing the sheet to inspect the same advice on the inventory row.
                val sheetDeadline = android.os.SystemClock.uptimeMillis() + 20_000
                while (true) {
                    try {
                        onView(allOf(withId(NetworkR.id.action_sheet_package_name), withText(containsString("com.google.android.gms")), isDisplayed()))
                            .check(matches(isDisplayed()))
                        break
                    } catch (failure: androidx.test.espresso.NoMatchingViewException) {
                        if (android.os.SystemClock.uptimeMillis() >= sheetDeadline) throw failure
                        Thread.sleep(100)
                    }
                }
                onView(withId(NetworkR.id.internet_recommendation))
                    .check(matches(withText(containsString("Keep internet allowed"))))
                    .perform(click())
                    .check(matches(withText(containsString("not the current firewall status"))))
                    .perform(click())
                val image = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                context.filesDir.resolve("network-recommendation.png").outputStream().use { output ->
                    image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
                }
                image.recycle()
                scenario.closeNetworkAppSheet()
                val deadline = android.os.SystemClock.uptimeMillis() + 20_000
                while (true) {
                    try {
                        onView(allOf(withId(NetworkR.id.package_name), withText("com.google.android.gms"), isDisplayed()))
                            .check(matches(isDisplayed()))
                        onView(allOf(withId(NetworkR.id.internet_recommendation_summary), isDisplayed()))
                            .check(matches(withText("Internet advice: Keep internet allowed")))
                        break
                    } catch (failure: androidx.test.espresso.NoMatchingViewException) {
                        if (android.os.SystemClock.uptimeMillis() >= deadline) throw failure
                        Thread.sleep(100)
                    }
                }
            }
        } finally {
            prefs.edit().putBoolean(Constants.Settings.KEY_SHOW_FIREWALL_START_PROMPT, startPrompt).commit()
        }
    }

    @Test fun allSheetLayoutsHandleMissingPermissionMetadataAndRetainedRecords() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val inflater = LayoutInflater.from(ContextThemeWrapper(context, NetworkR.style.Theme_De1984_Views))
            val unknown = NetworkPackage("example.unknown", uid = -1, name = "Unknown", icon = "",
                isEnabled = true, type = PackageType.USER)
            listOf(BottomSheetPackageActionSimpleBinding.inflate(inflater).internetRecommendation,
                BottomSheetPackageActionGranularBinding.inflate(inflater).internetRecommendation).forEach { view ->
                InternetRecommendationView.bind(view, unknown)
                view.performClick()
                assertTrue(view.text.contains("Review before blocking"))
                assertTrue(view.text.contains("could not be verified"))
                assertFalse(view.text.contains("No direct internet permission"))
            }
            val view = BottomSheetPackageActionsBinding.inflate(inflater).internetRecommendation
            val retained = Package("com.google.android.calculator", uid = 1000, name = "Calculator", icon = "",
                isEnabled = false, type = PackageType.SYSTEM, isInstalled = false)
            InternetRecommendationView.bind(view, retained)
            view.performClick()
            assertTrue(view.text.contains("No rule needed while uninstalled"))
            assertTrue(view.text.contains("If reinstalled: Consider blocking internet"))
            InternetRecommendationView.bind(view, retained.copy(uid = -1, isEnabled = true, isInstalled = true, permissionsKnown = true, versionCode = 1L,
                permissions = listOf("android.permission.ACCESS_NETWORK_STATE"), hasNetworkAccess = true))
            assertTrue(view.text.contains("No direct internet permission"))
        }
    }

    @Test fun disabledInstalledPackageWithoutVersionNameStillGetsLiveAdvice() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val inflater = LayoutInflater.from(ContextThemeWrapper(context, NetworkR.style.Theme_De1984_Views))
            val view = BottomSheetPackageActionsBinding.inflate(inflater).internetRecommendation
            InternetRecommendationView.bind(view, Package("org.mozilla.firefox", uid = -1,
                name = "Browser", icon = "", isEnabled = false, type = PackageType.SYSTEM, versionCode = 1,
                permissions = listOf("android.permission.INTERNET"), permissionsKnown = true))
            assertFalse(view.text.contains("No rule needed while uninstalled"))
            assertTrue(view.text.contains("Allow if you use online features"))
        }
    }

    @Test fun versionMetadataDoesNotProvePermissionReadSucceeded() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val inflater = LayoutInflater.from(ContextThemeWrapper(context, NetworkR.style.Theme_De1984_Views))
            val view = BottomSheetPackageActionSimpleBinding.inflate(inflater).internetRecommendation
            InternetRecommendationView.bind(view, NetworkPackage("example.unknown", uid = -1,
                name = "Unknown", icon = "", isEnabled = true, type = PackageType.USER, versionCode = 1))
            view.performClick()
            assertTrue(view.text.contains("could not be verified"))
            assertFalse(view.text.contains("No direct internet permission"))
        }
    }
}
