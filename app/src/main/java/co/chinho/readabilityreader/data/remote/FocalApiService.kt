package co.chinho.readabilityreader.data.remote

import co.chinho.readabilityreader.data.remote.dto.FocalPointsRequest
import co.chinho.readabilityreader.data.remote.dto.FocalPointsResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface FocalApiService {
    @POST("api/reader/focal-points")
    suspend fun getFocalPoints(
        @Body request: FocalPointsRequest,
    ): FocalPointsResponse
}
