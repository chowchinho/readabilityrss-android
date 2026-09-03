package co.chinho.readabilityreader.data.repository

import co.chinho.readabilityreader.data.local.dao.JottyQueueDao
import co.chinho.readabilityreader.data.local.entity.JottyQueueEntity
import co.chinho.readabilityreader.data.remote.jotty.CreateNoteRequest
import co.chinho.readabilityreader.data.remote.jotty.JottyApiService
import co.chinho.readabilityreader.data.remote.jotty.JottyNoteFormatter
import co.chinho.readabilityreader.di.JottyServiceFactory
import co.chinho.readabilityreader.domain.model.Article
import co.chinho.readabilityreader.domain.repository.CredentialRepository
import co.chinho.readabilityreader.domain.repository.JottyRepository
import co.chinho.readabilityreader.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JottyRepositoryImpl @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val credentialRepository: CredentialRepository,
    private val jottyServiceFactory: JottyServiceFactory,
    private val noteFormatter: JottyNoteFormatter,
    private val jottyQueueDao: JottyQueueDao,
) : JottyRepository {

    override suspend fun sendArticle(article: Article): JottyRepository.SendResult {
        val enabled = userPreferencesRepository.jottyEnabled.first()
        if (!enabled) return JottyRepository.SendResult.Disabled

        val category = userPreferencesRepository.jottyDefaultCategory.first()
        val note = withContext(Dispatchers.Default) { noteFormatter.format(article, category) }
        val serverUrl = userPreferencesRepository.jottyServerUrl.first()
        val apiKey = credentialRepository.getJottyApiKey()

        if (serverUrl.isBlank() || apiKey.isNullOrBlank()) {
            enqueue(article.id, note.title, note.content, note.category, "Jotty server URL or API key missing")
            return JottyRepository.SendResult.Queued("Server URL or API key not set")
        }

        return tryPost(article.id, note.title, note.content, note.category, serverUrl, apiKey)
    }

    override suspend fun testConnection(serverUrl: String, apiKey: String): JottyRepository.TestResult {
        if (serverUrl.isBlank() || apiKey.isBlank()) {
            return JottyRepository.TestResult.Failed("Server URL and API key are required")
        }
        val service = runCatching { jottyServiceFactory.create(serverUrl) }
            .getOrElse { return JottyRepository.TestResult.Failed(it.message ?: "Invalid server URL") }
        return try {
            val response = service.listCategories(apiKey)
            if (response.isSuccessful) {
                JottyRepository.TestResult.Ok
            } else {
                JottyRepository.TestResult.Failed("HTTP ${response.code()}: ${response.message()}")
            }
        } catch (t: Throwable) {
            JottyRepository.TestResult.Failed(t.message ?: t.javaClass.simpleName)
        }
    }

    override suspend fun flushQueue(): JottyRepository.FlushSummary {
        val enabled = userPreferencesRepository.jottyEnabled.first()
        if (!enabled) {
            return JottyRepository.FlushSummary(sent = 0, stillPending = jottyQueueDao.count(), failedHard = 0)
        }
        val serverUrl = userPreferencesRepository.jottyServerUrl.first()
        val apiKey = credentialRepository.getJottyApiKey()
        if (serverUrl.isBlank() || apiKey.isNullOrBlank()) {
            return JottyRepository.FlushSummary(sent = 0, stillPending = jottyQueueDao.count(), failedHard = 0)
        }
        val pending = jottyQueueDao.getPending(MAX_ATTEMPTS)
        var sent = 0
        var stillPending = 0
        var failedHard = 0
        pending.forEach { entry ->
            val result = tryPost(entry.articleId, entry.title, entry.content, entry.category, serverUrl, apiKey)
            when (result) {
                is JottyRepository.SendResult.Sent -> sent += 1
                is JottyRepository.SendResult.Queued -> {
                    val newAttempts = entry.attempts + 1
                    if (newAttempts >= MAX_ATTEMPTS) failedHard += 1 else stillPending += 1
                }
                is JottyRepository.SendResult.Disabled -> Unit
            }
        }
        return JottyRepository.FlushSummary(sent = sent, stillPending = stillPending, failedHard = failedHard)
    }

    private suspend fun tryPost(
        articleId: Long,
        title: String,
        content: String,
        category: String,
        serverUrl: String,
        apiKey: String,
    ): JottyRepository.SendResult {
        return try {
            val service = jottyServiceFactory.create(serverUrl)
            val response = service.createNote(apiKey, CreateNoteRequest(title = title, content = content, category = category))
            if (response.isSuccessful) {
                jottyQueueDao.deleteById(articleId)
                JottyRepository.SendResult.Sent
            } else {
                val reason = "HTTP ${response.code()}"
                bumpOrInsert(articleId, title, content, category, reason)
                JottyRepository.SendResult.Queued(reason)
            }
        } catch (t: Throwable) {
            val reason = t.message ?: t.javaClass.simpleName
            bumpOrInsert(articleId, title, content, category, reason)
            JottyRepository.SendResult.Queued(reason)
        }
    }

    private suspend fun enqueue(
        articleId: Long,
        title: String,
        content: String,
        category: String,
        reason: String,
    ) {
        bumpOrInsert(articleId, title, content, category, reason)
    }

    private suspend fun bumpOrInsert(
        articleId: Long,
        title: String,
        content: String,
        category: String,
        reason: String,
    ) {
        val now = System.currentTimeMillis()
        val existingAttempts = jottyQueueDao.getAttempts(articleId)
        if (existingAttempts != null) {
            jottyQueueDao.updateAttempt(
                articleId = articleId,
                attempts = existingAttempts + 1,
                lastAttemptAt = now,
                error = reason,
            )
        } else {
            jottyQueueDao.upsert(
                JottyQueueEntity(
                    articleId = articleId,
                    title = title,
                    content = content,
                    category = category,
                    attempts = 1,
                    lastAttemptAt = now,
                    lastErrorMessage = reason,
                )
            )
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 10
    }
}
