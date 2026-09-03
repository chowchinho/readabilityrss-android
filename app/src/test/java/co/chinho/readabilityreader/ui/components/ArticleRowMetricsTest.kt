package co.chinho.readabilityreader.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleRowMetricsTest {

    @Test
    fun textBlockDrivesHeightWhenTallerThanImage() {
        val height = computeArticleRowHeight(
            titleLineHeight = 20.8.dp,
            snippetLineHeight = 19.6.dp,
            feedLabelLineHeight = 14.85.dp,
            imageSize = 125.dp,
        )

        // 20.8*2 + 3 + 19.6*3 + 6 + max(16, 14.85) = 125.4 ; beats the 125dp image by 0.4 ; + 24
        assertEquals(149.4f, height.value, 0.05f)
    }

    @Test
    fun imageDrivesHeightWhenTallerThanTextBlock() {
        val height = computeArticleRowHeight(
            titleLineHeight = 10.dp,
            snippetLineHeight = 10.dp,
            feedLabelLineHeight = 10.dp,
            imageSize = 120.dp,
        )

        // text block = 10*2 + 3 + 10*3 + 6 + 16 = 75 ; image wins
        assertEquals(144f, height.value, 0.05f)
    }

    @Test
    fun feedRowUsesFaviconAsFloor() {
        val tinyLabel = computeArticleRowHeight(20.dp, 20.dp, 4.dp, 0.dp)
        val faviconSizedLabel = computeArticleRowHeight(20.dp, 20.dp, 16.dp, 0.dp)

        assertEquals(faviconSizedLabel.value, tinyLabel.value, 0.05f)
    }

    @Test
    fun feedRowGrowsWhenLabelExceedsFavicon() {
        val faviconSized = computeArticleRowHeight(20.dp, 20.dp, 16.dp, 0.dp)
        val tallLabel = computeArticleRowHeight(20.dp, 20.dp, 22.dp, 0.dp)

        assertEquals(6f, (tallLabel - faviconSized).value, 0.05f)
    }

    @Test
    fun heightGrowsMonotonicallyWithFontSize() {
        var previous = 0f
        for (fontSizeSp in 12..24) {
            val height = computeArticleRowHeight(
                titleLineHeight = (fontSizeSp * 1.30f).dp,
                snippetLineHeight = ((fontSizeSp - 2).coerceAtLeast(10) * 1.40f).dp,
                feedLabelLineHeight = (11 * 1.35f).dp,
                imageSize = 0.dp,
            )
            assertTrue("height must not shrink at ${fontSizeSp}sp", height.value >= previous)
            previous = height.value
        }
    }

    @Test
    fun phoneThumbnailIsFixedAtPhoneSize() {
        assertEquals(125f, computeArticleThumbnailSize(null, 16.dp).value, 0.05f)
    }

    @Test
    fun tabletThumbnailIsClampedUpAtNarrowPane() {
        // content = 180 - 4 - 12 - 0 - 12 = 152 ; 33% = 50.16 -> floor 64
        assertEquals(64f, computeArticleThumbnailSize(180.dp, 0.dp).value, 0.05f)
    }

    @Test
    fun tabletThumbnailIsClampedDownAtWidePane() {
        // content = 450 - 4 - 12 - 0 - 12 = 422 ; 33% = 139.26 -> ceiling 125
        assertEquals(125f, computeArticleThumbnailSize(450.dp, 0.dp).value, 0.05f)
    }

    @Test
    fun tabletThumbnailScalesProportionallyBetweenClamps() {
        // content = 300 - 4 - 12 - 0 - 12 = 272 ; 33% = 89.76
        assertEquals(89.76f, computeArticleThumbnailSize(300.dp, 0.dp).value, 0.05f)
    }
}
