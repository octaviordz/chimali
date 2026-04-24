package com.chimali.fido2.domain.usecase

import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.repository.CredentialRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class GetAllCredentialsUseCaseTest {
    private lateinit var credentialRepository: CredentialRepository
    private lateinit var getAllCredentialsUseCase: GetAllCredentialsUseCase

    @BeforeTest
    fun setup() {
        credentialRepository = mockk()
        getAllCredentialsUseCase = GetAllCredentialsUseCase(credentialRepository)
    }

    @Test
    fun `invoke should return empty list when no credentials exist`() =
        runTest {
            // Arrange
            coEvery { credentialRepository.getPagedCredentials(any(), any()) } returns Result.success(emptyList())

            // Act
            val result = getAllCredentialsUseCase(10L, 0L).getOrNull() ?: emptyList()

            // Assert
            assertEquals(0, result.size)
            coVerify(exactly = 1) { credentialRepository.getPagedCredentials(10L, 0L) }
        }

    @Test
    fun `invoke should return credentials from repository page`() =
        runTest {
            // Arrange
            val mockCredential1 = mockk<PasskeyCredential>()
            val mockCredential2 = mockk<PasskeyCredential>()
            coEvery {
                credentialRepository.getPagedCredentials(any(), any())
            } returns Result.success(listOf(mockCredential1, mockCredential2))

            // Act
            val result = getAllCredentialsUseCase(10L, 0L).getOrNull() ?: emptyList()

            // Assert
            assertEquals(2, result.size)
            assertEquals(mockCredential1, result[0])
            assertEquals(mockCredential2, result[1])
            coVerify(exactly = 1) { credentialRepository.getPagedCredentials(10L, 0L) }
        }
}
