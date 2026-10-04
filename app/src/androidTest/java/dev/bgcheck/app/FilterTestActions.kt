package dev.bgcheck.app

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeContentTestRule

/** Follow the same disclosure path a person uses to change app inventory filters. */
internal fun ComposeContentTestRule.openAppFilters() {
    onNodeWithTag("apps-list").performScrollToNode(hasText("Filters"))
    val disclosure = onNodeWithText("Filters")
    if (disclosure.fetchSemanticsNode().config[SemanticsProperties.StateDescription] == "Collapsed") {
        disclosure.performClick()
    }
}
