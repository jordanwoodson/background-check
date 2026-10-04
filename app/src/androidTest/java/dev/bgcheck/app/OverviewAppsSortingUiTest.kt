package dev.bgcheck.app

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.bgcheck.app.data.*
import dev.bgcheck.app.ui.BackgroundCheckApp
import dev.bgcheck.app.ui.BackgroundCheckTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OverviewAppsSortingUiTest {
    @get:Rule val compose = createComposeRule()

    private fun show(apps: List<AppEntry>, changes: List<ChangeRecord> = emptyList(), onUndo: (ChangeRecord) -> Unit = {}) {
        compose.setContent {
            BackgroundCheckTheme {
                BackgroundCheckApp(
                    state = UiState(access = AccessState.READY, loading = false, apps = apps, changes = changes),
                    onRefresh = {}, onConnect = {}, onSelect = {}, onDismissDetail = {}, onAction = {}, onUndo = onUndo,
                    onOpenAppSettings = {}, onOpenShizuku = {}, onOpenWirelessSettings = {}, onClearMessage = {},
                    onRefreshResources = {}, onRefreshEnergy = {}, onSetMonitorPaused = {}, onSetMonitorInterval = {},
                    onMonitorVisible = {}, onForceStop = {},
                )
            }
        }
    }

    private fun app(name: String, uid: Int, system: Boolean = false, state: ProcessState = ProcessState.FOREGROUND) =
        AppEntry("dev.sort.${name.lowercase().replace(' ', '_')}", name, uid, system, processState = state)

    private fun sort(list: String, id: String, key: String) {
        if (id == "apps") compose.openAppFilters()
        compose.onNodeWithTag(list).performScrollToNode(hasTestTag("$id-sort"))
        compose.onNodeWithTag("$id-sort").performScrollTo().performClick()
        compose.onNodeWithText(key, useUnmergedTree = false).performClick()
    }

    private fun reverse(list: String, id: String) {
        compose.onNodeWithTag(list).performScrollToNode(hasTestTag("$id-direction"))
        compose.onNodeWithTag("$id-direction").performScrollTo().performClick()
    }

    private fun assertOrder(list: String, vararg labels: String) {
        compose.onNodeWithTag(list).performScrollToNode(hasText(labels.last()))
        compose.onNodeWithText(labels.last()).performScrollTo()
        // Keep the last label near the viewport bottom so preceding rows remain
        // visible even on compact phones; scrolling to a child may align it at top.
        val viewportBottom = compose.onNodeWithTag(list).fetchSemanticsNode().boundsInRoot.bottom
        val lastBottom = compose.onNodeWithText(labels.last()).fetchSemanticsNode().boundsInRoot.bottom
        compose.onNodeWithTag(list).performSemanticsAction(SemanticsActions.ScrollBy) { scroll ->
            scroll(0f, lastBottom - viewportBottom + 16f)
        }
        val positions = labels.map { label -> compose.onNodeWithText(label).assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top }
        assertTrue("Expected rendered order ${labels.toList()}, got vertical positions $positions", positions.zipWithNext().all { (a, b) -> a < b })
    }

    @Test fun appsSortUidNumericallyInBothDirections() {
        show(listOf(app("Hundred", 100), app("Ten", 10), app("Two", 2)))
        compose.onNodeWithText("Apps").performClick()
        sort("apps-list", "apps", "UID")
        assertOrder("apps-list", "Two", "Ten", "Hundred")
        reverse("apps-list", "apps")
        assertOrder("apps-list", "Hundred", "Ten", "Two")
    }

    @Test fun appsRetainSearchFiltersAndSortAcrossPageChanges() {
        show(listOf(
            app("Needle Ten", 10, system = true), app("Needle Two", 2, system = true),
            app("Needle User", 20), app("Needle Cached", 30, system = true, state = ProcessState.CACHED),
            app("Unrelated", 40, system = true),
        ))
        compose.onNodeWithText("Apps").performClick()
        compose.onNodeWithTag("apps-list").performScrollToNode(hasSetTextAction())
        compose.onNode(hasSetTextAction()).performTextInput("Needle")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.openAppFilters()
        compose.onNodeWithText("System", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Active", substring = false).performScrollTo().performClick()
        sort("apps-list", "apps", "UID")
        reverse("apps-list", "apps")
        assertOrder("apps-list", "Needle Ten", "Needle Two")
        compose.onNodeWithText("Overview").performClick()
        compose.onNodeWithText("Apps").performClick()
        compose.onNodeWithTag("apps-list").performScrollToNode(hasSetTextAction())
        compose.onNode(hasSetTextAction()).assertTextContains("Needle")
        compose.onNodeWithText("System", substring = false).performScrollTo().assertIsSelected()
        compose.onNodeWithText("Active", substring = false).performScrollTo().assertIsSelected()
        compose.openAppFilters()
        compose.onNodeWithTag("apps-list").performScrollToNode(hasTestTag("apps-sort"))
        compose.onNodeWithTag("apps-sort").assertTextContains("UID", substring = true)
        compose.onNodeWithTag("apps-direction").assertTextContains("Descending", substring = true)
        assertOrder("apps-list", "Needle Ten", "Needle Two")
        compose.onNodeWithText("Needle User").assertDoesNotExist()
        compose.onNodeWithText("Needle Cached").assertDoesNotExist()
        compose.onNodeWithText("Unrelated").assertDoesNotExist()
    }

    @Test fun activePreviewSortsWholeCollectionBeforeLimitAndKeepsIndependentChoice() {
        show(listOf(app("Alpha", 2), app("Bravo", 10), app("Charlie", 100), app("Delta", 200), app("Echo", 300), app("Foxtrot", 400)))
        assertOrder("overview-list", "Alpha", "Bravo")
        sort("overview-list", "active-apps", "UID")
        reverse("overview-list", "active-apps")
        assertOrder("overview-list", "Foxtrot", "Echo")
        compose.onNodeWithText("Apps").performClick()
        compose.openAppFilters()
        compose.onNodeWithTag("apps-list").performScrollToNode(hasTestTag("apps-sort"))
        compose.onNodeWithTag("apps-sort").assertTextContains("Name", substring = true)
        compose.onNodeWithTag("apps-direction").assertTextContains("Ascending", substring = true)
        compose.onNodeWithText("Overview").performClick()
        compose.onNodeWithTag("overview-list").performScrollToNode(hasTestTag("active-apps-sort"))
        compose.onNodeWithTag("active-apps-sort").assertTextContains("UID", substring = true)
        compose.onNodeWithTag("active-apps-direction").assertTextContains("Descending", substring = true)
        assertOrder("overview-list", "Foxtrot", "Echo")
    }

    @Test fun historySortsBeforePreviewLimitAndUndoTargetsRenderedRecord() {
        val policy = PolicyState("dev.sort.fixture", "allow", null, false, userExempt = false)
        val changes = listOf("Alpha", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot").mapIndexed { index, label ->
            ChangeRecord(label, policy.packageName, "Change $label", "Action $label", (6 - index) * 1000L, policy, policy)
        }
        var undone: ChangeRecord? = null
        show(emptyList(), changes) { undone = it }
        sort("overview-list", "history", "App name")
        reverse("overview-list", "history")
        assertOrder("overview-list", "Change Foxtrot", "Change Echo")
        compose.onNode(hasText("Undo") and hasAnyAncestor(hasTestTag("history-record-Foxtrot"))).performClick()
        compose.runOnIdle { assertEquals("Foxtrot", undone?.id) }
        compose.onNodeWithTag("overview-list").performScrollToNode(hasText("Show all 6 changes"))
        compose.onNodeWithText("Show all 6 changes").performClick()
        compose.onNodeWithTag("overview-list").performScrollToNode(hasText("Change Alpha"))
        compose.onNodeWithText("Change Alpha").assertIsDisplayed()
        compose.onNodeWithTag("overview-list").performScrollToNode(hasText("Show fewer changes"))
        compose.onNodeWithText("Show fewer changes").performClick()
        assertOrder("overview-list", "Change Foxtrot", "Change Echo")
    }
}
