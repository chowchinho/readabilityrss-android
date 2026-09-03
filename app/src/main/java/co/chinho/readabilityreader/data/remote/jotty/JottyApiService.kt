package co.chinho.readabilityreader.data.remote.jotty

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface JottyApiService {
    @POST("api/notes")
    suspend fun createNote(
        @Header("x-api-key") apiKey: String,
        @Body body: CreateNoteRequest,
    ): Response<CreateNoteResponse>

    @GET("api/categories")
    suspend fun listCategories(
        @Header("x-api-key") apiKey: String,
    ): Response<CategoriesResponse>
}
