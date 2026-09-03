package co.chinho.readabilityreader.data.remote

object CachedImageHash {
    private val regex = Regex("cached-image/([0-9a-fA-F]+)", RegexOption.IGNORE_CASE)

    fun fromUrl(url: String): String? {
        val match = regex.find(url) ?: return null
        return match.groupValues[1].lowercase()
    }
}
