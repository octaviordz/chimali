package com.chimali.fido2.domain.usecase

import com.chimali.core.common.result.Outcome
import com.chimali.core.common.result.getOrNull
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.RpId
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.repository.CredentialRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
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
            coEvery { credentialRepository.getPagedCredentials(any(), any()) } returns Outcome.Success(emptyList())

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
            } returns Outcome.Success(listOf(mockCredential1, mockCredential2))

            // Act
            val result = getAllCredentialsUseCase(PAGE_SIZE_10, OFFSET_ZERO).getOrNull() ?: emptyList()

            // Assert
            assertEquals(EXPECTED_SIZE_2, result.size)
            assertEquals(mockCredential1, result[0])
            assertEquals(mockCredential2, result[1])
            coVerify(exactly = 1) { credentialRepository.getPagedCredentials(PAGE_SIZE_10, OFFSET_ZERO) }
        }

    @Test
    fun `successful page transfers mutable credential owners to caller for terminal erasure`() =
        runTest {
            val credential =
                PasskeyCredential.createTest(
                    id = CredentialId.generate(),
                    rpId = RpId("https://synthetic.example"),
                    userName = "synthetic-user",
                )
            val expectedRp = "https://synthetic.example".toCharArray()
            val expectedUser = "synthetic-user".toCharArray()
            try {
                coEvery { credentialRepository.getPagedCredentials(any(), any()) } returns
                    Outcome.Success(listOf(credential))

                val page = getAllCredentialsUseCase(PAGE_SIZE_10, OFFSET_ZERO).getOrNull()!!
                assertSame(credential, page.single(), "The use case forwards the repository-owned model")

                val rpOwner = credential.rpId.copyCharacters()
                val userOwner = credential.userName.copyCharacters()
                try {
                    assertContentEquals(expectedRp, rpOwner)
                    assertContentEquals(expectedUser, userOwner)
                } finally {
                    rpOwner.fill('\u0000')
                    userOwner.fill('\u0000')
                }

                credential.eraseSensitiveText()
                val erasedRp = credential.rpId.copyCharacters()
                val erasedUser = credential.userName.copyCharacters()
                try {
                    assertTrue(erasedRp.all { it == '\u0000' })
                    assertTrue(erasedUser.all { it == '\u0000' })
                } finally {
                    erasedRp.fill('\u0000')
                    erasedUser.fill('\u0000')
                }
            } finally {
                expectedRp.fill('\u0000')
                expectedUser.fill('\u0000')
                credential.eraseSensitiveText()
            }
        }
}
