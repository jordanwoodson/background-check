package dev.bgcheck.app

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bgcheck.network.De1984Application
import dev.bgcheck.network.data.firewall.ConnectivityManagerFirewallBackend
import dev.bgcheck.network.data.firewall.ConnectivityUidFirewall
import dev.bgcheck.network.data.firewall.IptablesFirewallBackend
import dev.bgcheck.network.domain.model.PackageIdentity
import dev.bgcheck.network.data.multiuser.HiddenApiHelper
import dev.bgcheck.network.domain.model.FirewallRule
import dev.bgcheck.network.domain.model.NetworkType
import dev.bgcheck.network.utils.Constants
import dev.bgcheck.network.utils.openAppSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Disposable managed-profile emulator, fixture in users 0/10, HTTP server on host port 8765. */
@RunWith(AndroidJUnit4::class)
class NetworkWorkProfileRuntimeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val deps get() = (context as De1984Application).dependencies
    private val pkg = "dev.bgcheck.fixture"
    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    ).bufferedReader().use { it.readText() }

    private suspend fun prepare() {
        check(android.os.Build.MODEL.contains("sdk")) { "Disposable emulator only" }
        deps.shizukuManager.checkShizukuStatus()
        assertTrue("Authorize Shizuku in the app", deps.shizukuManager.hasShizukuPermission)
        HiddenApiHelper.awaitCrossUserPermission(context)
        HiddenApiHelper.clearUserCache()
        assertTrue(HiddenApiHelper.currentProfile(context, 10)?.available == true)
    }

    private fun info(user: Int, flags: Int = 0) = requireNotNull(
        HiddenApiHelper.getApplicationInfoAsUser(context, pkg, flags, user)
    )

    private suspend fun probe(user: Int): String {
        val token = System.nanoTime().toString()
        shell("am start -S --user $user -n $pkg/.NetworkProbeActivity --es token $token")
        repeat(40) {
            val value = shell("run-as $pkg --user $user cat files/probe-$token").trim()
            if (value in setOf("ALLOWED", "BLOCKED", "INVALID_RESPONSE")) return value
            delay(100)
        }
        error("No probe result for user $user")
    }

    private fun backend() = ConnectivityManagerFirewallBackend(context, deps.shizukuManager, deps.errorHandler)

    @Test fun connectivityManagerSeparatesTrafficAndRestoresAfterNewBackendInstance() = runBlocking<Unit> {
        prepare()
        deps.firewallManager.stopFirewall().getOrThrow()
        val personal = info(0)
        val work = info(10)
        val prefs = context.getSharedPreferences(Constants.Settings.PREFS_NAME, Context.MODE_PRIVATE)
        val oldPolicy = prefs.getString(Constants.Settings.KEY_DEFAULT_FIREWALL_POLICY, null)
        val oldEnabled = prefs.getBoolean(Constants.Settings.KEY_FIREWALL_ENABLED, false)
        prefs.edit().putBoolean(Constants.Settings.KEY_FIREWALL_ENABLED, true).commit()
        prefs.edit().putString(Constants.Settings.KEY_DEFAULT_FIREWALL_POLICY, Constants.Settings.POLICY_ALLOW_ALL).commit()
        val useRoot = InstrumentationRegistry.getArguments().getString("backend") == "IPTABLES"
        val controller = if (useRoot) null else ConnectivityUidFirewall.connect(deps.shizukuManager)
        val originalChain = controller?.isEnabled()
        val originalPersonal = controller?.getRule(personal.uid)
        val originalWork = controller?.getRule(work.uid)
        fun rootBackend() = IptablesFirewallBackend(context, deps.rootManager, deps.shizukuManager, deps.errorHandler)
        val root = if (useRoot) rootBackend() else null
        val cm = if (useRoot) null else backend()
        val active = root ?: requireNotNull(cm)
        try {
            assertEquals("ALLOWED", probe(0))
            assertEquals("ALLOWED", probe(10))
            active.checkAvailability().getOrThrow()
            (root?.startInternal() ?: cm!!.startInternal()).getOrThrow()
            val ownRule = FirewallRule(pkg, 0, personal.uid, "Personal fixture")
            val workRule = FirewallRule(pkg, 10, work.uid, "Work fixture")
            active.applyRules(listOf(ownRule, workRule.blockAll()), NetworkType.WIFI, true).getOrThrow()
            assertEquals("ALLOWED", probe(0))
            assertEquals("BLOCKED", probe(10))
            active.applyRules(listOf(ownRule.blockAll(), workRule), NetworkType.WIFI, true).getOrThrow()
            assertEquals("BLOCKED", probe(0))
            assertEquals("ALLOWED", probe(10))
            (if (useRoot) rootBackend().stopInternal() else backend().stopInternal()).getOrThrow()
            if (controller != null) {
                assertEquals(originalPersonal, controller.getRule(personal.uid))
                assertEquals(originalWork, controller.getRule(work.uid))
                assertEquals(originalChain, controller.isEnabled())
            }
            assertEquals("ALLOWED", probe(0))
            assertEquals("ALLOWED", probe(10))
        } finally {
            root?.stopInternal() ?: cm?.stopInternal()
            if (controller != null) {
                controller.setRule(personal.uid, originalPersonal!!)
                controller.setRule(work.uid, originalWork!!)
                controller.setEnabled(originalChain!!)
            }
            prefs.edit().putString(Constants.Settings.KEY_DEFAULT_FIREWALL_POLICY, oldPolicy)
                .putBoolean(Constants.Settings.KEY_FIREWALL_ENABLED, oldEnabled).commit()
        }
    }

    @Test fun staleNetworkActionsAreRejectedAndDisableTargetsOnlyWork() = runBlocking<Unit> {
        prepare()
        val work = info(10)
        val serial = requireNotNull(HiddenApiHelper.currentProfile(context, 10)?.serialNumber)
        val target = PackageIdentity(work.uid, serial)
        val stale = target.copy(userSerial = serial + 10000)
        val wrongUid = target.copy(uid = info(0).uid)
        val source = deps.packageDataSource
        suspend fun rejects(action: suspend () -> Unit) {
            try { action(); fail("Stale selected installation must be rejected") }
            catch (expected: IllegalArgumentException) { }
            catch (expected: IllegalStateException) { }
        }
        rejects { source.setPackageEnabled(pkg, 10, false, stale) }
        rejects { source.uninstallPackage(pkg, 10, stale) }
        rejects { source.reinstallPackage(pkg, 10, stale) }
        rejects { source.forceStopPackage(pkg, 10, stale) }
        rejects { source.forceStopPackage(pkg, 10, wrongUid) }
        rejects { source.setAllNetworkBlocking(pkg, 10, true, expected = stale) }
        assertTrue(info(0).enabled)
        assertTrue(info(10).enabled)
        try {
            assertTrue(source.setPackageEnabled(pkg, 10, false, target))
            assertTrue("Personal copy stays enabled", info(0).enabled)
            assertFalse("Only work copy is disabled", info(10).enabled)
            assertTrue(source.setPackageEnabled(pkg, 10, true, target))
            assertTrue(info(0).enabled)
            assertTrue(info(10).enabled)
        } finally {
            shell("pm enable --user 10 $pkg")
            HiddenApiHelper.clearInstalledAppsCache()
        }
    }

    @Test fun disabledOemRulesAreNotActivatedAndExistingActiveDenialsSurviveStop() = runBlocking<Unit> {
        prepare()
        deps.firewallManager.stopFirewall().getOrThrow()
        val controller = ConnectivityUidFirewall.connect(deps.shizukuManager)
        val uid = info(0).uid
        val oldRule = controller.getRule(uid)
        val oldEnabled = controller.isEnabled()
        try {
            controller.setEnabled(false)
            controller.setRule(uid, 2)
            assertTrue("Inactive OEM denial must prevent activation", backend().startInternal().isFailure)
            assertFalse(controller.isEnabled())
            assertEquals(2, controller.getRule(uid))
            controller.setEnabled(true)
            backend().startInternal().getOrThrow()
            backend().stopInternal().getOrThrow()
            assertTrue("Existing active OEM chain stays enabled", controller.isEnabled())
            assertEquals("Existing OEM rule survives stop", 2, controller.getRule(uid))
        } finally {
            controller.setRule(uid, oldRule)
            controller.setEnabled(oldEnabled)
        }
    }

    @Test fun inventoryRetainsRealWorkIdentityAndFindsProfileOnlyMetadata() = runBlocking<Unit> {
        prepare()
        val uid = info(10).uid
        try {
            assertTrue(shell("pm uninstall -k --user 10 $pkg").contains("Success"))
            HiddenApiHelper.clearInstalledAppsCache()
            assertFalse(HiddenApiHelper.getInstalledApplicationsAsUser(context, 0, 10).any { it.packageName == pkg })
            val retained = HiddenApiHelper.getInstalledApplicationsAsUser(context, PackageManager.MATCH_UNINSTALLED_PACKAGES, 10)
                .single { it.packageName == pkg }
            assertEquals(uid, retained.uid)
            assertEquals(0, retained.flags and ApplicationInfo.FLAG_INSTALLED)
            assertTrue(info(0).flags and ApplicationInfo.FLAG_INSTALLED != 0)
            assertNull(HiddenApiHelper.getPackageInfoAsUser(context, pkg, 0, 10))
            assertTrue(shell("cmd package install-existing --user 10 $pkg").contains("installed"))
            assertTrue(shell("pm uninstall -k --user 0 $pkg").contains("Success"))
            HiddenApiHelper.clearInstalledAppsCache()
            val workOnly = HiddenApiHelper.getPackageInfoAsUser(context, pkg, PackageManager.GET_PERMISSIONS, 10)
            assertNotNull(workOnly)
            assertEquals(uid, workOnly!!.applicationInfo!!.uid)
            assertEquals("Background Check test app", context.packageManager.getApplicationLabel(workOnly.applicationInfo!!).toString())
            assertTrue(workOnly.requestedPermissions!!.contains(android.Manifest.permission.INTERNET))
            assertNull(HiddenApiHelper.getApplicationInfoAsUser(context, pkg, 0, 0))
        } finally {
            shell("cmd package install-existing --user 0 $pkg")
            shell("cmd package install-existing --user 10 $pkg")
            HiddenApiHelper.clearInstalledAppsCache()
        }
    }

    @Test fun networkShortcutCarriesTheSelectedWorkProfileIntoItsAppSheet() = runBlocking<Unit> {
        prepare()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        val prefs = context.getSharedPreferences(Constants.Settings.PREFS_NAME, Context.MODE_PRIVATE)
        val oldPrompt = prefs.getBoolean(Constants.Settings.KEY_SHOW_FIREWALL_START_PROMPT, true)
        prefs.edit().putBoolean(Constants.Settings.KEY_SHOW_FIREWALL_START_PROMPT, false).commit()
        val intent = android.content.Intent(context, dev.bgcheck.network.ui.MainActivity::class.java)
            .putExtra("dev.bgcheck.network.PACKAGE", pkg).putExtra("dev.bgcheck.network.USER_ID", 10)
        try {
            androidx.test.core.app.ActivityScenario.launch<android.app.Activity>(intent).use {
                var sheetVisible = false
                repeat(60) {
                    if (!sheetVisible) {
                        sheetVisible = runCatching {
                            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(
                                dev.bgcheck.network.R.id.action_sheet_settings_icon))
                                .check(androidx.test.espresso.assertion.ViewAssertions.matches(
                                    androidx.test.espresso.matcher.ViewMatchers.isDisplayed()))
                        }.isSuccess
                        if (!sheetVisible) delay(100)
                    }
                }
                assertTrue("Shortcut opens an installation-specific sheet", sheetVisible)
                androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(
                    dev.bgcheck.network.R.id.action_sheet_package_name))
                    .check(androidx.test.espresso.assertion.ViewAssertions.matches(
                        androidx.test.espresso.matcher.ViewMatchers.withText("Work (user 10) · $pkg")))
                androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(
                    dev.bgcheck.network.R.id.action_sheet_settings_icon))
                    .perform(androidx.test.espresso.action.ViewActions.click())
                delay(1000)
                assertTrue("Work shortcut Settings targets user10", shell("dumpsys activity activities").lineSequence().any {
                    (it.contains("topResumedActivity") || it.contains("mResumedActivity")) && it.contains("u10") && it.contains("com.android.settings")
                })
                shell("input keyevent 4")
            }
        } finally { prefs.edit().putBoolean(Constants.Settings.KEY_SHOW_FIREWALL_START_PROMPT, oldPrompt).commit() }
    }

    @Test fun androidSettingsOpensTheWorkInstallation() = runBlocking<Unit> {
        prepare()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { context.openAppSettings(pkg, 10) }
        delay(1500)
        val activities = shell("dumpsys activity activities")
        assertTrue("Settings must be resumed in user10", activities.lineSequence().any {
            (it.contains("topResumedActivity") || it.contains("mResumedActivity")) && it.contains("u10") && it.contains("com.android.settings")
        })
        shell("input keyevent 4")
    }
}
