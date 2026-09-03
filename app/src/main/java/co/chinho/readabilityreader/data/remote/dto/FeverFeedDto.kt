package co.chinho.readabilityreader.data.remote.dto

import com.google.gson.annotations.SerializedName

data class FeverFeedDto(
    @SerializedName("id") val id: Long,
    @SerializedName("favicon_id") val faviconId: Long? = null,
    @SerializedName("title") val title: String,
    @SerializedName("url") val url: String,
    @SerializedName("site_url") val siteUrl: String? = null,
    @SerializedName("favicon") val favicon: String? = null,
    @SerializedName("favicon_proxy") val faviconProxy: String? = null,
)
