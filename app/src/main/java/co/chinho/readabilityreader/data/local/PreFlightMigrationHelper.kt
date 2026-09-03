package co.chinho.readabilityreader.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import co.chinho.readabilityreader.data.remote.ApiKeyHasher
import co.chinho.readabilityreader.data.remote.FeverApiService
import co.chinho.readabilityreader.domain.repository.CredentialRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Inject
import javax.inject.Singleton

data class LegacyQueuedAction(val id: Long, val articleId: Long, val action: String)
data class LegacyServerProfile(val id: Long, val serverUrl: String, val username: String)

sealed interface PreFlightState {
    object Checking : PreFlightState
    object Acknowledged : PreFlightState
    data class UpgradePending(
        val profile: LegacyServerProfile,
        val actions: List<LegacyQueuedAction>
    ) : PreFlightState
    object Flushing : PreFlightState
    data class FlushFailed(
        val message: String,
        val profile: LegacyServerProfile,
        val actions: List<LegacyQueuedAction>
    ) : PreFlightState
}

sealed interface FlushResult {
    object Success : FlushResult
    data class Partial(val remaining: Int) : FlushResult
    data class Failure(val error: Throwable) : FlushResult
}

// SPEC AMBIGUITY: JottyQueueEntity / JottyQueueDao is mentioned in the spec but does not exist in this branch. Proceeding with ReadStateQueue only.
@Singleton
class PreFlightMigrationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val credentialRepository: CredentialRepository,
    private val apiKeyHasher: ApiKeyHasher,
    private val okHttpClient: OkHttpClient,
) {
    private val sharedPrefs = context.getSharedPreferences("readability_upgrade_prefs", Context.MODE_PRIVATE)

    suspend fun checkUpgradePending(): PreFlightState = withContext(Dispatchers.IO) {
        val dbFile = context.getDatabasePath("readability.db")
        if (!dbFile.exists()) {
            return@withContext PreFlightState.Acknowledged
        }

        val onDiskVersion = try {
            SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.version
            }
        } catch (e: Exception) {
            Log.e("PreFlight", "Error reading DB version on disk", e)
            0
        }

        // SPEC AMBIGUITY: Database version in codebase was 3, but the spec says version 5. Bumping directly to 5.
        if (onDiskVersion <= 0 || onDiskVersion >= 5) {
            return@withContext PreFlightState.Acknowledged
        }

        val acknowledged = sharedPrefs.getBoolean("pref_v5_migration_acknowledged", false)
        if (acknowledged) {
            return@withContext PreFlightState.Acknowledged
        }

        // Query read_state_queue
        val queuedActions = mutableListOf<LegacyQueuedAction>()
        try {
            SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                val cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='read_state_queue'", null)
                val exists = cursor.use { it.moveToFirst() }
                if (exists) {
                    db.rawQuery("SELECT id, articleId, action FROM read_state_queue", null).use { c ->
                        val idCol = c.getColumnIndex("id")
                        val articleIdCol = c.getColumnIndex("articleId")
                        val actionCol = c.getColumnIndex("action")
                        if (idCol >= 0 && articleIdCol >= 0 && actionCol >= 0) {
                            while (c.moveToNext()) {
                                queuedActions.add(
                                    LegacyQueuedAction(
                                        id = c.getLong(idCol),
                                        articleId = c.getLong(articleIdCol),
                                        action = c.getString(actionCol)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PreFlight", "Error reading read_state_queue from disk DB", e)
        }

        if (queuedActions.isEmpty()) {
            acknowledgeMigration()
            return@withContext PreFlightState.Acknowledged
        }

        // Query active server profile
        var activeProfile: LegacyServerProfile? = null
        try {
            SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                val cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='server_profiles'", null)
                val exists = cursor.use { it.moveToFirst() }
                if (exists) {
                    db.rawQuery("SELECT id, serverUrl, username FROM server_profiles WHERE isActive = 1 LIMIT 1", null).use { c ->
                        val idCol = c.getColumnIndex("id")
                        val serverUrlCol = c.getColumnIndex("serverUrl")
                        val usernameCol = c.getColumnIndex("username")
                        if (idCol >= 0 && serverUrlCol >= 0 && usernameCol >= 0 && c.moveToFirst()) {
                            activeProfile = LegacyServerProfile(
                                id = c.getLong(idCol),
                                serverUrl = c.getString(serverUrlCol),
                                username = c.getString(usernameCol)
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PreFlight", "Error reading server_profiles from disk DB", e)
        }

        val profile = activeProfile
        if (profile == null) {
            acknowledgeMigration()
            return@withContext PreFlightState.Acknowledged
        }

        val password = credentialRepository.getPassword(profile.id)
        if (password == null) {
            acknowledgeMigration()
            return@withContext PreFlightState.Acknowledged
        }

        return@withContext PreFlightState.UpgradePending(profile, queuedActions)
    }

    fun acknowledgeMigration() {
        sharedPrefs.edit().putBoolean("pref_v5_migration_acknowledged", true).apply()
    }

    suspend fun flushQueue(
        profile: LegacyServerProfile,
        actions: List<LegacyQueuedAction>
    ): FlushResult = withContext(Dispatchers.IO) {
        val password = credentialRepository.getPassword(profile.id)
        if (password == null) {
            return@withContext FlushResult.Failure(Exception("Password not found"))
        }

        val standardKey = apiKeyHasher.computeApiKey(profile.username, password)
        val passwordOnlyKey = apiKeyHasher.computePasswordOnlyHash(password)
        val apiKeys = listOf(standardKey, passwordOnlyKey)

        val baseUrl = if (profile.serverUrl.endsWith("/")) profile.serverUrl else "${profile.serverUrl}/"
        val service = try {
            Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(FeverApiService::class.java)
        } catch (e: Exception) {
            return@withContext FlushResult.Failure(e)
        }

        var authSuccessKey: String? = null
        for (key in apiKeys) {
            try {
                val authResponse = service.query(apiKey = key, groups = "1")
                if (authResponse.auth == 1) {
                    authSuccessKey = key
                    break
                }
            } catch (e: Exception) {
                // Ignore and try next
            }
        }

        if (authSuccessKey == null) {
            return@withContext FlushResult.Failure(Exception("Authentication failed or network error during handshake"))
        }

        val completedIds = mutableListOf<Long>()
        var lastError: Throwable? = null

        for (action in actions) {
            try {
                val response = service.query(
                    apiKey = authSuccessKey,
                    mark = "item",
                    actionState = action.action,
                    id = action.articleId
                )
                if (response.auth == 1) {
                    completedIds.add(action.id)
                } else {
                    lastError = Exception("Server returned auth = 0 for action update")
                }
            } catch (e: Exception) {
                lastError = e
            }
        }

        if (completedIds.isNotEmpty()) {
            val dbFile = context.getDatabasePath("readability.db")
            try {
                SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                    db.beginTransaction()
                    try {
                        val idsStr = completedIds.joinToString(",")
                        db.execSQL("DELETE FROM read_state_queue WHERE id IN ($idsStr)")
                        db.setTransactionSuccessful()
                    } finally {
                        db.endTransaction()
                    }
                }
            } catch (e: Exception) {
                Log.e("PreFlight", "Error deleting flushed actions from disk DB", e)
            }
        }

        return@withContext if (completedIds.size == actions.size) {
            FlushResult.Success
        } else if (completedIds.isNotEmpty()) {
            FlushResult.Partial(actions.size - completedIds.size)
        } else {
            FlushResult.Failure(lastError ?: Exception("Unknown error"))
        }
    }
}
