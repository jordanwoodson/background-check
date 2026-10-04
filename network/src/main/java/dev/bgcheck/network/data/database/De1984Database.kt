package dev.bgcheck.network.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.bgcheck.network.data.database.dao.FirewallRuleDao
import dev.bgcheck.network.data.database.entity.FirewallRuleEntity

@Database(
    entities = [
        FirewallRuleEntity::class
    ],
    version = 7,
    exportSchema = true
)
abstract class De1984Database : RoomDatabase() {

    abstract fun firewallRuleDao(): FirewallRuleDao
}
