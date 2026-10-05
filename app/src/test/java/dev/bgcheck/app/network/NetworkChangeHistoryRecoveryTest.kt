package dev.bgcheck.app.network

import dev.bgcheck.network.data.history.NetworkChangeHistoryStore
import dev.bgcheck.network.data.history.NetworkChangeTransaction
import dev.bgcheck.network.data.history.NetworkChangeUnverifiedException
import dev.bgcheck.network.domain.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class NetworkChangeHistoryRecoveryTest {
    private val before = NetworkChangeValue(packageEnabled = true, packageEnabledSetting = 0)
    private val after = NetworkChangeValue(packageEnabled = false, packageEnabledSetting = 3)
    private val change = NetworkChangeRecord("change", "dev.bgcheck.fixture", "Probe", 0, 10123, 0, 10,
        "Personal", 20, NetworkChangeKind.PACKAGE_ENABLED, before, after)

    @Test fun checkpointIsDurableBeforeAnyEffectAndSurvivesAbruptTermination() = runBlocking {
        var disk: String? = null
        val store = NetworkChangeHistoryStore({ disk }, { disk = it; true })
        val transaction = NetworkChangeTransaction(store)
        class AbruptTermination : Error()
        try {
            transaction.execute(listOf(change.copy(after = before)), operation = {
                val reopened = NetworkChangeHistoryStore({ disk }, { true }).records.value.single()
                assertTrue(reopened.recovery)
                assertFalse(reopened.canUndo)
                throw AbruptTermination()
            }, verify = { listOf(change) })
            fail("Abrupt termination was swallowed")
        } catch (_: AbruptTermination) { }
        assertTrue(NetworkChangeHistoryStore({ disk }, { true }).records.value.single().recovery)
    }

    @Test fun verificationLossAfterEffectKeepsRecoverableHistoryAndReportsFailure() = runBlocking {
        var disk: String? = null
        var disabled = false
        val store = NetworkChangeHistoryStore({ disk }, { disk = it; true })
        try {
            NetworkChangeTransaction(store).execute(listOf(change), operation = { disabled = true },
                verify = { error("Profile access lost") })
            fail("Unverified effect reported success")
        } catch (_: IllegalStateException) { }
        assertTrue(disabled)
        val recovered = NetworkChangeHistoryStore({ disk }, { true }).records.value.single()
        assertTrue(recovered.recovery)
        assertFalse(recovered.canUndo)
        assertTrue(recovered.recoveryReason.orEmpty().contains("Profile access lost"))
    }

    @Test fun cancellationAfterEffectStillFinalizesVisibleRecovery() = runBlocking {
        var disk: String? = null
        val store = NetworkChangeHistoryStore({ disk }, { disk = it; true })
        try {
            NetworkChangeTransaction(store).execute(listOf(change), operation = { throw CancellationException("interrupted") },
                verify = { completed ->
                    assertFalse(completed)
                    listOf(change.copy(recovery = true, recoveryReason = "Interrupted"))
                })
            fail("Cancellation was swallowed")
        } catch (_: CancellationException) { }
        assertTrue(NetworkChangeHistoryStore({ disk }, { true }).records.value.single().recovery)
    }

    @Test fun readablePackageMismatchReportsFailureAfterSavingObservedRecovery() = runBlocking {
        var disk: String? = null
        var commandRan = false
        val store = NetworkChangeHistoryStore({ disk }, { disk = it; true })
        val result = runCatching {
            NetworkChangeTransaction(store).execute(listOf(change), operation = { commandRan = true },
                verify = { completed ->
                    assertTrue(completed)
                    listOf(change.copy(after = before, recovery = true,
                        recoveryReason = "Android's current setting did not match the requested setting."))
                })
        }
        assertTrue(commandRan)
        assertTrue("A command with a mismatched observed result reported success", result.isFailure)
        assertTrue(result.exceptionOrNull() is NetworkChangeUnverifiedException)
        val saved = NetworkChangeHistoryStore({ disk }, { true }).records.value.single()
        assertTrue(saved.recovery)
        assertFalse(saved.canUndo)
        assertEquals(0, saved.after.packageEnabledSetting)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("verif", ignoreCase = true))
    }

    @Test fun unavailablePackagePostStateReportsFailureAfterSavingRecovery() = runBlocking {
        var disk: String? = null
        var commandRan = false
        val store = NetworkChangeHistoryStore({ disk }, { disk = it; true })
        val result = runCatching {
            NetworkChangeTransaction(store).execute(listOf(change), operation = { commandRan = true },
                verify = { completed ->
                    assertTrue(completed)
                    listOf(change.copy(after = NetworkChangeValue(packageState = true), recovery = true,
                        recoveryReason = "The current package setting is unavailable."))
                })
        }
        assertTrue("Missing metadata must not prevent the ordinary command", commandRan)
        assertTrue("An unavailable observed result reported success", result.isFailure)
        assertTrue(result.exceptionOrNull() is NetworkChangeUnverifiedException)
        val saved = NetworkChangeHistoryStore({ disk }, { true }).records.value.single()
        assertTrue(saved.recovery)
        assertFalse(saved.canUndo)
        assertNull(saved.after.packageEnabled)
        assertNull(saved.after.packageEnabledSetting)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("verif", ignoreCase = true))
    }

    @Test fun verifiedOperationReplacesCheckpointWithUndoableRecord() = runBlocking {
        var disk: String? = null
        val store = NetworkChangeHistoryStore({ disk }, { disk = it; true })
        NetworkChangeTransaction(store).execute(listOf(change), operation = { Unit }, verify = { listOf(change) })
        val saved = NetworkChangeHistoryStore({ disk }, { true }).records.value.single()
        assertFalse(saved.recovery)
        assertTrue(saved.canUndo)
    }

    @Test fun historyTrimmingNeverDropsPendingRecovery() {
        var disk: String? = null
        val store = NetworkChangeHistoryStore({ disk }, { disk = it; true })
        store.append(listOf(change.copy(id = "pending", recovery = true)))
        store.append((1..550).map { change.copy(id = "done$it") })
        val reopened = NetworkChangeHistoryStore({ disk }, { true }).records.value
        assertTrue(reopened.any { it.id == "pending" && it.recovery })
        assertEquals(500, reopened.count { !it.recovery })
    }

    @Test fun identicalUidAfterReinstallDoesNotProveOperationIdentity() {
        val original = NetworkInstallationIdentity(10123, 0, 10)
        assertFalse(NetworkChangeGuard.sameInstallation(original, original.copy(firstInstallTime = 11)))
        assertFalse(NetworkChangeGuard.sameInstallation(original, null))
        assertTrue(NetworkChangeGuard.sameInstallation(original, original))
    }

    @Test fun effectiveEnabledStateCannotHideChangedRawMode() {
        val enabled = change.copy(before = after, after = before)
        try {
            NetworkChangeGuard.requireUndo(enabled, enabled.identity, true,
                before.copy(packageEnabledSetting = 1))
            fail("DEFAULT and ENABLED were treated as identical")
        } catch (_: IllegalArgumentException) { }
        assertFalse(change.copy(before = before.copy(packageEnabledSetting = null)).canUndo)
        assertEquals("default-state", PackageEnabledSetting.command(0))
        assertEquals("enable", PackageEnabledSetting.command(1))
        assertEquals("disable", PackageEnabledSetting.command(2))
        assertEquals("disable-user", PackageEnabledSetting.command(3))
        assertEquals("disable-until-used", PackageEnabledSetting.command(4))
        assertTrue(before.summary().contains("default", ignoreCase = true))
    }
}
