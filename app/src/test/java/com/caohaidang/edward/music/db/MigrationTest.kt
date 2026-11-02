package com.caohaidang.edward.music.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Runs the real Room migrations against the exported schemas in app/schemas, on the JVM via Robolectric
 * (no emulator needed). A broken migration here means every user would crash on opening the app after an
 * update, because the project deliberately has no fallbackToDestructiveMigration.
 *
 * To add a migration: bump `version` in MusicDatabase.kt, build once so Room exports the new schema JSON,
 * add the migration to MANUAL_MIGRATIONS (or an AutoMigration), then add a data-preserving test below.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        InternalDatabase::class.java,
    )

    @Test
    fun migrate12To13_keepsPlaylistsAndLeavesCustomThumbnailEmpty() {
        helper.createDatabase(TEST_DB, 12).apply {
            execSQL("INSERT INTO playlist (id, name, browseId) VALUES ('p1', 'Road trip', 'VL123')")
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 13, true, MIGRATION_12_13)

        db.query("SELECT name, browseId, customThumbnail FROM playlist WHERE id = 'p1'").use { c ->
            assertTrue(c.moveToFirst(), "playlist row must survive the migration")
            assertEquals("Road trip", c.getString(0))
            assertEquals("VL123", c.getString(1))
            assertTrue(c.isNull(2), "new column starts empty")
        }
    }

    @Test
    fun migrate13To14_stampsCreatedAtInCreationOrder() {
        helper.createDatabase(TEST_DB, 13).apply {
            execSQL("INSERT INTO playlist (id, name) VALUES ('a', 'First')")
            execSQL("INSERT INTO playlist (id, name) VALUES ('b', 'Second')")
            execSQL("INSERT INTO playlist (id, name, customThumbnail) VALUES ('c', 'Third', 'cover.jpg')")
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 14, true, MIGRATION_13_14)

        val createdAt = linkedMapOf<String, Long>()
        db.query("SELECT id, createdAt FROM playlist ORDER BY rowid").use { c ->
            while (c.moveToNext()) {
                assertTrue(!c.isNull(1), "createdAt must be filled for old rows")
                createdAt[c.getString(0)] = c.getLong(1)
            }
        }
        assertEquals(listOf("a", "b", "c"), createdAt.keys.toList())
        val times = createdAt.values.toList()
        assertTrue(times[0] < times[1] && times[1] < times[2], "older playlists keep the smaller timestamp: $times")

        db.query("SELECT customThumbnail FROM playlist WHERE id = 'c'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("cover.jpg", c.getString(0), "existing data in other columns is untouched")
        }
    }

    /** The whole chain a user on the oldest supported version would go through (manual + auto migrations). */
    @Test
    fun migrateAll_fromVersion1_toCurrent() {
        helper.createDatabase(TEST_DB, 1).close()

        val db = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            InternalDatabase::class.java,
            TEST_DB,
        ).addMigrations(*MANUAL_MIGRATIONS).build()
        try {
            // Opening forces every migration to run and Room to validate the final schema.
            assertNotNull(db.openHelper.writableDatabase)
        } finally {
            db.close()
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
