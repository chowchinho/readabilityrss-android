package co.chinho.readabilityreader.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE article_images ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE article_images ADD COLUMN focalX INTEGER NOT NULL DEFAULT 50")
        db.execSQL("ALTER TABLE article_images ADD COLUMN focalY INTEGER NOT NULL DEFAULT 50")
        db.execSQL("ALTER TABLE article_images ADD COLUMN focalComputed INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE articles ADD COLUMN score REAL DEFAULT NULL")
        db.execSQL("ALTER TABLE articles ADD COLUMN primaryTopic TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE articles ADD COLUMN secondaryTopics TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE articles ADD COLUMN region TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE articles ADD COLUMN articleType TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE articles ADD COLUMN vote TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE articles ADD COLUMN freshness REAL DEFAULT NULL")
        db.execSQL("ALTER TABLE articles ADD COLUMN pairTerms TEXT DEFAULT NULL")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `label_weights` (
                `axis` TEXT NOT NULL,
                `canonicalLabel` TEXT NOT NULL,
                `displayLabel` TEXT NOT NULL,
                `declared` REAL NOT NULL,
                `prior` REAL NOT NULL,
                `votes` INTEGER NOT NULL,
                `explicit` REAL NOT NULL,
                `behavioural` REAL NOT NULL,
                `effective` REAL NOT NULL,
                PRIMARY KEY(`axis`, `canonicalLabel`)
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `vote_queue` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `articleId` INTEGER NOT NULL,
                `vote` TEXT,
                `markRead` INTEGER NOT NULL,
                `queuedAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `event_queue` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `articleId` INTEGER NOT NULL,
                `eventType` TEXT NOT NULL,
                `dwellSeconds` INTEGER,
                `queuedAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_articles_feedId` ON `articles` (`feedId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_articles_isSaved` ON `articles` (`isSaved`)")
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `articles` ADD COLUMN `snippetText` TEXT DEFAULT NULL")
    }
}


