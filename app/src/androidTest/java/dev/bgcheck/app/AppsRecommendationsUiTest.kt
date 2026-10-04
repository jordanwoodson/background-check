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
class AppsRecommendationsUiTest {
    @get:Rule val compose = createComposeRule()
    private val browser = AppEntry("com.android.chrome", "Chrome", 10123, false)
    private val system = AppEntry("com.android.systemui", "System UI", 1000, true)
    private val removed = browser.copy(installed = false, uid = 1000)

    private fun show(app: AppEntry, selected: Boolean = true, onNetwork: (AppEntry?) -> Unit = {}) {
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

    @Test fun userDebloatBadgeOpensDetailsAndKeepsTheNetworkShortcut() {
        var opened: AppEntry? = null
        show(browser, selected = false, onNetwork = { opened = it })
        compose.onNodeWithText("Apps").performClick()
        compose.onNodeWithTag("apps-list").performScrollToNode(hasText("Chrome"))
        compose.onNodeWithText("Cana: Advanced").assertIsDisplayed()
        compose.onNodeWithText("Chrome").performClick()
        compose.onNodeWithText("Cana debloat recommendation").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Advanced").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Internet firewall recommendation").assertDoesNotExist()
        compose.waitForIdle()
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        instrumentation.targetContext.filesDir.resolve("cana-recommendation.png").outputStream().use {
            screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        screenshot.recycle()
        compose.onNodeWithText("Network and internet access").performScrollTo().performClick()
        assertEquals(browser.key, opened?.key)
    }

    @Test fun systemAppsKeepCanasUnsafeRatingAndSource() {
        show(system)
        compose.onNodeWithText("Unsafe").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Cana list details").performScrollTo().performClick()
        compose.onNodeWithText("View Cana's bundled source").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Internet firewall recommendation").assertDoesNotExist()
    }

    @Test fun uninstalledFilterShowsDebloatRatingWithoutLiveActions() {
        show(removed, selected = false)
        compose.onNodeWithText("Apps").performClick()
        compose.openAppFilters()
        compose.onNodeWithTag("apps-list").performScrollToNode(hasText("Uninstalled") and isSelectable())
        compose.onNode(hasText("Uninstalled") and isSelectable()).performScrollTo().performClick()
        compose.onNodeWithTag("apps-list").performScrollToNode(hasText("Chrome"))
        compose.onNodeWithText("Cana: Advanced").assertIsDisplayed()
        compose.onNodeWithText("Chrome").performClick()
        compose.onNodeWithText("Advanced").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Already uninstalled for this user.", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Network and internet access").assertDoesNotExist()
        compose.onNodeWithText("Force stop app").assertDoesNotExist()
        compose.onNodeWithText("Internet firewall recommendation").assertDoesNotExist()
    }

    @Test fun unknownAppsExplicitlyHaveNoCanaRating() {
        show(AppEntry("com.openai.chatgpt", "ChatGPT", 10200, false))
        compose.onNodeWithText("Not rated by Cana").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Cana's bundled list has no rating for this package.", substring = true)
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Internet firewall recommendation").assertDoesNotExist()
    }

    @Test fun packageWarningsAndReplacementRestrictionsRemainVisible() {
        show(browser)
        compose.onNodeWithText("Cana list warning").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Removing the app may break creating and storing passkeys", substring = true)
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Cana list details").performScrollTo().performClick()
        compose.onNodeWithText("GrapheneOS only").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Cromite", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test fun systemFilterAlsoShowsRecommendedRemovalForKnownPackages() {
        show(AppEntry("com.motorola.help", "Moto Feedback", 10201, true), selected = false)
        compose.onNodeWithText("Apps").performClick()
        compose.openAppFilters()
        compose.onNodeWithTag("apps-list").performScrollToNode(hasText("System") and isSelectable())
        compose.onNode(hasText("System") and isSelectable()).performScrollTo().performClick()
        compose.onNodeWithTag("apps-list").performScrollToNode(hasText("Moto Feedback"))
        compose.onNodeWithText("Cana: Recommended").assertIsDisplayed()
    }
}
