package co.chinho.readabilityreader.data.remote.dto

import com.google.gson.annotations.SerializedName

data class FeverResponse(
    @SerializedName("api_version") val apiVersion: Int? = null,
    @SerializedName("auth") val auth: Int,
    @SerializedName("last_refreshed_on_time") val lastRefreshedOnTime: Long? = null,
    @SerializedName("feeds") val feeds: List<FeverFeedDto>? = null,
    @SerializedName("feeds_groups") val feedsGroups: List<FeedGroupMappingDto>? = null,
    @SerializedName("groups") val groups: List<FeverGroupDto>? = null,
    @SerializedName("items") val items: List<FeverItemDto>? = null,
    @SerializedName("total_items") val totalItems: Int? = null,
    @SerializedName("unread_item_ids") val unreadItemIds: String? = null,
    @SerializedName("saved_item_ids") val savedItemIds: String? = null,
)
