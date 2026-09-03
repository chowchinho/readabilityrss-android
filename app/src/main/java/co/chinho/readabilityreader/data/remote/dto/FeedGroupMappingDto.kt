package co.chinho.readabilityreader.data.remote.dto

import com.google.gson.annotations.SerializedName

data class FeedGroupMappingDto(
    @SerializedName("group_id") val groupId: Long,
    @SerializedName("feed_ids") val feedIds: String,
)
