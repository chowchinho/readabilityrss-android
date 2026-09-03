package co.chinho.readabilityreader.data.remote.dto

import com.google.gson.annotations.SerializedName

data class RankingFeedbackRequest(
    @SerializedName("api_key") val apiKey: String,
    @SerializedName("votes") val votes: List<RankingVoteDto>? = null,
    @SerializedName("events") val events: List<RankingEventDto>? = null,
)

data class RankingVoteDto(
    @SerializedName("article_id") val articleId: Long,
    @SerializedName("vote") val vote: String? = null,
    @SerializedName("mark_read") val markRead: Boolean = false,
)

data class RankingEventDto(
    @SerializedName("article_id") val articleId: Long,
    @SerializedName("event_type") val eventType: String,
    @SerializedName("dwell_seconds") val dwellSeconds: Int? = null,
)

data class RankingFeedbackResponse(
    @SerializedName("accepted_votes") val acceptedVotes: List<Long>? = null,
    @SerializedName("accepted_events") val acceptedEvents: List<Long>? = null,
    @SerializedName("status") val status: String? = null,
)
