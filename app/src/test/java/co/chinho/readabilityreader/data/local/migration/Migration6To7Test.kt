package co.chinho.readabilityreader.data.local.migration

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.chinho.readabilityreader.data.local.ReadabilityDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration6To7Test {

    @Test
    fun testMigration6To7PreservesArticlesAndImages() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_test_v6_v7.db"
        context.deleteDatabase(dbName)

        val openHelper = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(6) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS `articles` (
                                `id` INTEGER NOT NULL,
                                `feedId` INTEGER NOT NULL,
                                `title` TEXT NOT NULL,
                                `url` TEXT NOT NULL,
                                `content` TEXT,
                                `publishedAt` INTEGER NOT NULL,
                                `isRead` INTEGER NOT NULL,
                                `isSaved` INTEGER NOT NULL,
                                `thumbnailUrl` TEXT,
                                `cachedAt` INTEGER NOT NULL,
                                `contentCachedAt` INTEGER,
                                `imagesCachedAt` INTEGER,
                                PRIMARY KEY(`id`)
                            )
                            """.trimIndent()
                        )

                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS `feeds` (
                                `id` INTEGER NOT NULL,
                                `groupId` INTEGER NOT NULL,
                                `title` TEXT NOT NULL,
                                `url` TEXT NOT NULL,
                                `siteUrl` TEXT,
                                `faviconId` INTEGER,
                                `faviconUrl` TEXT,
                                `faviconProxyUrl` TEXT,
                                `lastSyncedAt` INTEGER,
                                `listViewMode` TEXT NOT NULL DEFAULT 'standard',
                                PRIMARY KEY(`id`)
                            )
                            """.trimIndent()
                        )

                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS `groups` (
                                `id` INTEGER NOT NULL,
                                `title` TEXT NOT NULL,
                                PRIMARY KEY(`id`)
                            )
                            """.trimIndent()
                        )

                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS `server_profiles` (
                                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `name` TEXT NOT NULL,
                                `serverUrl` TEXT NOT NULL,
                                `username` TEXT NOT NULL,
                                `isActive` INTEGER NOT NULL
                            )
                            """.trimIndent()
                        )

                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS `read_state_queue` (
                                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `articleId` INTEGER NOT NULL,
                                `action` TEXT NOT NULL,
                                `queuedAt` INTEGER NOT NULL
                            )
                            """.trimIndent()
                        )

                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS `jotty_queue` (
                                `articleId` INTEGER NOT NULL,
                                `title` TEXT NOT NULL,
                                `content` TEXT NOT NULL,
                                `category` TEXT NOT NULL,
                                `attempts` INTEGER NOT NULL,
                                `lastAttemptAt` INTEGER NOT NULL,
                                `lastErrorMessage` TEXT,
                                PRIMARY KEY(`articleId`)
                            )
                            """.trimIndent()
                        )

                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS `article_images` (
                                `articleId` INTEGER NOT NULL,
                                `image_url` TEXT NOT NULL,
                                `position` INTEGER NOT NULL DEFAULT 0,
                                `focalX` INTEGER NOT NULL DEFAULT 50,
                                `focalY` INTEGER NOT NULL DEFAULT 50,
                                `focalComputed` INTEGER NOT NULL DEFAULT 0,
                                PRIMARY KEY(`articleId`, `image_url`),
                                FOREIGN KEY(`articleId`) REFERENCES `articles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                            )
                            """.trimIndent()
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )

        val v6Db = openHelper.writableDatabase

        v6Db.execSQL(
            """
            INSERT INTO articles (id, feedId, title, url, content, publishedAt, isRead, isSaved, thumbnailUrl, cachedAt, contentCachedAt, imagesCachedAt)
            VALUES (1001, 10, 'Test Article', 'https://example.com/1001', 'Sample Content', 1700000000000, 0, 1, 'https://example.com/thumb.jpg', 1700000000000, 1700000000000, NULL)
            """.trimIndent()
        )

        v6Db.execSQL(
            """
            INSERT INTO article_images (articleId, image_url, position, focalX, focalY, focalComputed)
            VALUES (1001, 'https://example.com/thumb.jpg', 0, 50, 50, 0)
            """.trimIndent()
        )

        v6Db.close()

        val roomDb = Room.databaseBuilder(context, ReadabilityDatabase::class.java, dbName)
            .addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
            .allowMainThreadQueries()
            .build()

        val article = kotlinx.coroutines.runBlocking { roomDb.articleDao().getArticleContent(1001) }
        assertEquals("Sample Content", article)

        val images = kotlinx.coroutines.runBlocking { roomDb.articleImageDao().getOrderedImagesForArticle(1001) }
        assertEquals(1, images.size)
        assertEquals("https://example.com/thumb.jpg", images[0].imageUrl)

        val cursor = roomDb.openHelper.readableDatabase.query("SELECT score, primaryTopic, vote FROM articles WHERE id = 1001")
        assertTrue(cursor.moveToFirst())
        assertTrue(cursor.isNull(0))
        assertTrue(cursor.isNull(1))
        assertTrue(cursor.isNull(2))
        cursor.close()

        val weightsCursor = roomDb.openHelper.readableDatabase.query("SELECT COUNT(*) FROM label_weights")
        assertTrue(weightsCursor.moveToFirst())
        assertEquals(0, weightsCursor.getInt(0))
        weightsCursor.close()

        roomDb.close()
        context.deleteDatabase(dbName)
    }
}
