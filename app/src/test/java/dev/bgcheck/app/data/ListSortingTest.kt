package dev.bgcheck.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ListSortingTest {
    private val asc = SortDirection.ASCENDING
    private val desc = SortDirection.DESCENDING

    private val processes = listOf(
        ProcessResource(30, 1, 20, "user", "bravo", "bravo", "D", 12.0, 600, 4000),
        ProcessResource(4, 1, 40, "user", "delta", "delta", "Z", null, null, null),
        ProcessResource(10, 1, 30, "user", "charlie", "charlie", "S", 0.0, 900, 2000),
        ProcessResource(2, 1, 10, "user", "Alpha", "Alpha", "R", 40.0, 300, 6000),
    )

    @Test fun processKeysOrderActualMetricsInBothDirections() {
        val expected = mapOf(
            ProcessSort.CPU to (listOf(10, 30, 2, 4) to listOf(2, 30, 10, 4)),
            ProcessSort.MEMORY to (listOf(2, 30, 10, 4) to listOf(10, 30, 2, 4)),
            ProcessSort.VIRTUAL to (listOf(10, 30, 2, 4) to listOf(2, 30, 10, 4)),
            ProcessSort.NAME to (listOf(2, 30, 10, 4) to listOf(4, 10, 30, 2)),
            ProcessSort.PID to (listOf(2, 4, 10, 30) to listOf(30, 10, 4, 2)),
            ProcessSort.UID to (listOf(2, 30, 10, 4) to listOf(4, 10, 30, 2)),
            ProcessSort.STATE to (listOf(30, 2, 10, 4) to listOf(4, 10, 2, 30)),
        )
        assertEquals(ProcessSort.entries.toSet(), expected.keys)
        expected.forEach { (key, orders) ->
            assertEquals("$key ascending", orders.first, ListSorting.processes(processes, key, asc).map { it.pid })
            assertEquals("$key descending", orders.second, ListSorting.processes(processes, key, desc).map { it.pid })
        }
    }

    @Test fun processTiesDoNotJumpWhenInputOrDirectionChanges() {
        val tied = processes.map { it.copy(cpuPercent = 0.0) }
        for (direction in SortDirection.entries) {
            assertEquals(listOf(2, 4, 10, 30), ListSorting.processes(tied, ProcessSort.CPU, direction).map { it.pid })
            assertEquals(listOf(2, 4, 10, 30), ListSorting.processes(tied.reversed(), ProcessSort.CPU, direction).map { it.pid })
        }
    }

    private val apps = listOf(
        AppEntry("pkg.m", "bravo", 20, false, processState = ProcessState.FOREGROUND, standbyBucket = 40),
        AppEntry("pkg.d", "Delta", 40, false),
        AppEntry("pkg.z", "Alpha", 30, false, processState = ProcessState.SERVICE, standbyBucket = 20),
        AppEntry("pkg.a", "charlie", 10, false, processState = ProcessState.CACHED, standbyBucket = 10),
    )

    @Test fun appKeysHandleNamesNumericIdentityAndUnavailableState() {
        val expected = mapOf(
            AppSort.NAME to (listOf("z", "m", "a", "d") to listOf("d", "a", "m", "z")),
            AppSort.PACKAGE to (listOf("a", "d", "m", "z") to listOf("z", "m", "d", "a")),
            AppSort.UID to (listOf("a", "m", "z", "d") to listOf("d", "z", "m", "a")),
            AppSort.STATE to (listOf("m", "z", "a", "d") to listOf("a", "z", "m", "d")),
            AppSort.STANDBY to (listOf("a", "z", "m", "d") to listOf("m", "z", "a", "d")),
        )
        assertEquals(AppSort.entries.toSet(), expected.keys)
        expected.forEach { (key, orders) ->
            assertEquals("$key ascending", orders.first, ListSorting.apps(apps, key, asc).map { it.packageName.removePrefix("pkg.") })
            assertEquals("$key descending", orders.second, ListSorting.apps(apps, key, desc).map { it.packageName.removePrefix("pkg.") })
        }
    }

    @Test fun appNameTiesIgnoreCaseAndKeepPackageOrder() {
        val tied = apps.map { it.copy(label = if (it.uid % 20 == 0) "SAME" else "same") }
        for (direction in SortDirection.entries) {
            assertEquals(listOf("pkg.a", "pkg.d", "pkg.m", "pkg.z"), ListSorting.apps(tied, AppSort.NAME, direction).map { it.packageName })
        }
    }

    private val energy = listOf(
        UidEnergy(30, 10.0, 300, 60, 600, 0),
        UidEnergy(40),
        UidEnergy(10, 0.0, 900, 30, 900, 20),
        UidEnergy(20, 30.0, 0, 90, 300, 60),
    )
    private val names = mapOf(
        10 to listOf(AppEntry("z", "Zebra", 10, false), AppEntry("a", "Alpha", 10, false)),
        20 to listOf(AppEntry("b", "beta", 20, false)),
        30 to listOf(AppEntry("c", "Charlie", 30, false)),
    )

    @Test fun batteryKeysRetainZeroAndPutUnavailableLastInBothDirections() {
        val expected = mapOf(
            EnergySort.BATTERY to (listOf(10, 30, 20, 40) to listOf(20, 30, 10, 40)),
            EnergySort.CPU to (listOf(20, 30, 10, 40) to listOf(10, 30, 20, 40)),
            EnergySort.WAKELOCK to (listOf(30, 10, 20, 40) to listOf(20, 10, 30, 40)),
            EnergySort.RECEIVED to (listOf(10, 30, 20, 40) to listOf(20, 30, 10, 40)),
            EnergySort.SENT to (listOf(20, 30, 10, 40) to listOf(10, 30, 20, 40)),
            EnergySort.NAME to (listOf(10, 20, 30, 40) to listOf(30, 20, 10, 40)),
            EnergySort.UID to (listOf(10, 20, 30, 40) to listOf(40, 30, 20, 10)),
        )
        assertEquals(EnergySort.entries.toSet(), expected.keys)
        expected.forEach { (key, orders) ->
            assertEquals("$key ascending", orders.first, ListSorting.energy(energy, key, asc, names).map { it.uid })
            assertEquals("$key descending", orders.second, ListSorting.energy(energy, key, desc, names).map { it.uid })
        }
    }

    @Test fun batteryTiesUseUidAndSharedNamesDoNotDependOnInventoryOrder() {
        val tied = energy.map { it.copy(estimatedMah = null) }
        for (direction in SortDirection.entries) {
            assertEquals(listOf(10, 20, 30, 40), ListSorting.energy(tied, EnergySort.BATTERY, direction, names).map { it.uid })
            val first = ListSorting.energy(energy, EnergySort.NAME, direction, names).map { it.uid }
            assertEquals(first, ListSorting.energy(energy.reversed(), EnergySort.NAME, direction, names.mapValues { it.value.reversed() }).map { it.uid })
        }
    }

    @Test fun batteryByteSortUsesLongValuesWithoutOverflowOrRounding() {
        val items = listOf(UidEnergy(1, networkRxBytes = Long.MAX_VALUE), UidEnergy(2, networkRxBytes = 5_000_000_000), UidEnergy(3, networkRxBytes = 0))
        assertEquals(listOf(3, 2, 1), ListSorting.energy(items, EnergySort.RECEIVED, asc, emptyMap()).map { it.uid })
        assertEquals(listOf(1, 2, 3), ListSorting.energy(items, EnergySort.RECEIVED, desc, emptyMap()).map { it.uid })
    }

    @Test fun componentsUseDisplayedNamesAndBothDirections() {
        val items = listOf(PowerComponent("cell", 1.0), PowerComponent("cpu", 3.0), PowerComponent("blue", 2.0))
        assertEquals(listOf("blue", "cpu", "cell"), ListSorting.components(items, ComponentSort.NAME, asc).map { it.name })
        assertEquals(listOf("cell", "cpu", "blue"), ListSorting.components(items, ComponentSort.NAME, desc).map { it.name })
        assertEquals(listOf("cell", "blue", "cpu"), ListSorting.components(items, ComponentSort.BATTERY, asc).map { it.name })
        assertEquals(listOf("cpu", "blue", "cell"), ListSorting.components(items, ComponentSort.BATTERY, desc).map { it.name })
    }

    @Test fun serviceKeysIncludeUnknownProcessLastAndForegroundGrouping() {
        val items = listOf(
            RunningService("z/.Beta", "zeta", true), RunningService("a/.Zed", "Beta", false),
            RunningService("m/.Alpha", "alpha", true), RunningService("d/.Delta", null, false),
        )
        val expected = mapOf(
            ServiceSort.NAME to (listOf("a", "d", "m", "z") to listOf("z", "m", "d", "a")),
            ServiceSort.PROCESS to (listOf("m", "a", "z", "d") to listOf("z", "a", "m", "d")),
            ServiceSort.FOREGROUND to (listOf("a", "d", "m", "z") to listOf("m", "z", "a", "d")),
        )
        assertEquals(ServiceSort.entries.toSet(), expected.keys)
        expected.forEach { (key, orders) ->
            assertEquals("$key ascending", orders.first, ListSorting.services(items, key, asc).map { it.component.substringBefore('/') })
            assertEquals("$key descending", orders.second, ListSorting.services(items, key, desc).map { it.component.substringBefore('/') })
        }
    }

    @Test fun historyKeysSortBeforePreviewAndKeepEachRecordAction() {
        val items = listOf(record("3", "m", "bravo", "Z action", 20, recovery = true),
            record("1", "z", "Alpha", "B action", 30), record("2", "a", "charlie", "a action", 10, undone = true))
        val expected = mapOf(
            HistorySort.TIME to (listOf("2", "3", "1") to listOf("1", "3", "2")),
            HistorySort.APP to (listOf("1", "3", "2") to listOf("2", "3", "1")),
            HistorySort.PACKAGE to (listOf("2", "3", "1") to listOf("1", "3", "2")),
            HistorySort.ACTION to (listOf("2", "1", "3") to listOf("3", "1", "2")),
            HistorySort.STATUS to (listOf("1", "3", "2") to listOf("2", "3", "1")),
        )
        assertEquals(HistorySort.entries.toSet(), expected.keys)
        expected.forEach { (key, orders) ->
            val ascending = ListSorting.history(items, key, asc)
            assertEquals("$key ascending", orders.first, ascending.map { it.id })
            assertEquals("$key descending", orders.second, ListSorting.history(items, key, desc).map { it.id })
            assertEquals(items.toSet(), ascending.toSet())
        }
    }

    @Test fun historyTiesUseNewestFirstThenStableRecordId() {
        val items = listOf(record("z", "p", "Same", "Action", 10), record("b", "p", "same", "Action", 20), record("a", "p", "SAME", "Action", 20))
        for (direction in SortDirection.entries) {
            assertEquals(listOf("a", "b", "z"), ListSorting.history(items, HistorySort.APP, direction).map { it.id })
        }
    }

    @Test fun emptyListsRemainEmptyForEveryKeyAndDirection() {
        for (direction in SortDirection.entries) {
            AppSort.entries.forEach { assertEquals(emptyList<AppEntry>(), ListSorting.apps(emptyList(), it, direction)) }
            HistorySort.entries.forEach { assertEquals(emptyList<ChangeRecord>(), ListSorting.history(emptyList(), it, direction)) }
            ProcessSort.entries.forEach { assertEquals(emptyList<ProcessResource>(), ListSorting.processes(emptyList(), it, direction)) }
            EnergySort.entries.forEach { assertEquals(emptyList<UidEnergy>(), ListSorting.energy(emptyList(), it, direction, emptyMap())) }
            ComponentSort.entries.forEach { assertEquals(emptyList<PowerComponent>(), ListSorting.components(emptyList(), it, direction)) }
            ServiceSort.entries.forEach { assertEquals(emptyList<RunningService>(), ListSorting.services(emptyList(), it, direction)) }
        }
    }

    private fun record(id: String, pkg: String, label: String, action: String, time: Long, undone: Boolean = false, recovery: Boolean = false): ChangeRecord {
        val policy = PolicyState(pkg, "allow", "allow", false)
        return ChangeRecord(id, pkg, label, action, time, policy, policy, undone, recovery)
    }
}
