package dev.bgcheck.app

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bgcheck.network.De1984Application
import dev.bgcheck.network.domain.model.Package
import dev.bgcheck.network.domain.model.PackageListCodec
import dev.bgcheck.network.presentation.viewmodel.PackagesViewModel
import dev.bgcheck.network.presentation.viewmodel.SettingsViewModel
import dev.bgcheck.network.utils.Constants
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.containsString
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import dev.bgcheck.network.R as NetworkR

/** Preview-only tests: exporting and importing lists never confirms an uninstall. */
@RunWith(AndroidJUnit4::class)
class NetworkPackageListRuntimeTest {
    private val app = ApplicationProvider.getApplicationContext<De1984Application>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Before fun allowNotificationsOnTheDisposableEmulator() {
        if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(
            app.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test fun visibleWorkExportImportsOnlyWorkAndMixedExportKeepsBothCopies() = runBlocking {
        app.dependencies.shizukuManager.checkShizukuStatus()
        val personal = app.dependencies.packageRepository.getPackage(FIXTURE, 0).getOrThrow()
        val work = app.dependencies.packageRepository.getPackage(FIXTURE, 10).getOrThrow()
        val store = ViewModelStore()
        lateinit var exporter: PackagesViewModel
        instrumentation.runOnMainSync {
            val deps = app.dependencies
            exporter = ViewModelProvider(store, PackagesViewModel.Factory(app, deps.provideGetPackagesUseCase(),
                deps.provideManagePackageUseCase(), deps.superuserBannerState, deps.rootManager, deps.shizukuManager,
                deps.packageDataChanged))[PackagesViewModel::class.java]
        }
        try {
            withSettings { settings ->
                listOf(listOf(work), listOf(personal, work)).forEachIndexed { index, rows ->
                    val file = app.cacheDir.resolve("profile-list-$index.txt").apply { delete() }
                    instrumentation.runOnMainSync { exporter.exportVisiblePackages(Uri.fromFile(file), rows) }
                    await { file.exists() && file.length() > 0 }
                    assertEquals(rows.map { it.userId }, PackageListCodec.decode(file.readText()).entries.map { it.userId })
                    instrumentation.runOnMainSync { settings.importUninstalledApps(Uri.fromFile(file)) }
                    await { settings.uiState.value.importUninstalledPreview != null || settings.uiState.value.error != null }
                    assertNull(settings.uiState.value.error)
                    val preview = requireNotNull(settings.uiState.value.importUninstalledPreview)
                    assertEquals(rows.map { FIXTURE to it.userId }.toSet(), preview.packagesToUninstall.toSet())
                    assertEquals(rows.map { it.uid }.toSet(), preview.expectedIdentities.values.map { it.uid }.toSet())
                    awaitText("Work (User 10)")
                    if (rows.size == 2) awaitText("Personal (User 0)")
                    onView(withText(NetworkR.string.dialog_cancel)).perform(click())
                    await { settings.uiState.value.importUninstalledPreview == null }
                    file.delete()
                }
            }
        } finally {
            instrumentation.runOnMainSync { store.clear() }
        }
    }

    @Test fun settingsExportRetainsBothProfilesForUninstalledPackages() = runBlocking {
        val deps = app.dependencies
        deps.shizukuManager.checkShizukuStatus()
        val rows = listOf(0, 10).map { deps.packageRepository.getPackage(FIXTURE, it).getOrThrow().copy(isInstalled = false) }
        val repository = object : dev.bgcheck.network.domain.repository.PackageRepository by deps.packageRepository {
            override suspend fun getUninstalledSystemPackages(): Result<List<Package>> = Result.success(rows)
        }
        val store = ViewModelStore()
        lateinit var settings: SettingsViewModel
        instrumentation.runOnMainSync {
            settings = ViewModelProvider(store, SettingsViewModel.Factory(app, deps.permissionManager, deps.rootManager,
                deps.shizukuManager, deps.firewallManager, deps.firewallRepository, deps.captivePortalManager,
                deps.bootProtectionManager, deps.provideSmartPolicySwitchUseCase(), repository))[SettingsViewModel::class.java]
        }
        val file = app.cacheDir.resolve("retained-profile-list.txt")
        try {
            instrumentation.runOnMainSync { settings.exportUninstalledApps(Uri.fromFile(file)) }
            await { file.exists() && file.length() > 0 || settings.uiState.value.error != null }
            assertNull(settings.uiState.value.error)
            val entries = PackageListCodec.decode(file.readText()).entries
            assertEquals(setOf(0, 10), entries.map { it.userId }.toSet())
            assertEquals(rows.map { it.userSerial }.toSet(), entries.map { it.userSerial }.toSet())
        } finally {
            file.delete()
            instrumentation.runOnMainSync { store.clear() }
        }
    }

    @Test fun legacyFileShowsAnExplicitProfileChoiceBeforeTheUninstallPreview() = runBlocking {
        app.dependencies.shizukuManager.checkShizukuStatus()
        val file = app.cacheDir.resolve("legacy-package-list.txt").apply { writeText("# Existing name-only format\n$FIXTURE\n") }
        try {
            withSettings { settings ->
                instrumentation.runOnMainSync { settings.importUninstalledApps(Uri.fromFile(file)) }
                await { settings.uiState.value.importProfileSelection != null || settings.uiState.value.error != null }
                assertNull(settings.uiState.value.error)
                assertNull(settings.uiState.value.importUninstalledPreview)
                awaitText("Name-only list: choose target profile")
                onView(withText("Work (User 10) · 1 package names")).perform(click())
                await { settings.uiState.value.importUninstalledPreview != null || settings.uiState.value.error != null }
                val preview = requireNotNull(settings.uiState.value.importUninstalledPreview)
                assertEquals(listOf(FIXTURE to 10), preview.packagesToUninstall)
                awaitText("This name-only list will remove apps only from Work (User 10)")
                onView(withText(NetworkR.string.dialog_cancel)).perform(click())
            }
        } finally { file.delete() }
    }

    private fun withSettings(block: (SettingsViewModel) -> Unit) {
        val prefs = app.getSharedPreferences(Constants.Settings.PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val oldPrompt = prefs.getBoolean(Constants.Settings.KEY_SHOW_FIREWALL_START_PROMPT, true)
        prefs.edit().putBoolean(Constants.Settings.KEY_SHOW_FIREWALL_START_PROMPT, false).commit()
        try {
            ActivityScenario.launch<android.app.Activity>(Intent(app, dev.bgcheck.network.ui.MainActivity::class.java)).use { scenario ->
                onView(withId(NetworkR.id.settingsFragment)).perform(click())
                lateinit var settings: SettingsViewModel
                scenario.onActivity { settings = ViewModelProvider(it as androidx.lifecycle.ViewModelStoreOwner)[SettingsViewModel::class.java] }
                block(settings)
            }
        } finally { prefs.edit().putBoolean(Constants.Settings.KEY_SHOW_FIREWALL_START_PROMPT, oldPrompt).commit() }
    }

    private fun awaitText(text: String) {
        val deadline = SystemClock.uptimeMillis() + 10000
        while (true) {
            try {
                onView(withText(containsString(text))).check(matches(isDisplayed()))
                return
            } catch (failure: androidx.test.espresso.NoMatchingViewException) {
                if (SystemClock.uptimeMillis() >= deadline) {
                    val bitmap = instrumentation.uiAutomation.takeScreenshot()
                    app.filesDir.resolve("package-import-failure.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                    throw failure
                }
                Thread.sleep(100)
            }
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 30000
        while (!condition()) {
            check(SystemClock.uptimeMillis() < deadline) { "Timed out waiting for package-list preview" }
            Thread.sleep(50)
        }
        instrumentation.waitForIdleSync()
    }

    companion object { const val FIXTURE = "dev.bgcheck.fixture" }
}
