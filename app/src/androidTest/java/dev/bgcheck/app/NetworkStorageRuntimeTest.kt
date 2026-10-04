package dev.bgcheck.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import dev.bgcheck.network.De1984Application
import dev.bgcheck.network.data.multiuser.HiddenApiHelper
import dev.bgcheck.network.data.database.FirewallDatabaseMigrations
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.bgcheck.network.data.database.De1984Database
import dev.bgcheck.network.data.repository.FirewallRepositoryImpl
import dev.bgcheck.network.domain.model.FirewallRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NetworkStorageRuntimeTest {
    @Test fun restorePersistsProfilesAndRollsBackFailedReplacement() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "network-restore-test.db"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, De1984Database::class.java, name).build()
        var db = open()
        try {
            var repo = FirewallRepositoryImpl(db.firewallRuleDao(), context)
            (context as De1984Application).dependencies.shizukuManager.checkShizukuStatus()
            HiddenApiHelper.awaitCrossUserPermission(context)
            val profile = requireNotNull(HiddenApiHelper.currentProfile(context, 10))
            val app = requireNotNull(HiddenApiHelper.getApplicationInfoAsUser(context, "dev.bgcheck.fixture", 0, 10))
            val rule = FirewallRule("dev.bgcheck.fixture", userId = 10, uid = app.uid, appName = "Work probe", wifiBlocked = true, lanBlocked = true, blockWhenBackground = true, userSerial = profile.serialNumber)
            repo.restoreRules(listOf(rule), true)
            db.close()
            db = open()
            repo = FirewallRepositoryImpl(db.firewallRuleDao(), context)
            assertEquals(rule, repo.getRuleByPackageSync(rule.packageName, 10))
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_test BEFORE INSERT ON firewall_rules WHEN NEW.packageName = 'dev.bgcheck.reject' BEGIN SELECT RAISE(ABORT, 'test failure'); END")
            try {
                repo.restoreRules(listOf(rule.copy(packageName = "dev.bgcheck.reject")), true)
                fail("Trigger should reject replacement")
            } catch (expected: android.database.sqlite.SQLiteException) { }
            assertEquals("Failed replacement must preserve existing rules", rule, repo.getRuleByPackageSync(rule.packageName, 10))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
    @Test fun versionSixMigratesRealProfileRulesAndRoomValidatesTheSchema() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "network-migration-six-test.db"
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            old.execSQL("""CREATE TABLE firewall_rules (
                packageName TEXT NOT NULL, userId INTEGER NOT NULL, uid INTEGER NOT NULL,
                appName TEXT NOT NULL, wifiBlocked INTEGER NOT NULL, mobileBlocked INTEGER NOT NULL,
                blockWhenBackground INTEGER NOT NULL, blockWhenRoaming INTEGER NOT NULL,
                lanBlocked INTEGER NOT NULL, enabled INTEGER NOT NULL, isSystemApp INTEGER NOT NULL,
                hasInternetPermission INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL,
                PRIMARY KEY(packageName, userId))""")
            for (user in listOf(0, 10, 999)) old.execSQL(
                "INSERT INTO firewall_rules VALUES (?, ?, ?, ?, 1, 0, 1, 0, 1, 1, 0, 1, 123, 456)",
                arrayOf<Any>("dev.bgcheck.fixture", user, user * 100000 + 10123, "Fixture $user")
            )
            old.version = 6
        }
        val db = Room.databaseBuilder(context, De1984Database::class.java, name)
            .addMigrations(FirewallDatabaseMigrations.profileIdentity(context)).build()
        try {
            // Opening the production Room model also validates every migrated column and key.
            val sql = db.openHelper.writableDatabase
            assertEquals(7, sql.version)
            sql.query("SELECT * FROM firewall_rules ORDER BY userId").use { rows ->
                assertEquals(3, rows.count)
                for (user in listOf(0, 10, 999)) {
                    assertTrue(rows.moveToNext())
                    assertEquals(user, rows.getInt(rows.getColumnIndexOrThrow("userId")))
                    assertEquals(1, rows.getInt(rows.getColumnIndexOrThrow("wifiBlocked")))
                    assertEquals(0, rows.getInt(rows.getColumnIndexOrThrow("mobileBlocked")))
                    assertEquals(1, rows.getInt(rows.getColumnIndexOrThrow("blockWhenBackground")))
                    assertEquals(1, rows.getInt(rows.getColumnIndexOrThrow("lanBlocked")))
                    assertEquals(123L, rows.getLong(rows.getColumnIndexOrThrow("createdAt")))
                    assertEquals(456L, rows.getLong(rows.getColumnIndexOrThrow("updatedAt")))
                    val serialColumn = rows.getColumnIndexOrThrow("userSerial")
                    if (user == 999) assertTrue(rows.isNull(serialColumn))
                    else assertEquals(requireNotNull(HiddenApiHelper.currentProfile(context, user)).serialNumber,
                        rows.getLong(serialColumn))
                }
            }
            assertNull("Removed profiles stay hidden after migration",
                FirewallRepositoryImpl(db.firewallRuleDao(), context).getRuleByPackageSync("dev.bgcheck.fixture", 999))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

}
