package com.chimali.fido2.domain.usecase

import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.common.result.exceptionOrNull
import com.chimali.core.common.result.isFailure
import com.chimali.core.common.result.isSuccess
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.fido2.domain.repository.CredentialRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class DeleteCredentialUseCaseTest {
    private lateinit var credentialRepository: CredentialRepository
    private lateinit var deleteCredentialUseCase: DeleteCredentialUseCase

    @BeforeTest
    fun setup() {
        credentialRepository = mockk()
        deleteCredentialUseCase = DeleteCredentialUseCase(credentialRepository)
    }

    @Test
    fun `invoke should return success when repository successfully deletes credential`() =
        runTest {
            // Arrange
            val credentialId = CredentialId.fromEncoded("test-credential-id")
            coEvery { credentialRepository.deleteCredential(credentialId) } returns Outcome.Success(Unit)

            // Act
            val result = deleteCredentialUseCase(credentialId)

            // Assert
            assertTrue(result.isSuccess)
            coVerify(exactly = 1) { credentialRepository.deleteCredential(credentialId) }
        }

    @Test
    fun `invoke should return failure when repository fails to delete credential`() =
        runTest {
            // Arrange
            val credentialId = CredentialId.fromEncoded("non-existent-id")
            val exception = Exception("Credential not found")
            coEvery { credentialRepository.deleteCredential(credentialId) } returns
                Outcome.Error(DomainError.NotFound("Credential not found", exception))

            // Act
            val result = deleteCredentialUseCase(credentialId)

            // Assert
            assertTrue(result.isFailure)
            assertEquals(exception, result.exceptionOrNull())
            coVerify(exactly = 1) { credentialRepository.deleteCredential(credentialId) }
        }
}
