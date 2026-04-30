package com.chimali.fido2.domain.usecase

import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.common.result.exceptionOrNull
import com.chimali.core.common.result.isFailure
import com.chimali.core.common.result.isSuccess
import com.chimali.fido2.domain.repository.CredentialRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

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
            coEvery { credentialRepository.resetAuthenticator() } returns Outcome.Success(Unit)

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
            coEvery { credentialRepository.resetAuthenticator() } returns
                Outcome.Error(
                    DomainError.UnknownError(
                        exception.message ?: "error",
                        exception,
                    ),
                )

            // Act
            val result = resetAuthenticatorUseCase()

            // Assert
            assertTrue(result.isFailure)
            assertEquals(exception, result.exceptionOrNull())
            coVerify(exactly = 1) { credentialRepository.resetAuthenticator() }
        }
}
