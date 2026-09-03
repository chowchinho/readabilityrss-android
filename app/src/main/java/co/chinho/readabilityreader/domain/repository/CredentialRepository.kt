package co.chinho.readabilityreader.domain.repository

interface CredentialRepository {
    suspend fun savePassword(profileId: Long, password: String)
    suspend fun getPassword(profileId: Long): String?
    suspend fun deletePassword(profileId: Long)

    suspend fun saveJottyApiKey(key: String)
    suspend fun getJottyApiKey(): String?
    suspend fun deleteJottyApiKey()
}
