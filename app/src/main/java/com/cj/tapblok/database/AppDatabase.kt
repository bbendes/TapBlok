package com.cj.tapblok.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [BlockedApp::class, AppGroup::class, GroupTimeRule::class, EmergencyBlock::class],
    version = 5,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun blockedAppDao(): BlockedAppDao
    abstract fun appGroupDao(): AppGroupDao
    abstract fun groupTimeRuleDao(): GroupTimeRuleDao
    abstract fun emergencyBlockDao(): EmergencyBlockDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS app_groups (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        breakDurationMs INTEGER,
                        breakCount INTEGER,
                        minBetweenBreaksMs INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE blocked_apps_new (
                        packageName TEXT NOT NULL PRIMARY KEY,
                        groupId INTEGER,
                        FOREIGN KEY(groupId) REFERENCES app_groups(id) ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "INSERT INTO blocked_apps_new (packageName, groupId) SELECT packageName, NULL FROM blocked_apps"
                )
                db.execSQL("DROP TABLE blocked_apps")
                db.execSQL("ALTER TABLE blocked_apps_new RENAME TO blocked_apps")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_blocked_apps_groupId ON blocked_apps(groupId)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS group_time_rules (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        groupId INTEGER NOT NULL,
                        daysOfWeekMask INTEGER NOT NULL,
                        startMinuteOfDay INTEGER NOT NULL,
                        endMinuteOfDay INTEGER NOT NULL,
                        priority INTEGER NOT NULL DEFAULT 0,
                        blockingEnabled INTEGER NOT NULL DEFAULT 1,
                        breakCountOverride INTEGER,
                        breakDurationMsOverride INTEGER,
                        minBetweenBreaksMsOverride INTEGER,
                        FOREIGN KEY(groupId) REFERENCES app_groups(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_group_time_rules_groupId ON group_time_rules(groupId)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_groups ADD COLUMN minDelayBeforeFirstBreakMs INTEGER")
                db.execSQL("ALTER TABLE group_time_rules ADD COLUMN minDelayBeforeFirstBreakMsOverride INTEGER")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS emergency_blocks (
                        packageName TEXT NOT NULL PRIMARY KEY,
                        expiresAtMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
