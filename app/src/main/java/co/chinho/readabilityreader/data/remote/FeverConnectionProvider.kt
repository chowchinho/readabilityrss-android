package co.chinho.readabilityreader.data.remote

interface FeverConnectionProvider {
    suspend fun getActiveConnection(): FeverConnection?
    suspend fun getPotentialConnections(): List<FeverConnection>
}
