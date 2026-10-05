package dev.bgcheck.app

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
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

/** Chips in a horizontally scrolling row also need their containing list scrolled vertically. */
internal fun ComposeContentTestRule.scrollListToNode(listTag: String, matcher: SemanticsMatcher) {
    val list = onNodeWithTag(listTag)
    list.performScrollToNode(matcher)
    val viewport = list.fetchSemanticsNode().boundsInRoot
    val target = onNode(matcher).fetchSemanticsNode()
    val top = target.positionInRoot.y
    val bottom = top + target.size.height
    val distance = when {
        top < viewport.top -> top - viewport.top
        bottom > viewport.bottom -> bottom - viewport.bottom
        else -> 0f
    }
    if (distance != 0f) list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, distance) }
}
