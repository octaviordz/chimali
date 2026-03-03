package com.chimali.fido2.domain.usecase

import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.repository.CredentialRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GetAllCredentialsUseCaseTest {

    private lateinit var credentialRepository: CredentialRepository
    private lateinit var getAllCredentialsUseCase: GetAllCredentialsUseCase

    @BeforeEach
    fun setup() {
        credentialRepository = mockk()
        getAllCredentialsUseCase = GetAllCredentialsUseCase(credentialRepository)
    }

    @Test
    fun `invoke should return empty flow when no credentials exist`() = runTest {
        // Arrange
        coEvery { credentialRepository.getAllCredentials() } returns flowOf()

        // Act
        val result = getAllCredentialsUseCase().toList()

        // Assert
        assertEquals(0, result.size)
        coVerify(exactly = 1) { credentialRepository.getAllCredentials() }
    }

    @Test
    fun `invoke should return all credentials from repository`() = runTest {
        // Arrange
        val mockCredential1 = mockk<PasskeyCredential>()
        val mockCredential2 = mockk<PasskeyCredential>()
        coEvery { credentialRepository.getAllCredentials() } returns flowOf(mockCredential1, mockCredential2)

        // Act
        val result = getAllCredentialsUseCase().toList()

        // Assert
        assertEquals(2, result.size)
        assertEquals(mockCredential1, result[0])
        assertEquals(mockCredential2, result[1])
        coVerify(exactly = 1) { credentialRepository.getAllCredentials() }
    }
}
