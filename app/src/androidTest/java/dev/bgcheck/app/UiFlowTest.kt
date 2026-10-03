package dev.bgcheck.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Full UI path targets only the dedicated fixture installed by the test operator. */
@RunWith(AndroidJUnit4::class)
class UiFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun searchConfirmRestrictAndUndoFixture() {
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText("Connected").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Apps", useUnmergedTree = false).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("dev.bgcheck.fixture")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.waitUntil(20_000) {
            compose.onAllNodesWithText("Background Check test app").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Background Check test app").performClick()
        compose.waitUntil(20_000) {
            compose.onAllNodesWithText("Allowed").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Restrict background work").performScrollTo().performClick()
        compose.onNodeWithText("Restrict background work?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Allowed").assertExists()
        compose.onNodeWithText("Restrict background work").performScrollTo().performClick()
        compose.onAllNodesWithText("Restrict background work").onLast().performClick()
        compose.waitUntil(20_000) {
            compose.onAllNodesWithText("Restricted").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Close app details").performScrollTo().performClick()
        compose.onNodeWithText("Overview").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Undo"))
        compose.onAllNodesWithText("Undo").onFirst().performClick()
        compose.waitUntil(20_000) {
            compose.onAllNodesWithText("Restored").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
