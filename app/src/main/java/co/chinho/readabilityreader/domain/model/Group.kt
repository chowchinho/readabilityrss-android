package co.chinho.readabilityreader.domain.model

data class Group(
    val id: Long,
    val title: String,
    val feeds: List<Feed>,
)
