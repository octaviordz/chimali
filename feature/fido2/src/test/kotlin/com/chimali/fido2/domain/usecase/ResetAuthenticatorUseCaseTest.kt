package com.chimali.fido2.domain.usecase

import com.chimali.fido2.domain.repository.CredentialRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test

class ResetAuthenticatorUseCaseTest {
    private lateinit var credentialRepository: CredentialRepository
    private lateinit var resetAuthenticatorUseCase: ResetAuthenticatorUseCase

    @BeforeTest
    fun setup() {
        credentialRepository = mockk()
        resetAuthenticatorUseCase = ResetAuthenticatorUseCase(credentialRepository)
    }

    @Test
    fun `invoke should return success when repository successfully resets authenticator`() =
        runTest {
            // Arrange
            coEvery { credentialRepository.resetAuthenticator() } returns Result.success(Unit)

            // Act
            val result = resetAuthenticatorUseCase()

            // Assert
            assertTrue(result.isSuccess)
            coVerify(exactly = 1) { credentialRepository.resetAuthenticator() }
        }

    @Test
    fun `invoke should return failure when repository fails to reset authenticator`() =
        runTest {
            // Arrange
            val exception = Exception("Secure storage wipe failed")
            coEvery { credentialRepository.resetAuthenticator() } returns Result.failure(exception)

            // Act
            val result = resetAuthenticatorUseCase()

            // Assert
            assertTrue(result.isFailure)
            assertEquals(exception, result.exceptionOrNull())
            coVerify(exactly = 1) { credentialRepository.resetAuthenticator() }
        }
}
