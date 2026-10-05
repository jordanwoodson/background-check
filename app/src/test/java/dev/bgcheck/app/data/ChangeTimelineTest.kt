package dev.bgcheck.app.data

import dev.bgcheck.network.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class ChangeTimelineTest {
    private val before = PolicyState("com.example.app", "allow", null, false)
    private val policy = ChangeRecord("p", "com.example.app", "Example", "Background restricted", 100,
        before, before.copy(backgroundMode = "ignore"))
    private val network = NetworkChangeRecord("n", "com.example.app", "Example work", 10, 1010001, 42, 10,
        "Work", 200, NetworkChangeKind.PACKAGE_ENABLED, NetworkChangeValue(packageEnabled = true), NetworkChangeValue(packageEnabled = false))

    @Test fun mergesAndSortsBeforePreviewing() {
        val entries = ChangeTimeline.entries(listOf(policy), listOf(network), -1, "", HistorySort.TIME, SortDirection.DESCENDING)
        assertEquals(listOf("network-n", "p"), entries.map { it.id })
    }
    @Test fun searchMatchesBeforeAfterAndKeepsProfilesSeparate() {
        assertEquals("network-n", ChangeTimeline.entries(listOf(policy), listOf(network), 10, "disabled", HistorySort.TIME, SortDirection.ASCENDING).single().id)
        assertTrue(ChangeTimeline.entries(listOf(policy), listOf(network), 0, "disabled", HistorySort.TIME, SortDirection.ASCENDING).isEmpty())
        assertEquals("p", ChangeTimeline.entries(listOf(policy), listOf(network), 0, "ALLOW", HistorySort.TIME, SortDirection.ASCENDING).single().id)
    }
}
