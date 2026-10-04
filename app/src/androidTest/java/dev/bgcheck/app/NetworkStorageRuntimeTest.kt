package dev.bgcheck.app

import android.content.Context
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
            val rule = FirewallRule("dev.bgcheck.fixture", userId = 10, uid = 1010123, appName = "Work probe", wifiBlocked = true, lanBlocked = true, blockWhenBackground = true)
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
}
