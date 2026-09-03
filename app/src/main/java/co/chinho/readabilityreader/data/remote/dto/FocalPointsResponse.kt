package co.chinho.readabilityreader.data.remote.dto

import com.google.gson.annotations.SerializedName

data class FocalPointsResponse(
    @SerializedName("focal") val focal: Map<String, List<Int>>? = null,
)
