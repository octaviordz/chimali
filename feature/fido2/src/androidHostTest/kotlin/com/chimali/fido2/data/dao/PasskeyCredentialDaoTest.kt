package com.chimali.fido2.data.dao

import com.chimali.core.common.annotation.Req
import com.chimali.core.domain.model.CredentialKeyDescriptor
import com.chimali.core.domain.valueobject.RpId
import com.chimali.core.domain.valueobject.UserId
import com.chimali.fido2.data.database.Fido2Database
import com.chimali.fido2.data.database.PasskeyCredentialQueries
import com.chimali.fido2.data.database.Passkey_credential
import com.chimali.fido2.testing.testCredentialIdFromLabel
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.security.KeyPairGenerator
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested

@Req("NFR-SEC-041")
class PasskeyCredentialDaoTest {
    private lateinit var database: Fido2Database
    private lateinit var queries: PasskeyCredentialQueries
    private lateinit var dao: PasskeyCredentialDao
    private lateinit var timeProvider: com.chimali.core.domain.time.TimeProvider
    private lateinit var metadataProtectionService: com.chimali.fido2.data.service.CredentialMetadataProtectionService
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
        metadataProtectionService =
            mockk(relaxed = true) {
                every { getRpIdIndex(any()) } answers { "rp:${firstArg<CharSequence>()}".toByteArray() }
                every { getUserIdIndex(any()) } answers { "user:${firstArg<CharSequence>()}".toByteArray() }
                every { encryptCredentialMetadata(any(), any()) } returns byteArrayOf(1, 2, 3)
                every { encryptRelyingPartyMetadata(any(), any()) } returns byteArrayOf(4, 5, 6)
            }
        dao = PasskeyCredentialDao(database, timeProvider, metadataProtectionService)
        every { database.passkeyCredentialQueries } returns queries

        val keyPairGenerator = KeyPairGenerator.getInstance("EC").apply { initialize(KEY_SIZE_256) }
        publicKey = keyPairGenerator.generateKeyPair().public

        testCredential =
            com.chimali.fido2.domain.model.PasskeyCredential.create(
                id = testCredentialIdFromLabel("dGVzdF9pZABkR1Z6ZEY5cFpB"),
                rpId = RpId("example.com"),
                userId = UserId("user123"),
                userName = "testuser",
                userDisplayName = "Test User",
                publicKey = publicKey,
                aaguid = ByteArray(AAGUID_SIZE_16),
                credentialId = testCredentialIdFromLabel("dGVzdF9pZABkR1Z6ZEY5cFpB").toByteArray(),
                derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
            )

        testEntity =
            Passkey_credential(
                id = testCredential.id.encoded,
                created_at = testCredential.createdAt.toEpochMilliseconds(),
                last_used_at = testCredential.lastUsedAt.toEpochMilliseconds(),
                updated_at = testCredential.createdAt.toEpochMilliseconds(),
                aaguid =
                    java.util.Base64
                        .getEncoder()
                        .encodeToString(testCredential.aaguid),
                cose_algorithm = testCredential.coseAlgorithm.toLong(),
                credential_id = testCredential.id.encoded,
                cred_protect_policy = testCredential.credProtectPolicy.toLong(),
                derivation_profile = testCredential.derivationProfile,
                label = "Test Label",
                public_key =
                    java.util.Base64
                        .getEncoder()
                        .encodeToString(publicKey.encoded),
                rp_id = "example.com".encodeToByteArray(),
                rp_name = "example.com".encodeToByteArray(),
                sign_count = 0L,
                user_display_name = "Test User".encodeToByteArray(),
                user_id = "user123".encodeToByteArray(),
                user_name = "testuser".encodeToByteArray(),
                rp_id_index = "rp:example.com".toByteArray(),
                user_id_index = "user:user123".toByteArray(),
                encrypted_metadata = byteArrayOf(1, 2, 3),
            )
    }

    @Nested
    inner class InsertUpdateOperations {
        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Test
        fun `should insert credential successfully`() =
            runTest {
                var insertedRpId: ByteArray? = null
                var insertedUserId: ByteArray? = null
                var insertedUserName: ByteArray? = null
                var insertedDisplayName: ByteArray? = null
                every {
                    queries.insert(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                    )
                } answers {
                    insertedRpId = arg<ByteArray>(12).copyOf()
                    insertedDisplayName = arg<ByteArray>(16).copyOf()
                    insertedUserId = arg<ByteArray>(17).copyOf()
                    insertedUserName = arg<ByteArray>(19).copyOf()
                    mockk(relaxed = true)
                }
                dao.insertCredential(testCredential)

                coVerify(exactly = 1) {
                    queries.insert(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                    )
                }
                assertEquals("example.com", requireNotNull(insertedRpId).decodeToString())
                assertEquals("user123", requireNotNull(insertedUserId).decodeToString())
                assertEquals("testuser", requireNotNull(insertedUserName).decodeToString())
                assertEquals("Test User", requireNotNull(insertedDisplayName).decodeToString())
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Test
        fun `should update credential successfully`() =
            runTest {
                var updatedValues = emptyList<ByteArray>()
                every { queries.update(any(), any(), any(), any(), any(), any(), any()) } answers {
                    updatedValues =
                        listOf(arg<ByteArray>(2), arg<ByteArray>(4), arg<ByteArray>(5))
                            .map(ByteArray::copyOf)
                    mockk(relaxed = true)
                }
                dao.updateCredential(testCredential)

                coVerify(exactly = 1) { queries.update(any(), any(), any(), any(), any(), any(), any()) }
                assertEquals(
                    listOf("example.com", "Test User", "testuser"),
                    updatedValues.map(ByteArray::decodeToString),
                )
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
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
        /** @see NFR-SEC-041 and TR-AUTH-031 */
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

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `replacement erases retained row metadata after success failure and cancellation`() {
        fun retainedRow(): Pair<Passkey_credential, List<ByteArray>> {
            val fields =
                listOf(
                    "synthetic-rp".encodeToByteArray(),
                    "synthetic-rp-name".encodeToByteArray(),
                    "synthetic-display".encodeToByteArray(),
                    "synthetic-user-id".encodeToByteArray(),
                    "synthetic-user-name".encodeToByteArray(),
                )
            return testEntity.copy(
                rp_id = fields[0],
                rp_name = fields[1],
                user_display_name = fields[2],
                user_id = fields[3],
                user_name = fields[4],
            ) to fields
        }

        val (successRow, successBuffers) = retainedRow()
        dao.deleteAndEraseCredentialRows(listOf(successRow), ensureActive = {}, deleteById = {})
        successBuffers.forEach { assertContentEquals(ByteArray(it.size), it) }

        val (failureRow, failureBuffers) = retainedRow()
        assertFailsWith<IllegalStateException> {
            dao.deleteAndEraseCredentialRows(
                listOf(failureRow),
                ensureActive = {},
                deleteById = { error("synthetic delete failure") },
            )
        }
        failureBuffers.forEach { assertContentEquals(ByteArray(it.size), it) }

        val (cancelledRow, cancelledBuffers) = retainedRow()
        assertFailsWith<java.util.concurrent.CancellationException> {
            dao.deleteAndEraseCredentialRows(
                listOf(cancelledRow),
                ensureActive = {
                    throw java.util.concurrent.CancellationException("synthetic replacement cancellation")
                },
                deleteById = {},
            )
        }
        cancelledBuffers.forEach { assertContentEquals(ByteArray(it.size), it) }
    }
}
