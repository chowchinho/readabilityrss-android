package co.chinho.readabilityreader.di

import android.content.Context
import androidx.room.Room
import co.chinho.readabilityreader.data.local.ReadabilityDatabase
import co.chinho.readabilityreader.data.local.migration.MIGRATION_5_6
import co.chinho.readabilityreader.data.local.migration.MIGRATION_6_7
import co.chinho.readabilityreader.data.local.migration.MIGRATION_7_8
import co.chinho.readabilityreader.data.local.migration.MIGRATION_8_9
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ReadabilityDatabase =
        Room.databaseBuilder(context, ReadabilityDatabase::class.java, "readability.db")
            .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
            // No 4->5 exists, so 3 and 4 have no path to 7 and would throw on open. They predate
            // the article_images join table and rebuild from a sync, so they reset instead.
            // MIGRATION_3_4 cannot be registered alongside this: Room rejects the builder outright
            // if a migration's start or end version matches a destructive-fallback start version,
            // and 3_4 spans both. 5, 6 and 7 keep a guaranteed non-destructive path.
            .fallbackToDestructiveMigrationFrom(3, 4)
            .build()

    @Provides
    fun provideArticleDao(db: ReadabilityDatabase) = db.articleDao()

    @Provides
    fun provideFeedDao(db: ReadabilityDatabase) = db.feedDao()

    @Provides
    fun provideGroupDao(db: ReadabilityDatabase) = db.groupDao()

    @Provides
    fun provideServerProfileDao(db: ReadabilityDatabase) = db.serverProfileDao()

    @Provides
    fun provideReadStateQueueDao(db: ReadabilityDatabase) = db.readStateQueueDao()

    @Provides
    fun provideJottyQueueDao(db: ReadabilityDatabase) = db.jottyQueueDao()

    @Provides
    fun provideArticleImageDao(db: ReadabilityDatabase) = db.articleImageDao()

    @Provides
    fun provideLabelWeightDao(db: ReadabilityDatabase) = db.labelWeightDao()

    @Provides
    fun provideVoteQueueDao(db: ReadabilityDatabase) = db.voteQueueDao()

    @Provides
    fun provideEventQueueDao(db: ReadabilityDatabase) = db.eventQueueDao()
}
