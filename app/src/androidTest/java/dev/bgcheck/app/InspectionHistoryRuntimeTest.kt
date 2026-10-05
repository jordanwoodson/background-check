package dev.bgcheck.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.bgcheck.network.De1984Dependencies
import dev.bgcheck.network.data.history.NetworkChangeIdentityReader
import dev.bgcheck.network.domain.model.FirewallRule
import dev.bgcheck.network.domain.model.NetworkChangeKind
import dev.bgcheck.network.domain.model.PackageIdentity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Uses the disposable fixture only; preserves its previous saved policy and enabled override. */
@RunWith(AndroidJUnit4::class)
class InspectionHistoryRuntimeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dependencies get() = De1984Dependencies.getInstance(context)
    private val fixture = "dev.bgcheck.fixture"
    private val userId get() = android.os.Process.myUid() / 100000

    @Test fun savedRuleUndoRestoresVerifiedSnapshotAndRefusesConflicts(): Unit = runBlocking {
        val identity = NetworkChangeIdentityReader(context).read(fixture, userId)
        requireNotNull(identity.identity.firstInstallTime)
        val repo = dependencies.firewallRepository
        val original = repo.getRuleByPackageSync(fixture, userId)
        val allowed = FirewallRule(fixture, userId, identity.identity.uid, "Fixture history probe",
            hasInternetPermission = true, userSerial = identity.identity.userSerial)
        try {
            repo.insertRule(allowed)
            repo.insertRule(allowed.copy(wifiBlocked = true))
            val change = dependencies.networkChangeHistory.records.value.first {
                it.packageName == fixture && it.kind == NetworkChangeKind.FIREWALL_RULE && !it.undone
            }
            assertTrue(change.canUndo)
            assertTrue(dependencies.networkChangeUndo.undo(change.id).getOrThrow().contains("Saved firewall rule restored"))
            assertFalse(repo.getRuleByPackageSync(fixture, userId)!!.wifiBlocked)
            assertTrue(dependencies.networkChangeHistory.records.value.single { it.id == change.id }.undone)
            repo.insertRule(allowed.copy(wifiBlocked = true))
            val conflict = dependencies.networkChangeHistory.records.value.first { it.packageName == fixture }
            repo.insertRule(allowed.copy(wifiBlocked = true, mobileBlocked = true))
            assertTrue(dependencies.networkChangeUndo.undo(conflict.id).isFailure)
            assertTrue(repo.getRuleByPackageSync(fixture, userId)!!.mobileBlocked)
        } finally {
            if (original == null) repo.deleteRule(fixture, userId) else repo.insertRule(original)
        }
    }

    @Test fun packageUndoRestoresExactAndroidEnabledOverride(): Unit = runBlocking {
        dependencies.shizukuManager.checkShizukuStatus()
        val originalSetting = context.packageManager.getApplicationEnabledSetting(fixture)
        val original = NetworkChangeIdentityReader(context).read(fixture, userId)
        val originalEnabled = requireNotNull(original.enabled)
        assertEquals(originalSetting, requireNotNull(original.enabledSetting))
        val identity = PackageIdentity(original.identity.uid, original.identity.userSerial, original.identity.firstInstallTime)
        var primaryFailure: Throwable? = null
        try {
            dependencies.packageRepository.setPackageEnabled(fixture, userId, !originalEnabled, identity).getOrThrow()
            val change = dependencies.networkChangeHistory.records.value.first {
                it.packageName == fixture && it.kind == NetworkChangeKind.PACKAGE_ENABLED && !it.undone
            }
            assertTrue(change.canUndo)
            dependencies.networkChangeUndo.undo(change.id).getOrThrow()
            assertEquals(originalSetting, context.packageManager.getApplicationEnabledSetting(fixture))
            assertEquals(originalEnabled, context.packageManager.getApplicationInfo(fixture, 0).enabled)
        } catch (failure: Throwable) {
            primaryFailure = failure
            throw failure
        } finally {
            // Cleanup must also handle a changed package whose journal is left in recovery.
            // Restore independently of the Undo implementation being tested, with the same identity guard.
            try {
                check(dependencies.packageDataSource.setPackageEnabledSetting(fixture, userId, originalSetting, identity))
                assertEquals(originalSetting, context.packageManager.getApplicationEnabledSetting(fixture))
                assertEquals(originalEnabled, context.packageManager.getApplicationInfo(fixture, 0).enabled)
            } catch (cleanupFailure: Throwable) {
                primaryFailure?.addSuppressed(cleanupFailure) ?: throw cleanupFailure
            }
        }
    }
}
