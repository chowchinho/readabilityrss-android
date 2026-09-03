package co.chinho.readabilityreader.di

import co.chinho.readabilityreader.data.remote.FeverConnectionProvider
import co.chinho.readabilityreader.data.remote.FeverConnectionProviderImpl
import co.chinho.readabilityreader.data.repository.ArticleRepositoryImpl
import co.chinho.readabilityreader.data.repository.ConnectivityMonitor
import co.chinho.readabilityreader.data.repository.ConnectivityMonitorImpl
import co.chinho.readabilityreader.data.repository.CredentialRepositoryImpl
import co.chinho.readabilityreader.data.repository.FeedRepositoryImpl
import co.chinho.readabilityreader.data.repository.ServerProfileRepositoryImpl
import co.chinho.readabilityreader.data.repository.SyncClock
import co.chinho.readabilityreader.data.repository.SystemSyncClock
import co.chinho.readabilityreader.domain.repository.ArticleRepository
import co.chinho.readabilityreader.domain.repository.CredentialRepository
import co.chinho.readabilityreader.domain.repository.FeedRepository
import co.chinho.readabilityreader.domain.repository.ServerProfileRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindArticleRepository(impl: ArticleRepositoryImpl): ArticleRepository

    @Binds
    @Singleton
    abstract fun bindFeedRepository(impl: FeedRepositoryImpl): FeedRepository

    @Binds
    @Singleton
    abstract fun bindFeverConnectionProvider(impl: FeverConnectionProviderImpl): FeverConnectionProvider

    @Binds
    @Singleton
    abstract fun bindConnectivityMonitor(impl: ConnectivityMonitorImpl): ConnectivityMonitor

    @Binds
    @Singleton
    abstract fun bindSyncClock(impl: SystemSyncClock): SyncClock

    @Binds
    @Singleton
    abstract fun bindServerProfileRepository(impl: ServerProfileRepositoryImpl): ServerProfileRepository

    @Binds
    @Singleton
    abstract fun bindCredentialRepository(impl: CredentialRepositoryImpl): CredentialRepository
}
