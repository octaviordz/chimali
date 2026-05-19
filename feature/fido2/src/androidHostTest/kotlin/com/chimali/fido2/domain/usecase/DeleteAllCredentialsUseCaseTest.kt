package com.chimali.fido2.domain.usecase

import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.common.result.exceptionOrNull
import com.chimali.core.common.result.isFailure
import com.chimali.core.common.result.isSuccess
import com.chimali.core.domain.valueobject.RpId
import com.chimali.fido2.domain.repository.CredentialRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class DeleteAllCredentialsUseCaseTest {
    private lateinit var credentialRepository: CredentialRepository
    private lateinit var deleteAllCredentialsUseCase: DeleteAllCredentialsUseCase

    @BeforeTest
    fun setup() {
        credentialRepository = mockk()
        deleteAllCredentialsUseCase = DeleteAllCredentialsUseCase(credentialRepository)
    }

    @Test
    fun `invoke without rpId should delete all credentials`() =
        runTest {
            // Arrange
            coEvery { credentialRepository.deleteAllCredentials(null) } returns Outcome.Success(Unit)

            // Act
            val result = deleteAllCredentialsUseCase()

            // Assert
            assertTrue(result.isSuccess)
            coVerify(exactly = 1) { credentialRepository.deleteAllCredentials(null) }
        }

    @Test
    fun `invoke with rpId should delete only credentials for that rpId`() =
        runTest {
            // Arrange
            val rpId = RpId("example.com")
            coEvery { credentialRepository.deleteAllCredentials(rpId) } returns Outcome.Success(Unit)

            // Act
            val result = deleteAllCredentialsUseCase(rpId)

            // Assert
            assertTrue(result.isSuccess)
            coVerify(exactly = 1) { credentialRepository.deleteAllCredentials(rpId) }
        }

    @Test
    fun `invoke should return failure when repository fails`() =
        runTest {
            // Arrange
            val exception = Exception("Database error")
            coEvery { credentialRepository.deleteAllCredentials(null) } returns
                Outcome.Error(DomainError.DatabaseError("Database error", exception))

            // Act
            val result = deleteAllCredentialsUseCase()

            // Assert
            assertTrue(result.isFailure)
            assertEquals(exception, result.exceptionOrNull())
            coVerify(exactly = 1) { credentialRepository.deleteAllCredentials(null) }
        }
}
