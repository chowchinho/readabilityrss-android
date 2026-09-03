package co.chinho.readabilityreader.domain.repository

import co.chinho.readabilityreader.domain.model.Article

interface JottyRepository {

    sealed interface SendResult {
        data object Sent : SendResult
        data class Queued(val reason: String) : SendResult
        data object Disabled : SendResult
    }

    sealed interface TestResult {
        data object Ok : TestResult
        data class Failed(val message: String) : TestResult
    }

    suspend fun sendArticle(article: Article): SendResult

    suspend fun testConnection(serverUrl: String, apiKey: String): TestResult

    suspend fun flushQueue(): FlushSummary

    data class FlushSummary(
        val sent: Int,
        val stillPending: Int,
        val failedHard: Int,
    )
}
