package co.chinho.readabilityreader.data.remote.dto

import com.google.gson.annotations.SerializedName

data class FeverItemDto(
    @SerializedName("id") val id: Long,
    @SerializedName("feed_id") val feedId: Long,
    @SerializedName("title") val title: String,
    // Deserialised from server but not yet displayed in the UI.
    @SerializedName("author") val author: String? = null,
    @SerializedName("url") val url: String,
    @SerializedName("html") val html: String? = null,
    @SerializedName("main_image") val mainImage: String? = null,
    @SerializedName("main_image_proxy") val mainImageProxy: String? = null,
    @SerializedName("is_saved") val isSaved: Int? = null,
    @SerializedName("is_read") val isRead: Int? = null,
    @SerializedName("created_on_time") val createdOnTime: Long? = null,
)
