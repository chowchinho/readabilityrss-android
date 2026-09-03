package co.chinho.readabilityreader.domain.model

data class CacheStats(
    val articleCount: Int,
    val imageCount: Int,
    val databaseBytes: Long,
    val imageCacheUsedBytes: Long,
    val imageCacheMaxBytes: Long,
) {
    val totalUsedBytes: Long get() = databaseBytes + imageCacheUsedBytes
}
