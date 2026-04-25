package com.chimali.fido2.data.dao

import com.chimali.fido2.data.database.Fido2Database
import com.chimali.fido2.data.database.PasskeyCredential
import com.chimali.fido2.data.database.PasskeyCredentialQueries
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.security.KeyPairGenerator
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested

class PasskeyCredentialDaoTest {
    private lateinit var database: Fido2Database
    private lateinit var queries: PasskeyCredentialQueries
    private lateinit var dao: PasskeyCredentialDao
    private lateinit var testCredential: com.chimali.fido2.domain.model.PasskeyCredential
    private lateinit var testEntity: PasskeyCredential
    private lateinit var publicKey: java.security.PublicKey

    private companion object {
        private const val KEY_SIZE_256 = 256
        private const val AAGUID_SIZE_16 = 16
        private const val SIGN_COUNT_5 = 5L
    }

    @BeforeTest
    fun setUp() {
        database = mockk(relaxed = true)
        queries = mockk(relaxed = true)
        dao = PasskeyCredentialDao(database)
        every { database.passkeyCredentialQueries } returns queries

        val keyPairGenerator = KeyPairGenerator.getInstance("EC").apply { initialize(KEY_SIZE_256) }
        publicKey = keyPairGenerator.generateKeyPair().public

        testCredential =
            com.chimali.fido2.domain.model.PasskeyCredential.create(
                id = "test_id",
                rpId = "example.com",
                userId = "user123",
                userName = "testuser",
                userDisplayName = "Test User",
                publicKey = publicKey,
                privateKeyAlias = "test_alias",
                aaguid = ByteArray(AAGUID_SIZE_16),
                credentialId = "cred_id".toByteArray(),
            )

        testEntity =
            PasskeyCredential(
                id = "test_id",
                createdAt = testCredential.createdAt.toEpochMilli(),
                lastUsedAt = testCredential.lastUsedAt.toEpochMilli(),
                aaguid = java.util.Base64.getEncoder().encodeToString(testCredential.aaguid),
                coseAlgorithm = testCredential.coseAlgorithm.toLong(),
                credentialId = java.util.Base64.getEncoder().encodeToString(testCredential.credentialId),
                credProtectPolicy = testCredential.credProtectPolicy.toLong(),
                label = "Test Label",
                privateKeyAlias = "test_alias",
                publicKey = java.util.Base64.getEncoder().encodeToString(publicKey.encoded),
                rpId = "example.com",
                rpName = "example.com",
                signCount = 0L,
                userDisplayName = "Test User",
                userId = "user123",
                userName = "testuser",
            )
    }

    @Nested
    inner class InsertUpdateOperations {
        @Test
        fun `should insert credential successfully`() =
            runTest {
                dao.insertCredential(testCredential)

                coVerify(exactly = 1) {
                    queries.insert(
                        id = testCredential.id,
                        createdAt = testCredential.createdAt.toEpochMilli(),
                        lastUsedAt = testCredential.lastUsedAt.toEpochMilli(),
                        aaguid = java.util.Base64.getEncoder().encodeToString(testCredential.aaguid),
                        coseAlgorithm = testCredential.coseAlgorithm.toLong(),
                        credentialId = java.util.Base64.getEncoder().encodeToString(testCredential.credentialId),
                        credProtectPolicy = testCredential.credProtectPolicy.toLong(),
                        label = testCredential.label,
                        privateKeyAlias = testCredential.privateKeyAlias,
                        publicKey = java.util.Base64.getEncoder().encodeToString(publicKey.encoded),
                        rpId = testCredential.rpId,
                        rpName = testCredential.rpId,
                        signCount = testCredential.signCount,
                        userDisplayName = testCredential.userDisplayName,
                        userId = testCredential.userId,
                        userName = testCredential.userName,
                    )
                }
            }

        @Test
        fun `should update credential successfully`() =
            runTest {
                dao.updateCredential(testCredential)

                coVerify(exactly = 1) {
                    queries.update(
                        lastUsedAt = testCredential.lastUsedAt.toEpochMilli(),
                        label = testCredential.label,
                        rpName = testCredential.rpId,
                        signCount = testCredential.signCount,
                        userDisplayName = testCredential.userDisplayName,
                        userName = testCredential.userName,
                        id = testCredential.id,
                    )
                }
            }

        @Test
        fun `should update sign count successfully`() =
            runTest {
                dao.updateSignCount(testCredential.id, SIGN_COUNT_5)

                coVerify(exactly = 1) {
                    queries.updateSignCount(signCount = SIGN_COUNT_5, id = testCredential.id)
                }
            }
    }

    @Nested
    inner class GetOperations {
        @Test
        fun `getCredentialById should return entity`() =
            runTest {
                val queryMock = mockk<app.cash.sqldelight.Query<PasskeyCredential>>()
                every { queryMock.executeAsOneOrNull() } returns testEntity
                every { queries.selectById(testCredential.id) } returns queryMock

                val result = dao.getCredentialById(testCredential.id)

                assertEquals(testEntity, result)
                coVerify(exactly = 1) { queries.selectById(testCredential.id) }
            }
    }
}
