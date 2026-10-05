package dev.bgcheck.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationLayoutTest {
    @Test fun labelsThatFitIncludingPaddingUseThreeThenTwoItems() {
        val widths = listOf(96, 45, 89, 93, 72)
        val columns = largeFontNavigationColumns(widths, availableWidth = 336, horizontalPadding = 8)
        assertEquals(3, columns)
        assertEquals(listOf(3, 2), widths.chunked(columns).map { it.size })
    }

    @Test fun anyLabelTooWideForAThirdKeepsTwoItemsPerRow() {
        val widths = listOf(96, 45, 89, 93, 97)
        val columns = largeFontNavigationColumns(widths, availableWidth = 336, horizontalPadding = 8)
        assertEquals(2, columns)
        assertEquals(listOf(2, 2, 1), widths.chunked(columns).map { it.size })
    }

    @Test fun partialPixelsDoNotMakeAThirdWideEnough() {
        assertEquals(2, largeFontNavigationColumns(listOf(96, 45, 89, 93, 72), availableWidth = 335, horizontalPadding = 8))
    }
}
