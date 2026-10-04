package dev.bgcheck.app

import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bgcheck.app.data.AppInventory
import dev.bgcheck.app.data.AssociationKind
import dev.bgcheck.app.data.DescriptionSource
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Read-only phone verification. The expected inventory comes independently from Android's shell. */
@RunWith(AndroidJUnit4::class)
class UsbPackageAuditTest {
    @Test fun allRecordedPhoneAppsHavePurposesAndCorrectIdentityLinks(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val descriptor = automation.executeShellCommand("pm list packages -u --user ${Process.myUid() / 100000}")
        val expected = ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readLines() }
            .filter { it.startsWith("package:") }.map { it.removePrefix("package:") }.toSet() - "dev.bgcheck.app.test"
        val inventory = AppInventory(context).load().filter { it.packageName != "dev.bgcheck.app.test" }
        assertEquals("The app inventory must cover the shell inventory", expected, inventory.map { it.packageName }.toSet())
        val byId = inventory.associateBy { it.packageName }
        inventory.forEach { app ->
            assertEquals("Purpose missing for ${app.packageName}", DescriptionSource.GUIDE, app.description.source)
            assertTrue("Sources missing for ${app.packageName}", app.description.citations.isNotEmpty())
            app.associations.filter { it.kind == AssociationKind.SHARED_UID }.forEach { association ->
                val peer = requireNotNull(byId[association.packageName])
                assertTrue(app.installed && peer.installed)
                assertEquals(app.uid, peer.uid)
            }
            if (!app.installed) assertFalse(app.associations.any { it.kind == AssociationKind.SHARED_UID })
        }
        val output = JSONObject().put("packages", JSONArray(inventory.map { app ->
            JSONObject().put("packageName", app.packageName).put("label", app.label)
                .put("installed", app.installed).put("system", app.system).put("uid", app.uid)
                .put("description", app.description.text).put("source", app.description.source.name)
                .put("associations", JSONArray(app.associations.map {
                    JSONObject().put("packageName", it.packageName).put("kind", it.kind.name)
                }))
        }))
        context.filesDir.resolve("usb-package-audit.json").writeText(output.toString(2))
        // Hidden code libraries may not be returned by an ordinary app inventory query.
        val library = runCatching { context.packageManager.getApplicationInfo("com.google.android.trichromelibrary", PackageManager.MATCH_UNINSTALLED_PACKAGES) }
        android.util.Log.i("UsbPackageAudit", "Package coverage: ${inventory.size}; hidden library visible: ${library.isSuccess}")
    }
}
