package co.chinho.readabilityreader.domain.usecase

import co.chinho.readabilityreader.domain.model.ServerProfile
import co.chinho.readabilityreader.domain.repository.CredentialRepository
import co.chinho.readabilityreader.domain.repository.ServerProfileRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SaveServerProfileUseCaseTest {

    private lateinit var serverProfileRepository: ServerProfileRepository
    private lateinit var credentialRepository: CredentialRepository
    private lateinit var useCase: SaveServerProfileUseCase

    @Before
    fun setup() {
        serverProfileRepository = mockk(relaxed = true)
        credentialRepository = mockk(relaxed = true)
        useCase = SaveServerProfileUseCase(
            serverProfileRepository = serverProfileRepository,
            credentialRepository = credentialRepository,
        )
    }

    @Test
    fun savePasswordThrowsOnNewlyCreatedProfileDeletesProfileRowAndPropagatesFailure() = runBlocking {
        val newProfileId = 42L
        coEvery { serverProfileRepository.saveProfile(any()) } returns newProfileId
        coEvery { credentialRepository.savePassword(newProfileId, any()) } throws RuntimeException("Keystore failed")

        val result = useCase(
            name = "Test Profile",
            serverUrl = "https://example.com/fever/",
            username = "user",
            password = "pwd",
        )

        assertTrue(result.isFailure)
        assertEquals("Keystore failed", result.exceptionOrNull()?.message)

        coVerify(exactly = 1) { serverProfileRepository.saveProfile(any()) }
        coVerify(exactly = 1) { credentialRepository.savePassword(newProfileId, "pwd") }
        coVerify(exactly = 1) { serverProfileRepository.deleteProfile(newProfileId) }
    }

    @Test
    fun setActiveProfileThrowsOnNewlyCreatedProfileDeletesProfileRow() = runBlocking {
        val newProfileId = 42L
        coEvery { serverProfileRepository.saveProfile(any()) } returns newProfileId
        coEvery { credentialRepository.savePassword(newProfileId, any()) } returns Unit
        coEvery { serverProfileRepository.setActiveProfile(newProfileId) } throws RuntimeException("Active profile switch failed")

        val result = useCase(
            name = "Test Profile",
            serverUrl = "https://example.com/fever/",
            username = "user",
            password = "pwd",
        )

        assertTrue(result.isFailure)
        assertEquals("Active profile switch failed", result.exceptionOrNull()?.message)

        coVerify(exactly = 1) { serverProfileRepository.deleteProfile(newProfileId) }
    }

    @Test
    fun savePasswordThrowsWhileEditingExistingProfileLeavesExistingRowIntact() = runBlocking {
        val existingProfileId = 10L
        coEvery { serverProfileRepository.saveProfile(any()) } returns existingProfileId
        coEvery { credentialRepository.savePassword(existingProfileId, any()) } throws RuntimeException("Keystore update failed")

        val result = useCase(
            name = "Existing Profile",
            serverUrl = "https://example.com/fever/",
            username = "user",
            password = "newpwd",
            profileId = existingProfileId,
        )

        assertTrue(result.isFailure)
        assertEquals("Keystore update failed", result.exceptionOrNull()?.message)

        coVerify(exactly = 0) { serverProfileRepository.deleteProfile(any()) }
    }

    @Test
    fun successfulCreationSavesProfilePasswordAndSetsActive() = runBlocking {
        val newProfileId = 99L
        coEvery { serverProfileRepository.saveProfile(any()) } returns newProfileId

        val result = useCase(
            name = "Profile",
            serverUrl = "https://example.com/fever/",
            username = "user",
            password = "pwd",
        )

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { credentialRepository.savePassword(newProfileId, "pwd") }
        coVerify(exactly = 1) { serverProfileRepository.setActiveProfile(newProfileId) }
        coVerify(exactly = 0) { serverProfileRepository.deleteProfile(any()) }
    }
}
