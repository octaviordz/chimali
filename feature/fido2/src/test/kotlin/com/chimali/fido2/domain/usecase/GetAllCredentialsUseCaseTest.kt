package com.chimali.fido2.domain.usecase

import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.repository.CredentialRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals

class GetAllCredentialsUseCaseTest {
    private lateinit var credentialRepository: CredentialRepository
    private lateinit var getAllCredentialsUseCase: GetAllCredentialsUseCase

    private companion object {
        private const val PAGE_SIZE_10 = 10L
        private const val OFFSET_ZERO = 0L
        private const val EXPECTED_SIZE_2 = 2
    }

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
            val result = getAllCredentialsUseCase(PAGE_SIZE_10, OFFSET_ZERO).getOrNull() ?: emptyList()

            // Assert
            assertEquals(0, result.size)
            coVerify(exactly = 1) { credentialRepository.getPagedCredentials(PAGE_SIZE_10, OFFSET_ZERO) }
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
            val result = getAllCredentialsUseCase(PAGE_SIZE_10, OFFSET_ZERO).getOrNull() ?: emptyList()

            // Assert
            assertEquals(EXPECTED_SIZE_2, result.size)
            assertEquals(mockCredential1, result[0])
            assertEquals(mockCredential2, result[1])
            coVerify(exactly = 1) { credentialRepository.getPagedCredentials(PAGE_SIZE_10, OFFSET_ZERO) }
        }
}
