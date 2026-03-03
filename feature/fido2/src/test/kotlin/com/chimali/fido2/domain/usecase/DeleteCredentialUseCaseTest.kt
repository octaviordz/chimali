package com.chimali.fido2.domain.usecase

import com.chimali.fido2.domain.repository.CredentialRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DeleteCredentialUseCaseTest {

    private lateinit var credentialRepository: CredentialRepository
    private lateinit var deleteCredentialUseCase: DeleteCredentialUseCase

    @BeforeEach
    fun setup() {
        credentialRepository = mockk()
        deleteCredentialUseCase = DeleteCredentialUseCase(credentialRepository)
    }

    @Test
    fun `invoke should return success when repository successfully deletes credential`() = runTest {
        // Arrange
        val credentialId = "test-credential-id"
        coEvery { credentialRepository.deleteCredential(credentialId) } returns Result.success(Unit)

        // Act
        val result = deleteCredentialUseCase(credentialId)

        // Assert
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { credentialRepository.deleteCredential(credentialId) }
    }

    @Test
    fun `invoke should return failure when repository fails to delete credential`() = runTest {
        // Arrange
        val credentialId = "non-existent-id"
        val exception = Exception("Credential not found")
        coEvery { credentialRepository.deleteCredential(credentialId) } returns Result.failure(exception)

        // Act
        val result = deleteCredentialUseCase(credentialId)

        // Assert
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
        coVerify(exactly = 1) { credentialRepository.deleteCredential(credentialId) }
    }
}
