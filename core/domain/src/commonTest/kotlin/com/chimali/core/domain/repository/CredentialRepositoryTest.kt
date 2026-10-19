package com.chimali.core.domain.repository

import com.chimali.core.domain.util.TestDataFactory
import com.chimali.core.domain.valueobject.CredentialId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class CredentialRepositoryTest {
    private val repository = FakeCredentialRepository()

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun saveAndGetCredentialShouldReturnCorrectData() =
        runTest {
            // Given
            val credential =
                TestDataFactory.createCredential(
                    id = CredentialId.fromByteArray(ByteArray(16) { 1 }),
                    title = "Test",
                )

            // When
            val saveResult = repository.saveCredential(credential)
            val getResult = repository.getCredentialById(saveResult.getOrThrow())

            // Then
            assertTrue(saveResult.isSuccess)
            assertTrue(getResult.isSuccess)
            assertEquals(credential, getResult.getOrThrow())
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun searchCredentialsShouldReturnFilteredList() =
        runTest {
            // Given
            repository.saveCredential(
                TestDataFactory.createCredential(
                    id =
                        CredentialId.fromByteArray(
                            ByteArray(16) {
                                1
                            },
                        ),
                    title = "Apple",
                ),
            )
            repository.saveCredential(
                TestDataFactory.createCredential(
                    id =
                        CredentialId.fromByteArray(
                            ByteArray(16) {
                                2
                            },
                        ),
                    title = "Banana",
                ),
            )

            // When
            val result = repository.searchCredentials("App")

            // Then
            assertTrue(result.isSuccess)
            assertEquals(1, result.getOrThrow().size)
            assertEquals("Apple", result.getOrThrow().first().title)
        }
}
