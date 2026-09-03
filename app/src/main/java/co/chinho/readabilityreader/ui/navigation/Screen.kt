package co.chinho.readabilityreader.ui.navigation

sealed class Screen(val route: String) {
    data object FeedList : Screen("feed_list")
    
    data object ArticleList : Screen("article_list/{feedId}?groupId={groupId}") {
        const val NAV_ARG_FEED_ID = "feedId"
        const val NAV_ARG_GROUP_ID = "groupId"
        fun createRoute(feedId: Long) = "article_list/$feedId?groupId=0"
        fun createRouteForGroup(groupId: Long) = "article_list/0?groupId=$groupId"
    }
    
    data object ArticleReader : Screen("article_reader/{articleId}?feedId={feedId}&isSaved={isSaved}") {
        const val NAV_ARG_ARTICLE_ID = "articleId"
        const val NAV_ARG_FEED_ID = "feedId"
        const val NAV_ARG_IS_SAVED = "isSaved"
        fun createRoute(articleId: Long, feedId: Long? = null, isSaved: Boolean = false) =
            "article_reader/$articleId" +
                    "?feedId=${feedId ?: 0L}" +
                    "&isSaved=$isSaved"
    }
    
    data object SavedArticles : Screen("saved_articles")
    data object Settings : Screen("settings")
    data object ServerSetup : Screen("server_setup")
}
