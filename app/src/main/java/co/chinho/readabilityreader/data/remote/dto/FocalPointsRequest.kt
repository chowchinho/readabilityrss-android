package co.chinho.readabilityreader.data.remote.dto

import com.google.gson.annotations.SerializedName

data class FocalPointsRequest(
    @SerializedName("api_key") val apiKey: String,
    @SerializedName("hashes") val hashes: List<String>,
)
