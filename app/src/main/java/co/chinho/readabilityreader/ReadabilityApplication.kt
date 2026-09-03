package co.chinho.readabilityreader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Application
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import dagger.hilt.android.HiltAndroidApp
import co.chinho.readabilityreader.data.repository.ReadStateFlushObserver
import co.chinho.readabilityreader.worker.ArticleSyncWorker
import javax.inject.Inject
import javax.inject.Provider

@HiltAndroidApp
class ReadabilityApplication : Application(), Configuration.Provider, ImageLoaderFactory {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var coilImageLoaderProvider: Provider<ImageLoader>
    @Inject lateinit var readStateFlushObserver: ReadStateFlushObserver

    override fun onCreate() {
        super.onCreate()
        registerNotificationChannel()
        readStateFlushObserver.start()
    }

    override fun newImageLoader(): ImageLoader = coilImageLoaderProvider.get()

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private fun registerNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            ArticleSyncWorker.NOTIFICATION_CHANNEL_ID,
            getString(R.string.app_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Sync progress and completion notifications"
        }

        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
