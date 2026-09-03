package co.chinho.readabilityreader.data.repository

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import co.chinho.readabilityreader.domain.repository.CredentialRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CredentialRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : CredentialRepository {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "fever_credentials",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override suspend fun savePassword(profileId: Long, password: String) {
        prefs.edit().putString("password_$profileId", password).apply()
    }

    override suspend fun getPassword(profileId: Long): String? {
        return prefs.getString("password_$profileId", null)
    }

    override suspend fun deletePassword(profileId: Long) {
        prefs.edit().remove("password_$profileId").apply()
    }

    override suspend fun saveJottyApiKey(key: String) {
        prefs.edit().putString(JOTTY_API_KEY, key).apply()
    }

    override suspend fun getJottyApiKey(): String? {
        return prefs.getString(JOTTY_API_KEY, null)
    }

    override suspend fun deleteJottyApiKey() {
        prefs.edit().remove(JOTTY_API_KEY).apply()
    }

    private companion object {
        const val JOTTY_API_KEY = "jotty_api_key"
    }
}
