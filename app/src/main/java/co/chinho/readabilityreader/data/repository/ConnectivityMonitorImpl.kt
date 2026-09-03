package co.chinho.readabilityreader.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import co.chinho.readabilityreader.domain.repository.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@Singleton
class ConnectivityMonitorImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    userPreferencesRepository: UserPreferencesRepository,
) : ConnectivityMonitor {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private val networkStatus = MutableStateFlow(currentOnlineState())

    override val isOffline: StateFlow<Boolean> = combine(
        networkStatus,
        userPreferencesRepository.forceOffline,
    ) { isNetworkOnline, forceOffline ->
        forceOffline || !isNetworkOnline
    }.stateIn(
        scope = applicationScope,
        started = SharingStarted.Eagerly,
        initialValue = !currentOnlineState()
    )

    init {
        connectivityManager?.registerNetworkCallback(
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build(),
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    networkStatus.value = currentOnlineState()
                }

                override fun onLosing(network: Network, maxMsToLive: Int) {
                    networkStatus.value = false
                }

                override fun onLost(network: Network) {
                    networkStatus.value = false
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    networkStatus.value = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                }

                override fun onUnavailable() {
                    networkStatus.value = false
                }
            }
        )
    }

    override suspend fun isOnline(): Boolean {
        return !isOffline.value
    }

    private fun currentOnlineState(): Boolean {
        val cm = connectivityManager ?: return false
        val capabilities = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
