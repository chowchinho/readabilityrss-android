package co.chinho.readabilityreader.data.local.entity

import androidx.room.Entity

@Entity(
    tableName = "label_weights",
    primaryKeys = ["axis", "canonicalLabel"]
)
data class LabelWeightEntity(
    val axis: String,
    val canonicalLabel: String,
    val displayLabel: String,
    val declared: Double,
    val prior: Double,
    val votes: Int,
    val explicit: Double,
    val behavioural: Double,
    val effective: Double,
)
