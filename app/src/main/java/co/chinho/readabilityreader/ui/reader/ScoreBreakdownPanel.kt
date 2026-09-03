package co.chinho.readabilityreader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.chinho.readabilityreader.domain.model.Article
import co.chinho.readabilityreader.domain.model.LabelWeight
import co.chinho.readabilityreader.ui.theme.AppTheme
import co.chinho.readabilityreader.ui.theme.LocalEInkMode
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlin.math.abs

data class ScoreBreakdownRow(
    val label: String,
    val declaredStr: String = "—",
    val isPrior: Boolean = false,
    val votesStr: String = "—",
    val readStr: String = "—",
    val contribStr: String = "—",
    val isSecondaryRow: Boolean = false,
    val isSubtotalRow: Boolean = false,
    val isHeaderRow: Boolean = false,
)

data class ScoreBreakdownData(
    val headlineScore: Double?,
    val rows: List<ScoreBreakdownRow>,
    val hasPriorInherited: Boolean,
    val calculatedTotal: Double,
)

fun computeScoreBreakdown(
    article: Article,
    labelWeights: List<LabelWeight>,
): ScoreBreakdownData {
    val weightsMap = labelWeights.associateBy { Pair(it.axis, it.canonicalLabel) }
    val rows = mutableListOf<ScoreBreakdownRow>()
    var hasPriorInherited = false
    var totalSum = 0.0

    fun formatContrib(value: Double): String {
        return if (value > 0) "+%.2f".format(value) else "%.2f".format(value)
    }

    fun formatRead(value: Double): String {
        return when {
            abs(value) < 0.001 -> "—"
            value > 0 -> "+%.2f".format(value)
            else -> "%.2f".format(value)
        }
    }

    fun formatVotes(votes: Int): String {
        return if (votes == 0) "—" else votes.toString()
    }

    fun formatDeclared(declared: Double, prior: Double): Pair<String, Boolean> {
        return if (declared == 0.0 && abs(prior) > 0.001) {
            val priorStr = if (prior > 0) "+%.2f".format(prior) else "%.2f".format(prior)
            Pair("^$priorStr", true)
        } else if (abs(declared) < 0.001) {
            Pair("0.0", false)
        } else {
            val declStr = if (declared > 0) "+%.1f".format(declared) else "%.1f".format(declared)
            Pair(declStr, false)
        }
    }

    var primaryTopicEffective = 0.0
    var hasPrimaryTopic = false

    // 1. Primary Topic
    if (!article.primaryTopic.isNullOrBlank()) {
        hasPrimaryTopic = true
        val weight = weightsMap[Pair("topic", article.primaryTopic)]
        val effective = weight?.effective ?: 0.0
        primaryTopicEffective = effective
        totalSum += effective

        val (declStr, isPrior) = formatDeclared(weight?.declared ?: 0.0, weight?.prior ?: 0.0)
        if (isPrior) hasPriorInherited = true

        rows.add(
            ScoreBreakdownRow(
                label = "Topic: ${weight?.displayLabel ?: article.primaryTopic}",
                declaredStr = declStr,
                isPrior = isPrior,
                votesStr = formatVotes(weight?.votes ?: 0),
                readStr = formatRead((weight?.explicit ?: 0.0) + (weight?.behavioural ?: 0.0)),
                contribStr = formatContrib(effective),
            )
        )
    }

    // 2. Article Type
    if (!article.articleType.isNullOrBlank()) {
        val weight = weightsMap[Pair("type", article.articleType)]
        val effective = weight?.effective ?: 0.0
        totalSum += effective

        val (declStr, isPrior) = formatDeclared(weight?.declared ?: 0.0, weight?.prior ?: 0.0)
        if (isPrior) hasPriorInherited = true

        rows.add(
            ScoreBreakdownRow(
                label = "Type: ${weight?.displayLabel ?: article.articleType}",
                declaredStr = declStr,
                isPrior = isPrior,
                votesStr = formatVotes(weight?.votes ?: 0),
                readStr = formatRead((weight?.explicit ?: 0.0) + (weight?.behavioural ?: 0.0)),
                contribStr = formatContrib(effective),
            )
        )
    }

    // 3. Region
    if (!article.region.isNullOrBlank()) {
        val weight = weightsMap[Pair("region", article.region)]
        val effective = weight?.effective ?: 0.0
        totalSum += effective

        val (declStr, isPrior) = formatDeclared(weight?.declared ?: 0.0, weight?.prior ?: 0.0)
        if (isPrior) hasPriorInherited = true

        rows.add(
            ScoreBreakdownRow(
                label = "Region: ${weight?.displayLabel ?: article.region}",
                declaredStr = declStr,
                isPrior = isPrior,
                votesStr = formatVotes(weight?.votes ?: 0),
                readStr = formatRead((weight?.explicit ?: 0.0) + (weight?.behavioural ?: 0.0)),
                contribStr = formatContrib(effective),
            )
        )
    }

    // 4. Secondary Topics
    if (!article.secondaryTopics.isNullOrBlank()) {
        val secondaries = runCatching {
            val type = object : TypeToken<List<String>>() {}.type
            Gson().fromJson<List<String>>(article.secondaryTopics, type)
        }.getOrNull().orEmpty()

        if (secondaries.isNotEmpty()) {
            val unclampedSum = secondaries.sumOf { sec ->
                weightsMap[Pair("secondary", sec)]?.effective ?: 0.0
            }
            val unclampedWeighted = unclampedSum * 0.3

            rows.add(
                ScoreBreakdownRow(
                    label = "Also x0.3",
                    contribStr = "(%s)".format(formatContrib(unclampedWeighted)),
                    isHeaderRow = true,
                )
            )

            for (sec in secondaries) {
                val weight = weightsMap[Pair("secondary", sec)]
                val (declStr, isPrior) = formatDeclared(weight?.declared ?: 0.0, weight?.prior ?: 0.0)
                if (isPrior) hasPriorInherited = true

                rows.add(
                    ScoreBreakdownRow(
                        label = "  ${weight?.displayLabel ?: sec}",
                        declaredStr = declStr,
                        isPrior = isPrior,
                        votesStr = formatVotes(weight?.votes ?: 0),
                        readStr = formatRead((weight?.explicit ?: 0.0) + (weight?.behavioural ?: 0.0)),
                        contribStr = "—",
                        isSecondaryRow = true,
                    )
                )
            }

            val topicMag = if (hasPrimaryTopic) abs(primaryTopicEffective) else 999.0
            val clampedContrib = unclampedWeighted.coerceIn(-topicMag, topicMag)
            totalSum += clampedContrib

            rows.add(
                ScoreBreakdownRow(
                    label = "  subtotal, clamped, x0.3",
                    contribStr = formatContrib(clampedContrib),
                    isSubtotalRow = true,
                )
            )
        }
    }

    // 5. Pairs
    if (!article.pairTerms.isNullOrBlank()) {
        val pairs = runCatching {
            val type = object : TypeToken<List<List<Any>>>() {}.type
            Gson().fromJson<List<List<Any>>>(article.pairTerms, type)
        }.getOrNull().orEmpty()

        for (pair in pairs) {
            if (pair.size >= 2) {
                val pairName = pair[0].toString()
                val scoreVal = (pair[1] as? Number)?.toDouble() ?: 0.0
                totalSum += scoreVal

                rows.add(
                    ScoreBreakdownRow(
                        label = "Pairs: $pairName",
                        contribStr = formatContrib(scoreVal),
                    )
                )
            }
        }
    }

    // 6. Freshness
    if (article.freshness != null) {
        val freshnessVal = article.freshness
        totalSum += freshnessVal

        rows.add(
            ScoreBreakdownRow(
                label = "Freshness",
                contribStr = formatContrib(freshnessVal),
            )
        )
    }

    return ScoreBreakdownData(
        headlineScore = article.score,
        rows = rows,
        hasPriorInherited = hasPriorInherited,
        calculatedTotal = totalSum,
    )
}

@Composable
fun ScoreBreakdownPanel(
    article: Article,
    labelWeights: List<LabelWeight>,
    modifier: Modifier = Modifier,
) {
    val isEInk = LocalEInkMode.current
    val data = remember(article, labelWeights) { computeScoreBreakdown(article, labelWeights) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = if (isEInk) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    text = "declared",
                    modifier = Modifier.width(52.dp),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "votes",
                    modifier = Modifier.width(40.dp),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "read",
                    modifier = Modifier.width(48.dp),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "",
                    modifier = Modifier.width(56.dp),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                thickness = 1.dp,
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Data Rows
            for (row in data.rows) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = row.label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = if (row.isHeaderRow || row.isSubtotalRow) FontWeight.Medium else FontWeight.Normal,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = row.declaredStr,
                        modifier = Modifier.width(52.dp),
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = row.votesStr,
                        modifier = Modifier.width(40.dp),
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = row.readStr,
                        modifier = Modifier.width(48.dp),
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = row.contribStr,
                        modifier = Modifier.width(56.dp),
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = if (row.isSubtotalRow) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                thickness = 1.dp,
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Total Row
            val totalValue = data.headlineScore ?: data.calculatedTotal
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Total",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "%.2f".format(totalValue),
                    modifier = Modifier.width(56.dp),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            if (data.hasPriorInherited) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "^ Inherited prior weight",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Score Breakdown Panel")
@Composable
private fun ScoreBreakdownPanelPreview() {
    AppTheme {
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
            secondaryTopics = "[\"Samsung\", \"Ringtone\"]",
            region = "Global",
            articleType = "News",
            freshness = 0.90,
            pairTerms = "[[\"Consumer Tech x Global\", 1.00]]",
        )
        val sampleWeights = listOf(
            LabelWeight("topic", "Consumer Tech", "Consumer Tech", declared = 2.0, prior = 0.0, votes = 23, explicit = 0.0, behavioural = 0.31, effective = 2.00),
            LabelWeight("type", "News", "News", declared = 2.0, prior = 0.0, votes = 62, explicit = 0.0, behavioural = 0.12, effective = 1.48),
            LabelWeight("region", "Global", "Global", declared = 1.0, prior = 0.0, votes = 54, explicit = 0.0, behavioural = 0.0, effective = 1.23),
            LabelWeight("secondary", "Samsung", "Samsung", declared = 0.0, prior = 0.35, votes = 26, explicit = 0.0, behavioural = 0.0, effective = 0.35),
            LabelWeight("secondary", "Ringtone", "Ringtone", declared = 0.0, prior = 0.0, votes = 4, explicit = 0.0, behavioural = 0.0, effective = 0.0),
        )

        ScoreBreakdownPanel(
            article = sampleArticle,
            labelWeights = sampleWeights,
            modifier = Modifier.padding(16.dp),
        )
    }
}
