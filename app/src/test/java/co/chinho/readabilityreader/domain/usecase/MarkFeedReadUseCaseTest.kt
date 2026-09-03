package co.chinho.readabilityreader.domain.usecase

import co.chinho.readabilityreader.domain.repository.ArticleRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MarkFeedReadUseCaseTest {

    private val articleRepository = mockk<ArticleRepository>()
    private val useCase = MarkFeedReadUseCase(articleRepository)

    @Test
    fun `invoke calls repository with feed id`() = runTest {
        coEvery { articleRepository.markFeedRead(42L) } returns Unit

        useCase(42L)

        coVerify(exactly = 1) { articleRepository.markFeedRead(42L) }
    }

    @Test(expected = IllegalStateException::class)
    fun `invoke propagates repository failure`() = runTest {
        coEvery { articleRepository.markFeedRead(any()) } throws IllegalStateException("boom")

        useCase(7L)
    }
}
