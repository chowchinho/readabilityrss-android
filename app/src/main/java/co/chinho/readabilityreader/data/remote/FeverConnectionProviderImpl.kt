package co.chinho.readabilityreader.data.remote

import co.chinho.readabilityreader.data.local.dao.ServerProfileDao
import co.chinho.readabilityreader.domain.repository.CredentialRepository
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeverConnectionProviderImpl @Inject constructor(
    private val serverProfileDao: ServerProfileDao,
    private val apiKeyHasher: ApiKeyHasher,
    private val credentialRepository: CredentialRepository,
    private val okHttpClient: OkHttpClient,
) : FeverConnectionProvider {

    private val serviceCache = mutableMapOf<String, FeverApiService>()

    override suspend fun getActiveConnection(): FeverConnection? {
        return getPotentialConnections().firstOrNull()
    }

    override suspend fun getPotentialConnections(): List<FeverConnection> {
        val profile = serverProfileDao.getActiveProfile() ?: return emptyList()
        val password = credentialRepository.getPassword(profile.id) ?: return emptyList()

        val standardKey = apiKeyHasher.computeApiKey(profile.username, password)
        val passwordOnlyKey = apiKeyHasher.computePasswordOnlyHash(password)

        val service = getOrCreateService(profile.serverUrl)
        val rankingService = getOrCreateRankingService(profile.serverUrl)
        val focalService = getOrCreateFocalService(profile.serverUrl)

        return listOf(
            FeverConnection(
                serverUrl = profile.serverUrl,
                apiKey = standardKey,
                service = service,
                rankingService = rankingService,
                focalService = focalService,
            ),
            FeverConnection(
                serverUrl = profile.serverUrl,
                apiKey = passwordOnlyKey,
                service = service,
                rankingService = rankingService,
                focalService = focalService,
            )
        )
    }

    private val rankingServiceCache = mutableMapOf<String, RankingApiService>()
    private val focalServiceCache = mutableMapOf<String, FocalApiService>()

    private fun getOrCreateService(serverUrl: String): FeverApiService {
        return serviceCache.getOrPut(serverUrl) {
            val baseUrl = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
            Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(FeverApiService::class.java)
        }
    }

    private fun getOrCreateRankingService(serverUrl: String): RankingApiService {
        return rankingServiceCache.getOrPut(serverUrl) {
            val readerBaseUrl = serverUrl.toReaderBaseUrl()
            val baseUrl = if (readerBaseUrl.endsWith("/")) readerBaseUrl else "$readerBaseUrl/"
            Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(RankingApiService::class.java)
        }
    }

    private fun getOrCreateFocalService(serverUrl: String): FocalApiService {
        return focalServiceCache.getOrPut(serverUrl) {
            val readerBaseUrl = serverUrl.toReaderBaseUrl()
            val baseUrl = if (readerBaseUrl.endsWith("/")) readerBaseUrl else "$readerBaseUrl/"
            Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(FocalApiService::class.java)
        }
    }

    private fun String.toReaderBaseUrl(): String {
        val normalized = trim().removeSuffix("/")
        return normalized.removeSuffix("/fever")
    }
}
