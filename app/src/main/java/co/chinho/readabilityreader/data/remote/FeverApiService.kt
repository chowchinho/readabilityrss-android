package co.chinho.readabilityreader.data.remote

import co.chinho.readabilityreader.data.remote.dto.FeverResponse
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import retrofit2.http.Query

interface FeverApiService {
    @FormUrlEncoded
    @POST("?api")
    suspend fun query(
        @Field("api_key") apiKey: String,
        @Query("groups") groups: String? = null,
        @Query("feeds") feeds: String? = null,
        @Query("items") items: String? = null,
        @Query("since_id") sinceId: Long? = null,
        @Query("max_id") maxId: Long? = null,
        @Query("unread_item_ids") unreadItemIds: String? = null,
        @Query("saved_item_ids") savedItemIds: String? = null,
        @Query("favicons") favicons: String? = null,
        @Field("mark") mark: String? = null,
        @Field("as") actionState: String? = null,
        @Field("id") id: Long? = null,
        @Field("before") before: Long? = null,
    ): FeverResponse
}
