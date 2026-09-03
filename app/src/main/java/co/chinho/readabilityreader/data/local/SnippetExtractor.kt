package co.chinho.readabilityreader.data.local

object SnippetExtractor {
    const val MAX_VISIBLE_CHARS = 300

    private val TRANSLATION_NOTICE_REGEX =
        Regex("<p[^>]*>\\s*(?:🌐\\s*)?Translated by [^<]*</p>", RegexOption.IGNORE_CASE)
    private val TAG_REGEX = Regex("<[^>]+>")
    private val TRAILING_PARTIAL_TAG_REGEX = Regex("<[^>]*$")
    private val WHITESPACE_REGEX = Regex("\\s+")

    fun extract(content: String?): String? = content
        ?.replace(TRANSLATION_NOTICE_REGEX, "")
        ?.replace(TAG_REGEX, " ")
        ?.replace(TRAILING_PARTIAL_TAG_REGEX, "")
        ?.replace(WHITESPACE_REGEX, " ")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.take(MAX_VISIBLE_CHARS)
}
