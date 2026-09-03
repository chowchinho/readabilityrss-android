package co.chinho.readabilityreader.ui.articles

import org.junit.Assert.assertEquals
import org.junit.Test

class ArticleScopeTest {

    @Test
    fun bothNullIsAllSources() {
        assertEquals(ArticleScope.AllSources, resolveArticleScope(null, null))
    }

    @Test
    fun bothZeroIsAllSources() {
        assertEquals(ArticleScope.AllSources, resolveArticleScope(0L, 0L))
    }

    @Test
    fun zeroAndNullSentinelsAreEquivalent() {
        assertEquals(ArticleScope.AllSources, resolveArticleScope(0L, null))
        assertEquals(ArticleScope.AllSources, resolveArticleScope(null, 0L))
    }

    @Test
    fun feedIdAloneIsSingleFeed() {
        assertEquals(ArticleScope.SingleFeed(42L), resolveArticleScope(42L, null))
        assertEquals(ArticleScope.SingleFeed(42L), resolveArticleScope(42L, 0L))
    }

    @Test
    fun groupIdAloneIsCategory() {
        assertEquals(ArticleScope.Category(7L), resolveArticleScope(null, 7L))
        assertEquals(ArticleScope.Category(7L), resolveArticleScope(0L, 7L))
    }

    @Test
    fun groupWinsOverFeed() {
        assertEquals(ArticleScope.Category(7L), resolveArticleScope(42L, 7L))
    }

    @Test
    fun targetIdsAreMutuallyExclusive() {
        val category = resolveArticleScope(42L, 7L)
        assertEquals(null, category.targetFeedId)
        assertEquals(7L, category.targetGroupId)

        val feed = resolveArticleScope(42L, null)
        assertEquals(42L, feed.targetFeedId)
        assertEquals(null, feed.targetGroupId)

        val all = resolveArticleScope(null, null)
        assertEquals(null, all.targetFeedId)
        assertEquals(null, all.targetGroupId)
    }
}
