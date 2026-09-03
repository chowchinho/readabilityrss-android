package co.chinho.readabilityreader.data.repository

import kotlinx.coroutines.flow.StateFlow

interface ConnectivityMonitor {
    val isOffline: StateFlow<Boolean>
    suspend fun isOnline(): Boolean
}
