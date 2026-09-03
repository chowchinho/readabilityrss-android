package co.chinho.readabilityreader.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.chinho.readabilityreader.data.local.ReadabilityDatabase
import co.chinho.readabilityreader.data.local.entity.ArticleEntity
import co.chinho.readabilityreader.data.local.entity.FeedEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArticleDaoGroupFilterTest {

    private lateinit var db: ReadabilityDatabase
    private lateinit var articleDao: ArticleDao
    private lateinit var feedDao: FeedDao

    @Before
    fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ReadabilityDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        articleDao = db.articleDao()
        feedDao = db.feedDao()

        // group 1: feeds 10, 11 ; group 2: feed 20 ; group 3: feed 30 (no articles)
        feedDao.upsertFeeds(
            listOf(
                feed(id = 10L, groupId = 1L),
                feed(id = 11L, groupId = 1L),
                feed(id = 20L, groupId = 2L),
                feed(id = 30L, groupId = 3L),
            )
        )
        articleDao.upsertArticles(
            listOf(
                article(id = 100L, feedId = 10L),
                article(id = 101L, feedId = 11L),
                article(id = 200L, feedId = 20L),
            )
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun feed(id: Long, groupId: Long) = FeedEntity(
        id = id,
        groupId = groupId,
        title = "Feed $id",
        url = "https://example.com/feed/$id",
        siteUrl = null,
        faviconId = null,
        faviconUrl = null,
        faviconProxyUrl = null,
        lastSyncedAt = null,
    )

    private fun article(id: Long, feedId: Long) = ArticleEntity(
        id = id,
        feedId = feedId,
        title = "Article $id",
        url = "https://example.com/$id",
        content = "Content $id",
        publishedAt = 1_700_000_000_000L + id,
        isRead = false,
        isSaved = false,
        thumbnailUrl = null,
        cachedAt = 1_700_000_000_000L,
        contentCachedAt = 1_700_000_000_000L,
        imagesCachedAt = null,
    )

    private fun observe(feedId: Long? = null, groupId: Long? = null): List<Long> = runBlocking {
        articleDao.observeArticles(feedId = feedId, showRead = true, groupId = groupId, stickyIds = emptyList())
            .first()
            .map { it.article.id }
            .sorted()
    }

    @Test
    fun groupFilterReturnsOnlyThatGroupsArticles() {
        assertEquals(listOf(100L, 101L), observe(groupId = 1L))
    }

    @Test
    fun groupFilterSpansEveryFeedInTheGroup() {
        assertEquals(listOf(200L), observe(groupId = 2L))
    }

    @Test
    fun nullGroupIdReturnsEverything() {
        assertEquals(listOf(100L, 101L, 200L), observe())
    }

    @Test
    fun emptyGroupReturnsEmptyNotEverything() {
        assertEquals(emptyList<Long>(), observe(groupId = 3L))
    }

    @Test
    fun unknownGroupReturnsEmpty() {
        assertEquals(emptyList<Long>(), observe(groupId = 999L))
    }

    @Test
    fun feedIdAndGroupIdCompose() {
        assertEquals(listOf(100L), observe(feedId = 10L, groupId = 1L))
        assertEquals(emptyList<Long>(), observe(feedId = 20L, groupId = 1L))
    }
}
