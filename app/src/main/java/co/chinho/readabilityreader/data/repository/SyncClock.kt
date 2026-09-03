package co.chinho.readabilityreader.data.repository

interface SyncClock {
    fun nowMillis(): Long
}
