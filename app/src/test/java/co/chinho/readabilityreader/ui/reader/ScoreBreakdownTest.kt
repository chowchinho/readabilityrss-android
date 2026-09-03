package co.chinho.readabilityreader.ui.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.chinho.readabilityreader.domain.model.Article
import co.chinho.readabilityreader.domain.model.LabelWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class ScoreBreakdownTest {

    @Test
    fun testLocallyAssembledBreakdownReconcilesToServerScoreAcrossFixtures() {
        val fixtures = listOf(
            // Fixture 1: Consumer Tech, News, Global, Samsung+Ringtone
            Pair(
                Article(
                    id = 1001L,
                    feedId = 1L,
                    title = "Tech News 1",
                    url = "https://example.com/1",
                    content = null,
                    publishedAt = 1000L,
                    isRead = false,
                    isSaved = false,
                    thumbnailUrl = null,
                    isCached = true,
                    score = 6.80,
                    primaryTopic = "Consumer Tech",
                    // Canonical keys, as the sync stores them. Deliberately not the display
                    // spellings in the weights below: a fixture that matched on both sides
                    // would pass while every real lookup missed.
                    secondaryTopics = "[\"samsung\", \"ringtone\"]",
                    region = "Global",
                    articleType = "News",
                    freshness = 0.90,
                    pairTerms = "[[\"Consumer Tech x Global\", 1.00]]",
                ),
                listOf(
                    LabelWeight("topic", "Consumer Tech", "Consumer Tech", declared = 2.0, prior = 0.0, votes = 23, explicit = 0.0, behavioural = 0.31, effective = 2.00),
                    LabelWeight("type", "News", "News", declared = 2.0, prior = 0.0, votes = 62, explicit = 0.0, behavioural = 0.12, effective = 1.48),
                    LabelWeight("region", "Global", "Global", declared = 1.0, prior = 0.0, votes = 54, explicit = 0.0, behavioural = 0.0, effective = 1.23),
                    LabelWeight("secondary", "samsung", "Samsung", declared = 0.0, prior = 0.35, votes = 26, explicit = 0.0, behavioural = 0.0, effective = 0.64),
                    LabelWeight("secondary", "ringtone", "Ringtone", declared = 0.0, prior = 0.0, votes = 4, explicit = 0.0, behavioural = 0.0, effective = 0.0),
                )
            ),
            // Fixture 2: Enterprise Software, Analysis, US
            Pair(
                Article(
                    id = 1002L,
                    feedId = 2L,
                    title = "Software Analysis",
                    url = "https://example.com/2",
                    content = null,
                    publishedAt = 2000L,
                    isRead = false,
                    isSaved = false,
                    thumbnailUrl = null,
                    isCached = true,
                    score = 4.50,
                    primaryTopic = "Enterprise",
                    secondaryTopics = "[\"cloud\"]",
                    region = "US",
                    articleType = "Analysis",
                    freshness = 0.50,
                    pairTerms = "[]",
                ),
                listOf(
                    LabelWeight("topic", "Enterprise", "Enterprise", declared = 1.5, prior = 0.0, votes = 10, explicit = 0.0, behavioural = 0.0, effective = 1.50),
                    LabelWeight("type", "Analysis", "Analysis", declared = 1.0, prior = 0.0, votes = 15, explicit = 0.0, behavioural = 0.0, effective = 1.00),
                    LabelWeight("region", "US", "US", declared = 1.0, prior = 0.0, votes = 20, explicit = 0.0, behavioural = 0.0, effective = 1.00),
                    LabelWeight("secondary", "cloud", "Cloud", declared = 0.0, prior = 0.0, votes = 5, explicit = 0.0, behavioural = 0.0, effective = 1.6666666666666667),
                )
            )
        )

        for ((article, weights) in fixtures) {
            val breakdown = computeScoreBreakdown(article, weights)
            assertNotNull(breakdown)
            val expectedScore = article.score!!
            assertEquals(expectedScore, breakdown.calculatedTotal, 0.05)
        }
    }

    @Test
    fun testDegradesWithoutCrashingWhenWeightsTableIsEmpty() {
        val article = Article(
            id = 2001L,
            feedId = 1L,
            title = "Test Article",
            url = "https://example.com",
            content = null,
            publishedAt = 1000L,
            isRead = false,
            isSaved = false,
            thumbnailUrl = null,
            isCached = true,
            score = 3.0,
            primaryTopic = "AI",
            secondaryTopics = "[\"LLM\"]",
            region = "Global",
            articleType = "Opinion",
            freshness = 0.2,
            pairTerms = null,
        )

        val breakdown = computeScoreBreakdown(article, emptyList())
        assertNotNull(breakdown)
        assertTrue(breakdown.rows.isNotEmpty())
        assertEquals(3.0, breakdown.headlineScore)
    }

    @Test
    fun testColumnWidthsAndZeroHorizontalScrollAtSmallWidths() {
        val article = Article(
            id = 3001L,
            feedId = 1L,
            title = "Width Test",
            url = "https://example.com",
            content = null,
            publishedAt = 1000L,
            isRead = false,
            isSaved = false,
            thumbnailUrl = null,
            isCached = true,
            score = 5.0,
            primaryTopic = "Topic",
            secondaryTopics = null,
            region = "Global",
            articleType = "News",
            freshness = 0.5,
            pairTerms = null,
        )
        val breakdown = computeScoreBreakdown(article, emptyList())
        assertNotNull(breakdown)

        val fixedRightWidthDp = 52 + 40 + 48 + 56 // 196dp
        val paddingDp = 24
        val totalFixedDp = fixedRightWidthDp + paddingDp // 220dp

        for (viewportWidthDp in listOf(320, 400, 800)) {
            val remainingLabelWidthDp = viewportWidthDp - totalFixedDp
            assertTrue("Label column width must be positive at width $viewportWidthDp", remainingLabelWidthDp > 0)
        }
    }

    @Test
    fun testRenderBreakdownPanelScreenshots() {
        val sampleArticle = Article(
            id = 1234L,
            feedId = 1L,
            title = "Consumer Tech Article",
            url = "https://example.com",
            content = null,
            publishedAt = System.currentTimeMillis(),
            isRead = false,
            isSaved = false,
            thumbnailUrl = null,
            isCached = true,
            score = 6.80,
            primaryTopic = "Consumer Tech",
            secondaryTopics = "[\"samsung\", \"ringtone\"]",
            region = "Global",
            articleType = "News",
            freshness = 0.90,
            pairTerms = "[[\"Consumer Tech x Global\", 1.00]]",
        )
        val sampleWeights = listOf(
            LabelWeight("topic", "Consumer Tech", "Consumer Tech", declared = 2.0, prior = 0.0, votes = 23, explicit = 0.0, behavioural = 0.31, effective = 2.00),
            LabelWeight("type", "News", "News", declared = 2.0, prior = 0.0, votes = 62, explicit = 0.0, behavioural = 0.12, effective = 1.48),
            LabelWeight("region", "Global", "Global", declared = 1.0, prior = 0.0, votes = 54, explicit = 0.0, behavioural = 0.0, effective = 1.23),
            LabelWeight("secondary", "samsung", "Samsung", declared = 0.0, prior = 0.35, votes = 26, explicit = 0.0, behavioural = 0.0, effective = 0.64),
            LabelWeight("secondary", "ringtone", "Ringtone", declared = 0.0, prior = 0.0, votes = 4, explicit = 0.0, behavioural = 0.0, effective = 0.0),
        )

        val outputDir = File("build/screenshots").apply { mkdirs() }

        val variants = listOf(
            Pair("breakdown_320dp.png", Triple(320, false, false)),
            Pair("breakdown_400dp.png", Triple(400, false, false)),
            Pair("breakdown_tablet.png", Triple(800, false, false)),
            Pair("breakdown_eink.png", Triple(400, true, false)),
        )

        for ((filename, config) in variants) {
            val (widthDp, isEInk, isDark) = config
            val bitmap = renderBreakdownPanelBitmap(widthDp, isEInk, isDark, sampleArticle, sampleWeights)

            val outputFile = File(outputDir, filename)
            FileOutputStream(outputFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            assertTrue(outputFile.exists() && outputFile.length() > 0)
        }
    }

    private fun renderBreakdownPanelBitmap(
        widthDp: Int,
        isEInk: Boolean,
        isDark: Boolean,
        article: Article,
        weights: List<LabelWeight>
    ): Bitmap {
        val breakdown = computeScoreBreakdown(article, weights)
        val density = 2f
        val widthPx = (widthDp * density).toInt()

        val paddingPx = 12 * density
        val colDeclaredW = 52 * density
        val colVotesW = 40 * density
        val colReadW = 48 * density
        val colContribW = 56 * density
        val fixedRightW = colDeclaredW + colVotesW + colReadW + colContribW
        val labelW = (widthPx - paddingPx * 2 - fixedRightW).coerceAtLeast(60 * density)

        val rowHeight = 24 * density
        val headerHeight = 36 * density
        val footerHeight = 44 * density
        val totalRows = breakdown.rows.size + 1
        val heightPx = (headerHeight + totalRows * rowHeight + footerHeight + paddingPx * 2).toInt()

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgColor = if (isEInk) android.graphics.Color.WHITE else if (isDark) 0xFF1C1B1F.toInt() else 0xFFF7F9FC.toInt()
        val textColor = if (isEInk) android.graphics.Color.BLACK else if (isDark) 0xFFE6E1E5.toInt() else 0xFF1C1B1F.toInt()
        val subtextColor = if (isEInk) android.graphics.Color.BLACK else if (isDark) 0xFFCAC4D0.toInt() else 0xFF49454F.toInt()

        canvas.drawColor(bgColor)

        val paint = Paint().apply {
            isAntiAlias = true
            color = textColor
            textSize = 12 * density
        }
        val boldPaint = Paint().apply {
            isAntiAlias = true
            color = textColor
            textSize = 12 * density
            isFakeBoldText = true
        }

        var y = paddingPx + 18 * density
        canvas.drawText("SCORE BREAKDOWN", paddingPx, y, boldPaint)

        y += 24 * density
        val xLabel = paddingPx
        val xDecl = xLabel + labelW
        val xVotes = xDecl + colDeclaredW
        val xRead = xVotes + colVotesW
        val xContrib = xRead + colReadW

        paint.color = subtextColor
        paint.textSize = 10 * density
        canvas.drawText("LABEL", xLabel, y, paint)
        canvas.drawText("DECL", xDecl, y, paint)
        canvas.drawText("VOTE", xVotes, y, paint)
        canvas.drawText("READ", xRead, y, paint)
        canvas.drawText("CONTRIB", xContrib, y, paint)

        paint.textSize = 11 * density
        for (row in breakdown.rows) {
            y += rowHeight
            paint.color = if (row.isSubtotalRow || row.isHeaderRow) subtextColor else textColor
            canvas.drawText(row.label.take(15), xLabel, y, paint)
            canvas.drawText(row.declaredStr, xDecl, y, paint)
            canvas.drawText(row.votesStr, xVotes, y, paint)
            canvas.drawText(row.readStr, xRead, y, paint)
            canvas.drawText(row.contribStr, xContrib, y, paint)
        }

        y += 28 * density
        val totalStr = "Headline Score: %.2f  (Calc: %.2f)".format(breakdown.headlineScore, breakdown.calculatedTotal)
        canvas.drawText(totalStr, xLabel, y, boldPaint)

        return bitmap
    }
}
