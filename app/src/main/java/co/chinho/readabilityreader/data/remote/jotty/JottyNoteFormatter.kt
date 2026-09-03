package co.chinho.readabilityreader.data.remote.jotty

import co.chinho.readabilityreader.domain.model.Article
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

data class JottyNote(
    val title: String,
    val content: String,
    val category: String,
)

@Singleton
class JottyNoteFormatter @Inject constructor(
    private val markdownConverter: JottyMarkdownConverter,
) {
    fun format(article: Article, category: String): JottyNote {
        val date = formatDate(article.publishedAt)
        val title = "$date — ${article.title.trim()}"

        val source = article.feedTitle?.trim()?.takeIf { it.isNotBlank() } ?: "Unknown source"
        val url = article.url.trim().takeIf { it.isNotBlank() } ?: "(no URL)"

        val headerBlock = buildString {
            append("> **Source:** ")
            append(source)
            append("\n")
            append("> **Original URL:** ")
            append(url)
            append("\n\n---\n\n")
        }

        val bodyMarkdown = article.content
            ?.takeIf { it.isNotBlank() }
            ?.let { markdownConverter.convert(it) }
            ?.takeIf { it.isNotBlank() }
            ?: "_No content cached._"

        return JottyNote(
            title = title,
            content = headerBlock + bodyMarkdown,
            category = category.trim().ifBlank { "ReadabilityAndroid" },
        )
    }

    private fun formatDate(epochMs: Long): String {
        return runCatching {
            val instant = if (epochMs > 0) Instant.ofEpochMilli(epochMs) else Instant.now()
            DATE_FORMATTER.format(instant.atZone(ZoneId.systemDefault()).toLocalDate())
        }.getOrElse { DATE_FORMATTER.format(LocalDate.now()) }
    }

    private companion object {
        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }
}
