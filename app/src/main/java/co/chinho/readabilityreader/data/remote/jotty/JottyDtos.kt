package co.chinho.readabilityreader.data.remote.jotty

import com.google.gson.annotations.SerializedName

data class CreateNoteRequest(
    val title: String,
    val content: String,
    val category: String,
)

data class CreateNoteResponse(
    val success: Boolean?,
    val data: CreateNoteData?,
)

data class CreateNoteData(
    val id: String?,
    val title: String?,
    val category: String?,
)

data class CategoriesResponse(
    val success: Boolean?,
    @SerializedName("data") val data: List<CategoryDto>?,
)

data class CategoryDto(
    val name: String?,
    val path: String?,
)
