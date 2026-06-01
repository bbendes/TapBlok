package com.cj.tapblok.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "migration-test"

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate1To2_preservesBlockedAppsAndAddsGroupsTable() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL("INSERT INTO blocked_apps (packageName) VALUES ('com.example.one')")
            db.execSQL("INSERT INTO blocked_apps (packageName) VALUES ('com.example.two')")
        }

        val migratedDb = helper.runMigrationsAndValidate(
            TEST_DB,
            2,
            true,
            AppDatabase.MIGRATION_1_2
        )

        migratedDb.query("SELECT packageName, groupId FROM blocked_apps ORDER BY packageName").use { c ->
            assertEquals(2, c.count)
            assertTrue(c.moveToNext())
            assertEquals("com.example.one", c.getString(0))
            assertTrue(c.isNull(1))
            assertTrue(c.moveToNext())
            assertEquals("com.example.two", c.getString(0))
            assertTrue(c.isNull(1))
        }

        migratedDb.query("SELECT COUNT(*) FROM app_groups").use { c ->
            assertTrue(c.moveToNext())
            assertEquals(0, c.getInt(0))
        }
    }

    @Test
    fun migratedDbCanOpenAndReadEntities() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL("INSERT INTO blocked_apps (packageName) VALUES ('com.example.persisted')")
        }
        helper.runMigrationsAndValidate(TEST_DB, 2, true, AppDatabase.MIGRATION_1_2)

        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(ctx, AppDatabase::class.java, TEST_DB)
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .openHelperFactory(FrameworkSQLiteOpenHelperFactory())
            .build()
        try {
            val all = kotlinx.coroutines.runBlocking { db.blockedAppDao().getAllBlockedAppsList() }
            assertEquals(1, all.size)
            assertEquals("com.example.persisted", all.first().packageName)
            assertNull(all.first().groupId)
        } finally {
            db.close()
        }
    }
}
