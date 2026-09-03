package co.chinho.readabilityreader.data.remote.dto

import com.google.gson.annotations.SerializedName

data class FeverGroupDto(
    @SerializedName("id") val id: Long,
    @SerializedName("title") val title: String,
)
