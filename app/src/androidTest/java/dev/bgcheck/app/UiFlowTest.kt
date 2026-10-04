package dev.bgcheck.app

import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.bgcheck.app.data.AccessState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Full UI path targets only the dedicated fixture installed by the test operator. */
@RunWith(AndroidJUnit4::class)
class UiFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun searchConfirmRestrictAndUndoFixture() {
        val fixturePackage = "dev.bgcheck.fixture"
        lateinit var model: BackgroundViewModel
        compose.runOnUiThread { model = ViewModelProvider(compose.activity)[BackgroundViewModel::class.java] }
        compose.waitUntil(30_000) {
            val state = model.state.value
            state.access == AccessState.READY && !state.refreshing &&
                state.apps.any { it.packageName == fixturePackage }
        }
        compose.onNodeWithText("Apps", useUnmergedTree = false).performClick()
        compose.onNodeWithTag("apps-list").performScrollToNode(hasTestTag("apps-profile-0"))
        compose.onNodeWithTag("apps-profile-0").performClick()
        compose.onNode(hasSetTextAction()).performTextInput(fixturePackage)
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.waitUntil(20_000) {
            compose.onAllNodesWithText("Background Check test app").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Background Check test app").performClick()
        compose.waitUntil(20_000) {
            val state = model.state.value
            val policy = state.policy
            policy?.packageName == fixturePackage && policy.backgroundMode != null &&
                policy.error == null && !state.detailLoading && !state.busy
        }
        if (model.state.value.policy?.backgroundMode != "allow") {
            compose.onNodeWithText("Allow background work").performScrollTo().performClick()
            confirmPolicyChange("Allow background work?", "Allow background work")
            compose.waitUntil(20_000) {
                val state = model.state.value
                val policy = state.policy
                policy?.packageName == fixturePackage && policy.backgroundMode == "allow" &&
                    !state.busy && !state.detailLoading && !state.refreshing
            }
        }
        compose.onNodeWithText("Allowed").assertExists()
        compose.onNodeWithText("Restrict background work").performScrollTo().performClick()
        compose.onNodeWithText("Restrict background work?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Allowed").assertExists()
        compose.runOnIdle { assertEquals("allow", model.state.value.policy?.backgroundMode) }

        val previousIds = model.state.value.changes.map { it.id }.toSet()
        compose.onNodeWithText("Restrict background work").performScrollTo().performClick()
        confirmPolicyChange("Restrict background work?", "Restrict background work")
        compose.waitUntil(20_000) {
            val state = model.state.value
            val policy = state.policy
            policy?.packageName == fixturePackage && policy.backgroundMode in setOf("ignore", "deny") &&
                !state.busy && !state.detailLoading && !state.refreshing && state.changes.any {
                    it.id !in previousIds && it.packageName == fixturePackage && !it.recovery && !it.undone
                }
        }
        val change = model.state.value.changes.single { it.id !in previousIds && it.packageName == fixturePackage }
        assertEquals("Undo must restore the verified allowed baseline", "allow", change.before.backgroundMode)
        compose.onNodeWithText("Restricted").assertExists()
        compose.onNodeWithContentDescription("Close app details").assertIsDisplayed().performClick()
        compose.onNodeWithText("Overview").performClick()
        val recordTag = "history-record-${change.id}"
        compose.onNodeWithTag("overview-list").performScrollToNode(hasTestTag(recordTag))
        compose.onNode(hasText("Undo") and hasAnyAncestor(hasTestTag(recordTag)))
            .performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(20_000) {
            val state = model.state.value
            !state.busy && !state.refreshing && state.changes.any { it.id == change.id && it.undone && !it.recovery }
        }
        compose.onNode(hasText("Restored") and hasAnyAncestor(hasTestTag(recordTag))).assertIsDisplayed()
    }

    @Test fun workSettingsShortcutOpensAndroidSettingsAsTheManagedUser() {
        lateinit var model: BackgroundViewModel
        compose.runOnUiThread { model = ViewModelProvider(compose.activity)[BackgroundViewModel::class.java] }
        compose.waitUntil(30_000) {
            model.state.value.let { it.access == AccessState.READY && !it.loading && !it.refreshing && it.apps.isNotEmpty() }
        }
        val work = model.state.value.apps.firstOrNull { it.packageName == "dev.bgcheck.fixture" && it.userId == 10 }
        org.junit.Assume.assumeTrue("Requires the disposable emulator's user 10 fixture", work != null)
        compose.runOnUiThread { model.select(requireNotNull(work)) }
        compose.onNodeWithText("Open Android app settings").performScrollTo().performClick()
        try {
            compose.waitUntil(20_000) {
                val activities = shell("dumpsys activity activities")
                activities.lineSequence().any { line ->
                    (line.contains("ResumedActivity") || line.contains("mResumed")) &&
                        line.contains("u10 com.android.settings/")
                }
            }
        } finally {
            shell("input keyevent KEYCODE_BACK")
        }
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command))
            .bufferedReader().use { it.readText() }

    private fun confirmPolicyChange(title: String, action: String) {
        compose.onNodeWithText(title).assertIsDisplayed()
        compose.onNode(hasText(action) and hasClickAction() and
            hasAnyAncestor(isDialog() and hasAnyDescendant(hasText(title)))).performClick()
    }
}
