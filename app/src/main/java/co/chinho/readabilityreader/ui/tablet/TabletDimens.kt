package co.chinho.readabilityreader.ui.tablet

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

object TabletDimens {
    val FeedPaneExpandedWidth = 280.dp
    val ArticleListMinDp = 180.dp
    val ReaderMinDp = 240.dp
    val TabletBreakpointDp = 720.dp

    const val ArticleListMinFraction = 0.20f
    const val ArticleListMaxFraction = 0.70f
    const val ArticleListDefaultFraction = 0.33f
}

@Composable
fun isTabletWidth(): Boolean =
    LocalConfiguration.current.screenWidthDp.dp >= TabletDimens.TabletBreakpointDp
