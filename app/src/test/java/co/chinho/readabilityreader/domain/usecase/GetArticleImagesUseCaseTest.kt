package co.chinho.readabilityreader.domain.usecase

import co.chinho.readabilityreader.domain.model.ArticleImage
import co.chinho.readabilityreader.domain.repository.ArticleRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetArticleImagesUseCaseTest {

    private val articleRepository: ArticleRepository = mockk()
    private val useCase = GetArticleImagesUseCase(articleRepository)

    @Test
    fun `invoke returns the repository images in order`() = runTest {
        val articleId = 123L
        val expected = listOf(
            ArticleImage("https://example.com/img1.jpg", focalX = 40, focalY = 60),
            ArticleImage("https://example.com/img2.jpg"),
        )
        coEvery { articleRepository.getArticleImages(articleId) } returns expected

        val result = useCase(articleId)

        assertEquals(expected, result)
    }
}
