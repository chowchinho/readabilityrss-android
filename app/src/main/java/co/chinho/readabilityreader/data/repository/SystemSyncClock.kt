package co.chinho.readabilityreader.data.repository

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemSyncClock @Inject constructor() : SyncClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
