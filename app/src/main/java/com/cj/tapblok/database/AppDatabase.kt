package com.cj.tapblok.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [BlockedApp::class, AppGroup::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun blockedAppDao(): BlockedAppDao
    abstract fun appGroupDao(): AppGroupDao

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

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
