package dev.bgcheck.app

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.bgcheck.app.data.*
import dev.bgcheck.app.ui.BackgroundCheckApp
import dev.bgcheck.app.ui.BackgroundCheckTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InternetRecommendationsUiTest {
    @get:Rule val compose = createComposeRule()
    private val browser = AppEntry("org.mozilla.firefox", "Firefox", 10123, false, hasInternetPermission = true)
    private val system = AppEntry("com.android.systemui", "System UI", 1000, true, hasInternetPermission = true)
    private val removed = browser.copy(installed = false, uid = 1000, hasInternetPermission = false)

    private fun show(app: AppEntry, selected: Boolean = true, onNetwork: (String?) -> Unit = {}) {
        val state = mutableStateOf(UiState(access = AccessState.NOT_RUNNING, loading = false,
            apps = listOf(app).filter { it.installed }, uninstalledApps = listOf(app).filterNot { it.installed },
            selected = app.takeIf { selected }))
        compose.setContent {
            BackgroundCheckTheme {
                BackgroundCheckApp(state.value,
                    onRefresh = {}, onConnect = {}, onSelect = { state.value = state.value.copy(selected = it) },
                    onDismissDetail = { state.value = state.value.copy(selected = null) }, onAction = {}, onUndo = {},
                    onOpenAppSettings = {}, onOpenShizuku = {}, onOpenWirelessSettings = {}, onClearMessage = {},
                    onRefreshResources = {}, onRefreshEnergy = {}, onSetMonitorPaused = {}, onSetMonitorInterval = {},
                    onMonitorVisible = {}, onForceStop = {}, onOpenNetwork = onNetwork)
            }
        }
    }

    @Test fun userRecommendationIsVisibleInListAndOpensTheMatchingFirewallWithoutShizuku() {
        var opened: String? = null
        show(browser, selected = false, onNetwork = { opened = it })
        compose.onNodeWithText("Apps").performClick()
        compose.onNodeWithTag("apps-list").performScrollToNode(hasText("Firefox"))
        compose.onNodeWithText("Firewall: Allow if you use online features").assertIsDisplayed()
        compose.onNodeWithText("Firefox").performClick()
        compose.onNodeWithText("Internet firewall recommendation").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Network and internet access").performScrollTo().performClick()
        assertEquals("org.mozilla.firefox", opened)
    }

    @Test fun systemUidGetsGuidanceAndAnExplanation() {
        show(system)
        compose.onNodeWithText("Keep internet allowed").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("This package uses a core Android UID.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Recommendation details").performScrollTo().performClick()
        compose.onNodeWithText("Recommendation source").performScrollTo().assertIsDisplayed()
    }

    @Test fun uninstalledPackageShowsReinstallAdviceWithoutLiveFirewallAction() {
        show(removed)
        compose.onNodeWithText("No rule needed while uninstalled").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("If reinstalled: Allow if you use online features").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Network and internet access").assertDoesNotExist()
        compose.onNodeWithText("Force stop app").assertDoesNotExist()
    }

    @Test fun unknownPermissionAndUnknownAppRemainExplicit() {
        show(AppEntry("example.unknown", "Unknown app", 10200, false))
        compose.onNodeWithText("Review before blocking").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Recommendation details").performScrollTo().performClick()
        compose.onNodeWithText("Internet permission could not be verified; do not assume this app is offline.")
            .performScrollTo().assertIsDisplayed()
    }
}
