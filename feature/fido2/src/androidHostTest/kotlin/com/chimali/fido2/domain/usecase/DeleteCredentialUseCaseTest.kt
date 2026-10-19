package com.chimali.fido2.domain.usecase
import com.chimali.core.common.annotation.Req
import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.common.result.exceptionOrNull
import com.chimali.core.common.result.isFailure
import com.chimali.core.common.result.isSuccess
import com.chimali.fido2.domain.repository.CredentialRepository
import com.chimali.fido2.testing.testCredentialIdFromLabel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

@Req("NFR-SEC-041")
class DeleteCredentialUseCaseTest {
    private lateinit var credentialRepository: CredentialRepository
    private lateinit var deleteCredentialUseCase: DeleteCredentialUseCase

    @BeforeTest
    fun setup() {
        credentialRepository = mockk()
        deleteCredentialUseCase = DeleteCredentialUseCase(credentialRepository)
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `invoke should return success when repository successfully deletes credential`() =
        runTest {
            // Arrange
            val credentialId = testCredentialIdFromLabel("test-credential-iQB0ZXN0LWNyZWRlbnRpYWwtaWQ")
            coEvery { credentialRepository.deleteCredential(credentialId) } returns Outcome.Success(Unit)

            // Act
            val result = deleteCredentialUseCase(credentialId)

            // Assert
            assertTrue(result.isSuccess)
            coVerify(exactly = 1) { credentialRepository.deleteCredential(credentialId) }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `invoke should return failure when repository fails to delete credential`() =
        runTest {
            // Arrange
            val credentialId = testCredentialIdFromLabel("non-existent-icAbm9uLWV4aXN0ZW50LWlk")
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
