package com.chimali.fido2.domain.usecase

import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.common.result.exceptionOrNull
import com.chimali.core.common.result.isFailure
import com.chimali.core.common.result.isSuccess
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.RpId
import com.chimali.fido2.domain.repository.Fido2Repository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class AuthenticateCredentialUseCaseTest {
    private lateinit var fido2Repository: Fido2Repository
    private lateinit var authenticateCredentialUseCase: AuthenticateCredentialUseCase

    @BeforeTest
    fun setup() {
        fido2Repository = mockk()
        authenticateCredentialUseCase = AuthenticateCredentialUseCase(fido2Repository)
    }

    @Test
    fun `invoke should return success when repository successfully authenticates`() =
        runTest {
            // Arrange
            val rpId = RpId("example.com")
            val credentialId = CredentialId.fromEncoded("test-credential-id")
            coEvery { fido2Repository.authenticateCredential(rpId) } returns Outcome.Success(credentialId)

            // Act
            val result = authenticateCredentialUseCase(rpId)

            // Assert
            assertTrue(result.isSuccess)
            assertEquals(credentialId, (result as Outcome.Success).data)
            coVerify(exactly = 1) { fido2Repository.authenticateCredential(rpId) }
        }

    @Test
    fun `invoke should return failure when repository fails to authenticate`() =
        runTest {
            // Arrange
            val rpId = RpId("example.com")
            val exception = Exception("Authentication failed")
            coEvery { fido2Repository.authenticateCredential(rpId) } returns
                Outcome.Error(DomainError.UnknownError("Authentication failed", exception))

            // Act
            val result = authenticateCredentialUseCase(rpId)

            // Assert
            assertTrue(result.isFailure)
            assertEquals(exception, result.exceptionOrNull())
            coVerify(exactly = 1) { fido2Repository.authenticateCredential(rpId) }
        }
}
