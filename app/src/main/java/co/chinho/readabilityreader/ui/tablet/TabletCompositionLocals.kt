package co.chinho.readabilityreader.ui.tablet

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp

/**
 * Width of the article-list pane in the tablet three-pane shell, or null when
 * the surrounding content is not inside that pane (phone navigation, tablet
 * A-view right pane, etc.). Used by article rows to opt into tablet-specific
 * spacing and to size the thumbnail relative to pane width.
 */
val LocalTabletPaneWidth = compositionLocalOf<Dp?> { null }
