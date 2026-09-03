package co.chinho.readabilityreader.domain.model

data class ServerProfile(
    val id: Long = 0,
    val name: String,
    val serverUrl: String,
    val username: String,
    val isActive: Boolean,
)
