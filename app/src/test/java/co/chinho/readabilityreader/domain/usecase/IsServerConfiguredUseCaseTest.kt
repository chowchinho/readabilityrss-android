package co.chinho.readabilityreader.domain.usecase

import co.chinho.readabilityreader.domain.repository.ServerProfileRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IsServerConfiguredUseCaseTest {

    private val serverProfileRepository = mockk<ServerProfileRepository>()
    private val useCase = IsServerConfiguredUseCase(serverProfileRepository)

    @Test
    fun `returns true when server is configured`() = runTest {
        coEvery { serverProfileRepository.isServerConfigured() } returns true

        val result = useCase()

        assertTrue(result)
    }

    @Test
    fun `returns false when no server profiles exist`() = runTest {
        coEvery { serverProfileRepository.isServerConfigured() } returns false

        val result = useCase()

        assertFalse(result)
    }
}
