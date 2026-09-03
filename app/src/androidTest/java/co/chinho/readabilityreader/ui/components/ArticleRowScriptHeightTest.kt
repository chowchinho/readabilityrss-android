package co.chinho.readabilityreader.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.chinho.readabilityreader.domain.model.Article
import co.chinho.readabilityreader.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val ROW_TAG = "article_row_under_test"

@RunWith(AndroidJUnit4::class)
class ArticleRowScriptHeightTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun article(title: String, content: String?, withThumbnail: Boolean = false) = Article(
        id = 1L,
        feedId = 10L,
        title = title,
        url = "https://example.com/a",
        content = content,
        publishedAt = System.currentTimeMillis(),
        isRead = false,
        isSaved = false,
        thumbnailUrl = if (withThumbnail) "https://example.com/a.jpg" else null,
        isCached = true,
        feedTitle = "Test Feed",
        feedFaviconUrl = null,
    )

    private fun measure(
        title: String,
        content: String?,
        fontSizeSp: Int = 16,
        fontScale: Float = 1.0f,
        withThumbnail: Boolean = false,
    ): Dp {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = fontScale)
            ) {
                AppTheme(isDarkTheme = true) {
                    Box(modifier = Modifier.testTag(ROW_TAG)) {
                        ArticleRow(
                            article = article(title, content, withThumbnail),
                            onClick = {},
                            showFeedMetadata = true,
                            fontSizeSp = fontSizeSp,
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        return composeRule.onNodeWithTag(ROW_TAG).getUnclippedBoundsInRoot().height
    }

    private val english = Fixture(
        title = "iFixit's Galaxy Z Fold8 Teardown is Preview of Apple's Foldable Challenge",
        content = "Samsung launched new foldable smartphones earlier this month, including the " +
            "Galaxy Z Fold8. If you did not get a chance to look inside one yet, iFixit has now " +
            "published its full teardown of the device.",
    )

    private val chinese = Fixture(
        title = "【特集】台場「1:1獨角獸高達立像」本月最後機會！與Gundam Base限定R",
        content = "實物大獨角獸高達立像的展示期間即將結束，官方宣布本月為最後參觀機會，" +
            "並同步推出多款限定商品，吸引大量粉絲前往台場朝聖留念。",
    )

    private val japanese = Fixture(
        title = "実物大ユニコーンガンダム立像の展示期間が終了へ、台場で最後の機会",
        content = "2026年8月14日 00:00 【実物大ユニコーンガンダム立像】展示期間：" +
            "2017年9月24日〜2026年8月31日。会場では限定グッズの販売も予定されています。",
    )

    private val mixed = Fixture(
        title = "Apple iPhone Ultra Foldable 名稱與9月發表：ついに登場か",
        content = "Cheng Xin/Getty Images 據報導、Apple is preparing to launch its first " +
            "foldable device 、業界關注度極高。",
    )

    private data class Fixture(val title: String, val content: String)

    private fun assertAllScriptsMatch(fontSizeSp: Int, fontScale: Float) {
        val baseline = measure(english.title, english.content, fontSizeSp, fontScale)
        for ((name, fixture) in listOf(
            "chinese" to chinese,
            "japanese" to japanese,
            "mixed" to mixed,
        )) {
            val actual = measure(fixture.title, fixture.content, fontSizeSp, fontScale)
            assertEquals(
                "$name height differs at ${fontSizeSp}sp / fontScale $fontScale",
                baseline.value,
                actual.value,
                0.5f,
            )
        }
    }

    @Test
    fun allScriptsHaveEqualHeightAtDefaultSettings() {
        assertAllScriptsMatch(fontSizeSp = 16, fontScale = 1.0f)
    }

    @Test
    fun allScriptsHaveEqualHeightAtSmallestFont() {
        assertAllScriptsMatch(fontSizeSp = 12, fontScale = 0.85f)
    }

    @Test
    fun allScriptsHaveEqualHeightAtLargestFont() {
        assertAllScriptsMatch(fontSizeSp = 24, fontScale = 1.3f)
    }

    @Test
    fun shortAndLongContentHaveEqualHeight() {
        val full = measure(english.title, english.content)
        val oneLineTitleNoSnippet = measure("Short", null)

        assertEquals(full.value, oneLineTitleNoSnippet.value, 0.5f)
    }

    @Test
    fun thumbnaillessRowMatchesThumbnailedRow() {
        val withThumb = measure(english.title, english.content, withThumbnail = true)
        val withoutThumb = measure(english.title, english.content, withThumbnail = false)

        assertEquals(withThumb.value, withoutThumb.value, 0.5f)
    }
}
