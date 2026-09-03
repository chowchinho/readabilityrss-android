package co.chinho.readabilityreader.di

import co.chinho.readabilityreader.data.remote.jotty.JottyApiService
import co.chinho.readabilityreader.data.repository.JottyRepositoryImpl
import co.chinho.readabilityreader.domain.repository.JottyRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JottyServiceFactory @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    private val cache = ConcurrentHashMap<String, JottyApiService>()

    fun create(serverUrl: String): JottyApiService {
        val normalized = normalize(serverUrl)
        return cache.getOrPut(normalized) {
            Retrofit.Builder()
                .baseUrl(normalized)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(JottyApiService::class.java)
        }
    }

    private fun normalize(serverUrl: String): String {
        val trimmed = serverUrl.trim().trimEnd('/')
        return "$trimmed/"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class JottyBindingsModule {
    @Binds
    @Singleton
    abstract fun bindJottyRepository(impl: JottyRepositoryImpl): JottyRepository
}
