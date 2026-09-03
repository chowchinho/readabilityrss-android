package co.chinho.readabilityreader.data.remote.dto

import com.google.gson.JsonArray
import com.google.gson.annotations.SerializedName

data class RankingScoresResponse(
    @SerializedName("scores") val scores: List<JsonArray>? = null,
    @SerializedName("extras") val extras: Map<String, RankingExtraDto>? = null,
    @SerializedName("tags") val tags: List<RankingTagDto>? = null,
    @SerializedName("weights") val weights: Map<String, List<LabelWeightDto>>? = null,
    @SerializedName("ai_enabled") val aiEnabled: Boolean = true,
    @SerializedName("generated_at") val generatedAt: String? = null,
)

data class RankingExtraDto(
    @SerializedName("freshness") val freshness: Double? = null,
    @SerializedName("pairs") val pairs: List<JsonArray>? = null,
)

data class RankingTagDto(
    @SerializedName("id") val id: Long,
    @SerializedName("primary") val primary: String? = null,
    @SerializedName("secondary") val secondary: List<String>? = null,
    @SerializedName("secondary_canonical") val secondaryCanonical: List<String>? = null,
    @SerializedName("region") val region: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("vote") val vote: String? = null,
)

data class LabelWeightDto(
    // Only the secondary axis carries a separate canonical key; topic, type and region send
    // `label` alone, so a row keyed on `canonical` being present drops three axes entirely.
    @SerializedName("label") val label: String? = null,
    @SerializedName("canonical") val canonical: String? = null,
    @SerializedName("declared") val declared: Double = 0.0,
    @SerializedName("prior") val prior: Double = 0.0,
    @SerializedName("votes") val votes: Int = 0,
    @SerializedName("explicit") val explicit: Double = 0.0,
    @SerializedName("behavioural") val behavioural: Double = 0.0,
    @SerializedName("effective") val effective: Double = 0.0,
)
