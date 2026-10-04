package dev.bgcheck.network.data.database

import android.content.Context
import android.os.UserManager
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Shared by the production database and the on-device migration check. */
object FirewallDatabaseMigrations {
    fun profileIdentity(context: Context): Migration = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE firewall_rules ADD COLUMN userSerial INTEGER")
            val manager = context.getSystemService(UserManager::class.java)
            manager.userProfiles.forEach { handle ->
                val serial = manager.getSerialNumberForUser(handle)
                if (serial >= 0) db.execSQL(
                    "UPDATE firewall_rules SET userSerial = ? WHERE userId = ?",
                    arrayOf<Any>(serial, handle.hashCode())
                )
            }
        }
    }
}
