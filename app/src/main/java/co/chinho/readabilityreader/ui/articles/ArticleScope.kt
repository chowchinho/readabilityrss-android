package co.chinho.readabilityreader.ui.articles

sealed interface ArticleScope {
    data object AllSources : ArticleScope
    data class SingleFeed(val feedId: Long) : ArticleScope
    data class Category(val groupId: Long) : ArticleScope
}

val ArticleScope.targetFeedId: Long?
    get() = (this as? ArticleScope.SingleFeed)?.feedId

val ArticleScope.targetGroupId: Long?
    get() = (this as? ArticleScope.Category)?.groupId

/**
 * Both arguments use 0L as a "not set" sentinel because [androidx.navigation.NavType.LongType]
 * cannot be nullable. A group id wins over a feed id defensively; navigation never sets both.
 */
fun resolveArticleScope(feedIdArg: Long?, groupIdArg: Long?): ArticleScope {
    val groupId = groupIdArg?.takeIf { it != 0L }
    if (groupId != null) return ArticleScope.Category(groupId)

    val feedId = feedIdArg?.takeIf { it != 0L }
    if (feedId != null) return ArticleScope.SingleFeed(feedId)

    return ArticleScope.AllSources
}
