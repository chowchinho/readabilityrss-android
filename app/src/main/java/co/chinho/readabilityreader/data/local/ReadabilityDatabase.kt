package co.chinho.readabilityreader.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import co.chinho.readabilityreader.data.local.dao.ArticleDao
import co.chinho.readabilityreader.data.local.dao.ArticleImageDao
import co.chinho.readabilityreader.data.local.dao.EventQueueDao
import co.chinho.readabilityreader.data.local.dao.FeedDao
import co.chinho.readabilityreader.data.local.dao.GroupDao
import co.chinho.readabilityreader.data.local.dao.JottyQueueDao
import co.chinho.readabilityreader.data.local.dao.LabelWeightDao
import co.chinho.readabilityreader.data.local.dao.ReadStateQueueDao
import co.chinho.readabilityreader.data.local.dao.ServerProfileDao
import co.chinho.readabilityreader.data.local.dao.VoteQueueDao
import co.chinho.readabilityreader.data.local.entity.ArticleEntity
import co.chinho.readabilityreader.data.local.entity.ArticleImageEntity
import co.chinho.readabilityreader.data.local.entity.EventQueueEntity
import co.chinho.readabilityreader.data.local.entity.FeedEntity
import co.chinho.readabilityreader.data.local.entity.GroupEntity
import co.chinho.readabilityreader.data.local.entity.JottyQueueEntity
import co.chinho.readabilityreader.data.local.entity.LabelWeightEntity
import co.chinho.readabilityreader.data.local.entity.ReadStateQueueEntity
import co.chinho.readabilityreader.data.local.entity.ServerProfileEntity
import co.chinho.readabilityreader.data.local.entity.VoteQueueEntity

@Database(
    entities = [
        ArticleEntity::class,
        FeedEntity::class,
        GroupEntity::class,
        ServerProfileEntity::class,
        ReadStateQueueEntity::class,
        JottyQueueEntity::class,
        ArticleImageEntity::class,
        LabelWeightEntity::class,
        VoteQueueEntity::class,
        EventQueueEntity::class,
    ],
    version = 9,
    exportSchema = true,
)
abstract class ReadabilityDatabase : RoomDatabase() {
    abstract fun articleDao(): ArticleDao

    abstract fun feedDao(): FeedDao

    abstract fun groupDao(): GroupDao

    abstract fun serverProfileDao(): ServerProfileDao

    abstract fun readStateQueueDao(): ReadStateQueueDao

    abstract fun jottyQueueDao(): JottyQueueDao

    abstract fun articleImageDao(): ArticleImageDao

    abstract fun labelWeightDao(): LabelWeightDao

    abstract fun voteQueueDao(): VoteQueueDao

    abstract fun eventQueueDao(): EventQueueDao
}
