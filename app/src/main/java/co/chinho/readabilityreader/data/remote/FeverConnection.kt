package co.chinho.readabilityreader.data.remote

data class FeverConnection(
    val serverUrl: String,
    val apiKey: String,
    val service: FeverApiService,
    val rankingService: RankingApiService,
    val focalService: FocalApiService,
)
