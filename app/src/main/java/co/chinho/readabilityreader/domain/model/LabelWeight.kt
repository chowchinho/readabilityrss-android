package co.chinho.readabilityreader.domain.model

data class LabelWeight(
    val axis: String,
    val canonicalLabel: String,
    val displayLabel: String,
    val declared: Double,
    val prior: Double,
    val votes: Int,
    val explicit: Double,
    val behavioural: Double,
    val effective: Double,
)
