package dev.bgcheck.app.network

import dev.bgcheck.network.data.history.NetworkChangeHistoryStore
import dev.bgcheck.network.data.history.NetworkChangeUndoService
import dev.bgcheck.network.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class NetworkChangeHistoryUndoTest {
    private val record = NetworkChangeRecord(id = "one", packageName = "dev.bgcheck.fixture", label = "Probe",
        userId = 0, uid = 10123, userSerial = 0, firstInstallTime = 10, profileName = "Personal",
        timestamp = 20, kind = NetworkChangeKind.PACKAGE_ENABLED,
        before = NetworkChangeValue(packageEnabled = true, packageEnabledSetting = 0), after = NetworkChangeValue(packageEnabled = false, packageEnabledSetting = 3))

    @Test fun failedRestoreNeverMarksHistoryUndone() = runBlocking {
        var saved: String? = null
        val store = NetworkChangeHistoryStore({ saved }, { saved = it; true })
        store.append(listOf(record))
        val service = NetworkChangeUndoService(store,
            restoreFirewall = { error("Unexpected firewall action") },
            restorePackage = { error("Backend rejected enable") })
        assertTrue(service.undo(record.id).isFailure)
        assertFalse(store.records.value.single().undone)
        assertFalse(NetworkChangeHistoryStore({ saved }, { true }).records.value.single().undone)
    }

    @Test fun successfulVerifiedRestorePersistsUndoneAndRejectsASecondUndo() = runBlocking {
        var saved: String? = null
        var enabled = false
        val store = NetworkChangeHistoryStore({ saved }, { saved = it; true })
        store.append(listOf(record))
        val service = NetworkChangeUndoService(store,
            restoreFirewall = { error("Unexpected firewall action") },
            restorePackage = {
                NetworkChangeGuard.requireUndo(it, NetworkInstallationIdentity(10123, 0, 10), true,
                    NetworkChangeValue(packageEnabled = enabled, packageEnabledSetting = if (enabled) 0 else 3))
                enabled = it.before.packageEnabled!!
                "Package enabled state restored"
            })
        assertTrue(service.undo(record.id).isSuccess)
        assertTrue(enabled)
        assertTrue(NetworkChangeHistoryStore({ saved }, { true }).records.value.single().undone)
        assertTrue(service.undo(record.id).isFailure)
    }

    @Test fun failedPersistenceDoesNotPublishUnpersistedRecords() {
        val store = NetworkChangeHistoryStore({ null }, { false })
        try { store.append(listOf(record)); fail("Failed durable save was accepted") }
        catch (_: IllegalStateException) { }
        assertTrue(store.records.value.isEmpty())
    }

    @Test fun undoStatusWriteFailureReportsThatRestorationAlreadyHappened() = runBlocking {
        var writes = 0
        val store = NetworkChangeHistoryStore({ null }, { ++writes < 3 })
        store.append(listOf(record))
        var enabled = false
        val service = NetworkChangeUndoService(store,
            restoreFirewall = { error("Unexpected firewall action") },
            restorePackage = { enabled = true; "Package enabled state restored and verified." })
        val result = service.undo(record.id)
        assertTrue(result.isFailure)
        assertTrue(enabled)
        assertFalse(store.records.value.single().undone)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("restored", ignoreCase = true))
    }

    @Test fun undoWritesRecoveryCheckpointBeforeEffects() = runBlocking {
        var disk: String? = null
        val store = NetworkChangeHistoryStore({ disk }, { disk = it; true })
        store.append(listOf(record))
        val service = NetworkChangeUndoService(store, restoreFirewall = { error("Unexpected") }, restorePackage = {
            assertTrue(NetworkChangeHistoryStore({ disk }, { true }).records.value.single().recovery)
            error("Verification lost after effect")
        })
        assertTrue(service.undo(record.id).isFailure)
        assertTrue(NetworkChangeHistoryStore({ disk }, { true }).records.value.single().recovery)
        assertFalse(store.records.value.single().canUndo)
    }

    @Test fun safePreflightRejectionRestoresOriginalUndoState() = runBlocking {
        var disk: String? = null
        val store = NetworkChangeHistoryStore({ disk }, { disk = it; true })
        store.append(listOf(record))
        val service = NetworkChangeUndoService(store, restoreFirewall = { error("Unexpected") }, restorePackage = {
            throw dev.bgcheck.network.data.history.NetworkUndoPreflightException(IllegalArgumentException("Current setting changed"))
        })
        assertTrue(service.undo(record.id).isFailure)
        assertEquals(record, NetworkChangeHistoryStore({ disk }, { true }).records.value.single())
    }

}
