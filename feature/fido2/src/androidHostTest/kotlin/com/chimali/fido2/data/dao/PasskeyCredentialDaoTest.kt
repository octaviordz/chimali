package com.chimali.fido2.data.dao

import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.RpId
import com.chimali.core.domain.valueobject.UserId
import com.chimali.fido2.data.database.Fido2Database
import com.chimali.fido2.data.database.PasskeyCredentialQueries
import com.chimali.fido2.data.database.Passkey_credential
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
    private lateinit var timeProvider: com.chimali.core.domain.time.TimeProvider
    private lateinit var testCredential: com.chimali.fido2.domain.model.PasskeyCredential
    private lateinit var testEntity: Passkey_credential
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
        timeProvider = mockk(relaxed = true)
        dao = PasskeyCredentialDao(database, timeProvider)
        every { database.passkeyCredentialQueries } returns queries

        val keyPairGenerator = KeyPairGenerator.getInstance("EC").apply { initialize(KEY_SIZE_256) }
        publicKey = keyPairGenerator.generateKeyPair().public

        testCredential =
            com.chimali.fido2.domain.model.PasskeyCredential.create(
                id = CredentialId.fromEncoded("dGVzdF9pZA"),
                rpId = RpId("example.com"),
                userId = UserId("user123"),
                userName = "testuser",
                userDisplayName = "Test User",
                publicKey = publicKey,
                privateKeyAlias = "test_alias",
                aaguid = ByteArray(AAGUID_SIZE_16),
                credentialId = "cred_id".toByteArray(),
            )

        testEntity =
            Passkey_credential(
                id = "dGVzdF9pZA",
                created_at = testCredential.createdAt.toEpochMilliseconds(),
                last_used_at = testCredential.lastUsedAt.toEpochMilliseconds(),
                aaguid =
                    java.util.Base64
                        .getEncoder()
                        .encodeToString(testCredential.aaguid),
                cose_algorithm = testCredential.coseAlgorithm.toLong(),
                credential_id =
                    java.util.Base64
                        .getEncoder()
                        .encodeToString(testCredential.credentialId),
                cred_protect_policy = testCredential.credProtectPolicy.toLong(),
                label = "Test Label",
                private_key_alias = "test_alias",
                public_key =
                    java.util.Base64
                        .getEncoder()
                        .encodeToString(publicKey.encoded),
                rp_id = "example.com",
                rp_name = "example.com",
                sign_count = 0L,
                user_display_name = "Test User",
                user_id = "user123",
                user_name = "testuser",
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
                        id = testCredential.id.encoded,
                        created_at = testCredential.createdAt.toEpochMilliseconds(),
                        last_used_at = testCredential.lastUsedAt.toEpochMilliseconds(),
                        aaguid =
                            java.util.Base64
                                .getEncoder()
                                .encodeToString(testCredential.aaguid),
                        cose_algorithm = testCredential.coseAlgorithm.toLong(),
                        credential_id = testCredential.id.encoded,
                        cred_protect_policy = testCredential.credProtectPolicy.toLong(),
                        label = testCredential.label,
                        private_key_alias = testCredential.privateKeyAlias,
                        public_key =
                            java.util.Base64
                                .getEncoder()
                                .encodeToString(publicKey.encoded),
                        rp_id = testCredential.rpId.value,
                        rp_name = testCredential.rpId.value,
                        sign_count = testCredential.signCount,
                        user_display_name = testCredential.userDisplayName,
                        user_id = testCredential.userId.value,
                        user_name = testCredential.userName,
                    )
                }
            }

        @Test
        fun `should update credential successfully`() =
            runTest {
                dao.updateCredential(testCredential)

                coVerify(exactly = 1) {
                    queries.update(
                        last_used_at = testCredential.lastUsedAt.toEpochMilliseconds(),
                        label = testCredential.label,
                        rp_name = testCredential.rpId.value,
                        sign_count = testCredential.signCount,
                        user_display_name = testCredential.userDisplayName,
                        user_name = testCredential.userName,
                        id = testCredential.id.encoded,
                    )
                }
            }

        @Test
        fun `should update sign count successfully`() =
            runTest {
                dao.updateSignCount(testCredential.id, SIGN_COUNT_5)

                coVerify(exactly = 1) {
                    queries.update_sign_count(sign_count = SIGN_COUNT_5, id = testCredential.id.encoded)
                }
            }
    }

    @Nested
    inner class GetOperations {
        @Test
        fun `getCredentialById should return entity`() =
            runTest {
                val queryMock = mockk<app.cash.sqldelight.Query<Passkey_credential>>()
                every { queryMock.executeAsOneOrNull() } returns testEntity
                every { queries.select_by_id(testCredential.id.encoded) } returns queryMock

                val result = dao.getCredentialById(testCredential.id)

                assertEquals(testEntity, result)
                coVerify(exactly = 1) { queries.select_by_id(testCredential.id.encoded) }
            }
    }
}
