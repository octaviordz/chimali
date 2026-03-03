package com.chimali.fido2.data.dao

import com.chimali.fido2.data.database.Fido2Database
import com.chimali.fido2.data.database.PasskeyCredential
import com.chimali.fido2.data.database.PasskeyCredentialQueries
import io.mockk.Runs
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.security.KeyPairGenerator
import java.time.Instant

@DisplayName("PasskeyCredentialDao Tests")
class PasskeyCredentialDaoTest {

    private lateinit var database: Fido2Database
    private lateinit var queries: PasskeyCredentialQueries
    private lateinit var dao: PasskeyCredentialDao
    private lateinit var testCredential: com.chimali.fido2.domain.model.PasskeyCredential
    private lateinit var testEntity: PasskeyCredential
    private lateinit var publicKey: java.security.PublicKey

    @BeforeEach
    fun setUp() {
        database = mockk()
        queries = mockk()
        dao = PasskeyCredentialDao(database)
        every { database.passkeyCredentialQueries } returns queries

        val keyPairGenerator = KeyPairGenerator.getInstance("EC").apply { initialize(256) }
        publicKey = keyPairGenerator.generateKeyPair().public

        testCredential = com.chimali.fido2.domain.model.PasskeyCredential.create(
            id = "test_id",
            rpId = "example.com",
            userId = "user123",
            userName = "testuser",
            userDisplayName = "Test User",
            publicKey = publicKey,
            privateKeyAlias = "test_alias",
            aaguid = ByteArray(16),
            credentialId = "cred_id".toByteArray()
        )

        testEntity = PasskeyCredential(
            id = "test_id",
            rpId = "example.com",
            rpName = "example.com",
            userId = "user123",
            userName = "testuser",
            userDisplayName = "Test User",
            privateKeyAlias = "test_alias",
            signCount = 0L,
            createdAt = testCredential.createdAt.toEpochMilli(),
            lastUsedAt = testCredential.lastUsedAt.toEpochMilli(),
            aaguid = java.util.Base64.getEncoder().encodeToString(testCredential.aaguid),
            credentialId = java.util.Base64.getEncoder().encodeToString(testCredential.credentialId),
            publicKey = java.util.Base64.getEncoder().encodeToString(publicKey.encoded)
        )
    }

    @Nested
    @DisplayName("Insert and Update Operations")
    inner class InsertUpdateOperations {
        @Test
        fun `should insert credential successfully`() = runTest {
            every { queries.insert(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } just Runs

            dao.insertCredential(testCredential)

            coVerify(exactly = 1) { 
                queries.insert(
                    id = testCredential.id,
                    rpId = testCredential.rpId,
                    rpName = testCredential.rpId,
                    userId = testCredential.userId,
                    userName = testCredential.userName,
                    userDisplayName = testCredential.userDisplayName,
                    privateKeyAlias = testCredential.privateKeyAlias,
                    signCount = testCredential.signCount,
                    createdAt = testCredential.createdAt.toEpochMilli(),
                    lastUsedAt = testCredential.lastUsedAt.toEpochMilli(),
                    aaguid = java.util.Base64.getEncoder().encodeToString(testCredential.aaguid),
                    credentialId = java.util.Base64.getEncoder().encodeToString(testCredential.credentialId),
                    publicKey = java.util.Base64.getEncoder().encodeToString(publicKey.encoded)
                ) 
            }
        }

        @Test
        fun `should update credential successfully`() = runTest {
            every { queries.update(any(), any(), any(), any(), any(), any()) } just Runs

            dao.updateCredential(testCredential)

            coVerify(exactly = 1) {
                queries.update(
                    rpName = testCredential.rpId,
                    userName = testCredential.userName,
                    userDisplayName = testCredential.userDisplayName,
                    signCount = testCredential.signCount,
                    lastUsedAt = testCredential.lastUsedAt.toEpochMilli(),
                    id = testCredential.id
                )
            }
        }

        @Test
        fun `should update sign count successfully`() = runTest {
            every { queries.updateSignCount(any(), any()) } just Runs

            dao.updateSignCount(testCredential.id, 5L)

            coVerify(exactly = 1) {
                queries.updateSignCount(signCount = 5L, credentialId = testCredential.id)
            }
        }
    }

    @Nested
    @DisplayName("Get Operations")
    inner class GetOperations {
        @Test
        fun `getCredentialById should return entity`() = runTest {
            val queryMock = mockk<app.cash.sqldelight.Query<PasskeyCredential>>()
            every { queryMock.executeAsOneOrNull() } returns testEntity
            every { queries.selectById(testCredential.id) } returns queryMock

            val result = dao.getCredentialById(testCredential.id)

            assertEquals(testEntity, result)
            coVerify(exactly = 1) { queries.selectById(testCredential.id) }
        }
    }
}
