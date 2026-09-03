package co.chinho.readabilityreader.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import co.chinho.readabilityreader.data.local.migration.MIGRATION_5_6
import co.chinho.readabilityreader.data.local.migration.MIGRATION_7_8
import co.chinho.readabilityreader.data.local.migration.MIGRATION_8_9
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "migration-test"

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ReadabilityDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate5To6_preservesRowsAndAppliesDefaults() {
        helper.createDatabase(TEST_DB, 5).apply {
            execSQL(
                "INSERT INTO articles (id, feedId, title, url, content, publishedAt, isRead, isSaved, cachedAt) " +
                    "VALUES (1, 10, 'T', 'https://e.test/a', '<p>x</p>', 0, 0, 0, 0)"
            )
            execSQL(
                "INSERT INTO article_images (articleId, image_url) " +
                    "VALUES (1, 'https://e.test/img.jpg')"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 6, true, MIGRATION_5_6)

        db.query(
            "SELECT articleId, image_url, position, focalX, focalY, focalComputed FROM article_images"
        ).use { cursor ->
            assertTrue("row survived migration", cursor.moveToFirst())
            assertEquals(1L, cursor.getLong(0))
            assertEquals("https://e.test/img.jpg", cursor.getString(1))
            assertEquals(0, cursor.getInt(2))
            assertEquals(50, cursor.getInt(3))
            assertEquals(50, cursor.getInt(4))
            assertEquals(0, cursor.getInt(5))
            assertEquals("exactly one row", 1, cursor.count)
        }
    }

    @Test
    fun migrate7To8_preservesRowsAndCreatesIndices() {
        helper.createDatabase(TEST_DB, 7).apply {
            execSQL(
                "INSERT INTO articles (id, feedId, title, url, content, publishedAt, isRead, isSaved, cachedAt) " +
                    "VALUES (1, 10, 'T', 'https://e.test/a', '<p>x</p>', 0, 0, 1, 0)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 8, true, MIGRATION_7_8)

        db.query(
            "SELECT id, feedId, isSaved FROM articles WHERE id = 1"
        ).use { cursor ->
            assertTrue("row survived migration", cursor.moveToFirst())
            assertEquals(1L, cursor.getLong(0))
            assertEquals(10L, cursor.getLong(1))
            assertEquals(1, cursor.getInt(2))
            assertEquals("exactly one row", 1, cursor.count)
        }
    }

    @Test
    fun migrate8To9_preservesRowsAndAddsSnippetColumn() {
        helper.createDatabase(TEST_DB, 8).apply {
            execSQL(
                "INSERT INTO articles (id, feedId, title, url, content, publishedAt, isRead, isSaved, cachedAt) " +
                    "VALUES (1, 10, 'T', 'https://e.test/a', '<p>x</p>', 0, 0, 1, 0)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 9, true, MIGRATION_8_9)

        db.query(
            "SELECT id, feedId, snippetText FROM articles WHERE id = 1"
        ).use { cursor ->
            assertTrue("row survived migration", cursor.moveToFirst())
            assertEquals(1L, cursor.getLong(0))
            assertEquals(10L, cursor.getLong(1))
            assertTrue("snippetText defaults to null", cursor.isNull(2))
            assertEquals("exactly one row", 1, cursor.count)
        }
    }
}
