package co.chinho.readabilityreader.data.remote

import co.chinho.readabilityreader.data.remote.dto.RankingFeedbackRequest
import co.chinho.readabilityreader.data.remote.dto.RankingFeedbackResponse
import co.chinho.readabilityreader.data.remote.dto.RankingScoresResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface RankingApiService {
    @GET("api/reader/ranking/scores")
    suspend fun getScores(
        @Query("api_key") apiKey: String,
        @Query("since_id") sinceId: Long? = null,
        @Query("since") since: String? = null,
    ): RankingScoresResponse

    @POST("api/reader/ranking/feedback")
    suspend fun sendFeedback(
        @Body request: RankingFeedbackRequest,
    ): RankingFeedbackResponse
}
