package com.chimali.fido2.data.repository

import com.chimali.core.common.annotation.Req
import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.common.result.isSuccess
import com.chimali.core.domain.eventsourcing.AggregateService
import com.chimali.core.domain.eventsourcing.passkey.PasskeyCommand
import com.chimali.core.domain.eventsourcing.passkey.PasskeyState
import com.chimali.core.domain.model.ConsentOperationType
import com.chimali.core.domain.model.CredentialKeyDescriptor
import com.chimali.core.domain.model.CredentialSummary
import com.chimali.core.domain.model.RelyingParty
import com.chimali.core.domain.model.UserConsentRecord
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.RpId
import com.chimali.core.domain.valueobject.UserId
import com.chimali.fido2.data.dao.PasskeyCredentialDao
import com.chimali.fido2.data.dao.RelyingPartyDao
import com.chimali.fido2.data.dao.UserConsentRecordDao
import com.chimali.fido2.data.database.Passkey_credential
import com.chimali.fido2.data.database.Relying_party
import com.chimali.fido2.data.service.CredentialMetadataProtectionService
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.model.SensitiveCredentialText
import com.chimali.fido2.testing.CapturedDiagnostics
import com.chimali.fido2.testing.testCredentialIdFromLabel
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.security.KeyPairGenerator
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@OptIn(ExperimentalCoroutinesApi::class)
@Req("NFR-SEC-041")
class CredentialRepositoryImplTest {
    private lateinit var passkeyCredentialDao: PasskeyCredentialDao
    private lateinit var relyingPartyDao: RelyingPartyDao
    private lateinit var userConsentRecordDao: UserConsentRecordDao
    private lateinit var publicKeyDecoder: com.chimali.fido2.data.crypto.PublicKeyDecoder
    private lateinit var timeProvider: com.chimali.core.domain.time.TimeProvider
    private lateinit var aggregateService: AggregateService<PasskeyCommand, PasskeyState>
    private lateinit var repository: CredentialRepositoryImpl

    private lateinit var testCredential: PasskeyCredential
    private lateinit var testRp: RelyingParty
    private lateinit var testConsent: UserConsentRecord
    private lateinit var testPublicKey: java.security.PublicKey
    private lateinit var testEntity: com.chimali.fido2.data.database.Passkey_credential

    private companion object {
        private const val KEY_SIZE_256 = 256
        private const val AAGUID_SIZE_16 = 16
        private const val CRED_PROTECT_UV_REQUIRED = 3L
        private const val SIGN_COUNT_INCREMENT = 1L
    }

    @BeforeTest
    fun setUp() =
        runTest {
            passkeyCredentialDao = mockk()
            relyingPartyDao = mockk()
            userConsentRecordDao = mockk()
            publicKeyDecoder = mockk()
            timeProvider = mockk(relaxed = true)
            aggregateService =
                mockk {
                    coEvery { execute(any(), any()) } returns Result.success(mockk(relaxed = true))
                }
            val metadataProtectionService: com.chimali.fido2.data.service.CredentialMetadataProtectionService =
                mockk(relaxed = true) {
                    every { decryptCredentialMetadata(any(), any()) } answers {
                        CredentialMetadataProtectionService.CredentialMetadata(
                            "https://example.com".toCharArray(),
                            "user123".toCharArray(),
                        )
                    }
                }
            repository =
                CredentialRepositoryImpl(
                    passkeyCredentialDao,
                    relyingPartyDao,
                    userConsentRecordDao,
                    publicKeyDecoder,
                    timeProvider,
                    aggregateService,
                    metadataProtectionService,
                )

            val keyPairGenerator = KeyPairGenerator.getInstance("EC").apply { initialize(KEY_SIZE_256) }
            testPublicKey = keyPairGenerator.generateKeyPair().public

            testCredential =
                PasskeyCredential.create(
                    id = testCredentialIdFromLabel("dGVzdF9jcmVkZW50aWFsX2lk"),
                    rpId = RpId("https://example.com"),
                    userId = UserId("user123"),
                    userName = "testuser",
                    userDisplayName = "Test User",
                    publicKey = testPublicKey,
                    aaguid = ByteArray(AAGUID_SIZE_16),
                    credentialId = testCredentialIdFromLabel("dGVzdF9jcmVkZW50aWFsX2lk").toByteArray(),
                    derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
                )

            testEntity =
                com.chimali.fido2.data.database.Passkey_credential(
                    id = testCredential.id.encoded,
                    created_at = testCredential.createdAt.toEpochMilliseconds(),
                    last_used_at = testCredential.lastUsedAt.toEpochMilliseconds(),
                    updated_at = testCredential.createdAt.toEpochMilliseconds(),
                    aaguid =
                        java.util.Base64
                            .getEncoder()
                            .encodeToString(testCredential.aaguid),
                    cose_algorithm = PasskeyCredential.COSE_ES256.toLong(),
                    credential_id =
                        java.util.Base64
                            .getEncoder()
                            .encodeToString(testCredential.credentialId),
                    cred_protect_policy = testCredential.credProtectPolicy.toLong(),
                    derivation_profile = testCredential.derivationProfile,
                    label = null,
                    public_key =
                        java.util.Base64
                            .getEncoder()
                            .encodeToString(testPublicKey.encoded),
                    rp_id = "https://example.com".encodeToByteArray(),
                    rp_name = "example".encodeToByteArray(),
                    sign_count = 0L,
                    user_display_name = "Test User".encodeToByteArray(),
                    user_id = "user123".encodeToByteArray(),
                    user_name = "testuser".encodeToByteArray(),
                    rp_id_index = "rp:https://example.com".toByteArray(),
                    user_id_index = "user:user123".toByteArray(),
                    encrypted_metadata = byteArrayOf(1, 2, 3),
                )

            testRp =
                RelyingParty.create(
                    id = RpId("https://example.com"),
                    name = "Example Website",
                )

            testConsent =
                UserConsentRecord.create(
                    id = "1",
                    operationType = ConsentOperationType.REGISTRATION,
                    rpId = RpId("https://example.com"),
                    credentialId = testCredentialIdFromLabel("dGVzdF9jcmVkZW50aWFsX2lk"),
                    isBiometricUsed = true,
                    isPinUsed = false,
                    ipAddress = "192.168.1.1",
                    userAgent = "Test User Agent",
                    deviceId = "test_device_id",
                )

            // Common stubs
            coEvery { publicKeyDecoder.decodePublicKey(any(), any()) } returns Outcome.Success(testPublicKey)
            // Default Mocks
            coEvery { passkeyCredentialDao.insertCredential(any()) } just Runs
            coEvery { passkeyCredentialDao.insertCredentialProjection(testCredential) } just Runs
            coEvery { passkeyCredentialDao.getCredentialById(testCredential.id) } returns testEntity
            coEvery { passkeyCredentialDao.getCredentialsByRpText(any()) } returns flowOf(listOf())
            coEvery { passkeyCredentialDao.getCredentialsByUserId(any()) } returns flowOf(listOf())
            coEvery { passkeyCredentialDao.getCredentialsByRpIdAndUserId(any(), any()) } returns listOf()
            coEvery { passkeyCredentialDao.getAllCredentials() } returns flowOf(listOf())
            coEvery { passkeyCredentialDao.updateCredential(any()) } just Runs
            coEvery { passkeyCredentialDao.deleteCredential(testCredential.id) } just Runs
            coEvery { passkeyCredentialDao.updateSignCount(testCredential.id, any()) } just Runs
            coEvery { passkeyCredentialDao.updateLastUsedAt(testCredential.id) } just Runs
            coEvery { passkeyCredentialDao.getSignCount(testCredential.id) } returns 0L

            coEvery { relyingPartyDao.getRelyingPartyById(any()) } returns null
            coEvery { relyingPartyDao.insertOrUpdateRelyingParty(any()) } just Runs
            coEvery { relyingPartyDao.updateRelyingParty(any()) } just Runs
            coEvery { relyingPartyDao.getAllRelyingParties() } returns flowOf()

            coEvery { userConsentRecordDao.insertConsent(any()) } returns Unit
            coEvery { userConsentRecordDao.getRecentConsent(any(), any()) } returns flowOf()
        }

    @Test
    fun `rejected credential batch admission erases the untransferred credential`() {
        val candidate = testCredential.copy()
        val originalCredentialId = candidate.credentialId.copyOf()
        val rpText = candidate.rpId.copyCharacters()
        val userIdText = candidate.userId.copyCharacters()
        val userNameText = candidate.userName.copyCharacters()
        val displayNameText = candidate.userDisplayName.copyCharacters()

        try {
            assertFailsWith<IllegalStateException> {
                mutableListOf<PasskeyCredential>().admitCredential(candidate) { false }
            }

            val erasedRpText = candidate.rpId.copyCharacters()
            val erasedUserIdText = candidate.userId.copyCharacters()
            val erasedUserNameText = candidate.userName.copyCharacters()
            val erasedDisplayNameText = candidate.userDisplayName.copyCharacters()
            try {
                assertContentEquals(CharArray(rpText.size), erasedRpText)
                assertContentEquals(CharArray(userIdText.size), erasedUserIdText)
                assertContentEquals(CharArray(userNameText.size), erasedUserNameText)
                assertContentEquals(CharArray(displayNameText.size), erasedDisplayNameText)
            } finally {
                erasedRpText.fill('\u0000')
                erasedUserIdText.fill('\u0000')
                erasedUserNameText.fill('\u0000')
                erasedDisplayNameText.fill('\u0000')
            }
            assertContentEquals(ByteArray(originalCredentialId.size), candidate.credentialId)
            assertTrue(testCredential.rpId.contentEquals("https://example.com"))
        } finally {
            originalCredentialId.fill(0)
            rpText.fill('\u0000')
            userIdText.fill('\u0000')
            userNameText.fill('\u0000')
            displayNameText.fill('\u0000')
            candidate.eraseSensitiveText()
        }
    }

    @Test
    fun `rejected summary batch admission erases the untransferred summary`() {
        val summary =
            CredentialSummary.fromRpId(
                id = testCredential.id.encoded,
                rpId = RpId("https://example.com"),
                credentialId = testCredential.id,
                lastUsedAt = testCredential.lastUsedAt,
                coseAlgorithm = PasskeyCredential.COSE_ES256,
                derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
                storedPublicKeyEncoding = testPublicKey.encoded.copyOf(),
            )
        val rpText = summary.rpIdText.copyCharacters()

        try {
            assertFailsWith<IllegalStateException> {
                mutableListOf<CredentialSummary>().admitCredentialSummary(summary) { false }
            }

            val erasedRpText = summary.rpIdText.copyCharacters()
            try {
                assertContentEquals(CharArray(rpText.size), erasedRpText)
            } finally {
                erasedRpText.fill('\u0000')
            }
            assertTrue(testCredential.rpId.contentEquals("https://example.com"))
        } finally {
            rpText.fill('\u0000')
            summary.eraseSensitiveText()
        }
    }

    @Test
    fun `result wrapper construction failure erases owned credential batch`() {
        val candidate = testCredential.copy()
        val originalRpText = candidate.rpId.copyCharacters()

        try {
            assertFailsWith<IllegalStateException> {
                createOwnedBatchResult(listOf(candidate), PasskeyCredential::eraseSensitiveText) {
                    throw IllegalStateException("synthetic result construction failure")
                }
            }

            val observedRpText = candidate.rpId.copyCharacters()
            try {
                assertContentEquals(CharArray(originalRpText.size), observedRpText)
            } finally {
                observedRpText.fill('\u0000')
            }
            assertTrue(testCredential.rpId.contentEquals("https://example.com"))
        } finally {
            originalRpText.fill('\u0000')
            candidate.eraseSensitiveText()
        }
    }

    @Test
    fun `result wrapper construction failure erases owned summary batch`() {
        val summary =
            CredentialSummary.fromRpId(
                id = testCredential.id.encoded,
                rpId = RpId("https://example.com"),
                credentialId = testCredential.id,
                lastUsedAt = testCredential.lastUsedAt,
                coseAlgorithm = PasskeyCredential.COSE_ES256,
                derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
                storedPublicKeyEncoding = testPublicKey.encoded.copyOf(),
            )
        val originalRpText = summary.rpIdText.copyCharacters()

        try {
            assertFailsWith<IllegalStateException> {
                createOwnedBatchResult(listOf(summary), CredentialSummary::eraseSensitiveText) {
                    throw IllegalStateException("synthetic result construction failure")
                }
            }

            val observedRpText = summary.rpIdText.copyCharacters()
            try {
                assertContentEquals(CharArray(originalRpText.size), observedRpText)
            } finally {
                observedRpText.fill('\u0000')
            }
            assertTrue(testCredential.rpId.contentEquals("https://example.com"))
        } finally {
            originalRpText.fill('\u0000')
            summary.eraseSensitiveText()
        }
    }

    @Test
    fun `update relying party erases borrowed and transferred owners after successful update`() =
        runTest {
            coEvery { relyingPartyDao.getRelyingPartyByText(any()) } returns testRpEntity()
            var borrowed: RelyingParty? = null
            var transferred: RelyingParty? = null

            val result =
                repository.updateRelyingParty(RpId("https://example.com")) { current ->
                    borrowed = current
                    current.copy(name = "Updated example").also { transferred = it }
                }

            assertIs<Outcome.Success<*>>(result)
            assertSensitiveTextErased(requireNotNull(borrowed))
            assertSensitiveTextErased(requireNotNull(transferred))
            coVerify(exactly = 1) { relyingPartyDao.updateRelyingParty(any()) }
        }

    @Test
    fun `update relying party erases identity result exactly once on completion`() =
        runTest {
            coEvery { relyingPartyDao.getRelyingPartyByText(any()) } returns testRpEntity()
            var borrowed: RelyingParty? = null

            val result =
                repository.updateRelyingParty(RpId("https://example.com")) { current ->
                    borrowed = current
                    current
                }

            assertIs<Outcome.Success<*>>(result)
            assertSensitiveTextErased(requireNotNull(borrowed))
            coVerify(exactly = 1) { relyingPartyDao.updateRelyingParty(any()) }
        }

    @Test
    fun `update relying party erases borrowed owner when callback fails`() =
        runTest {
            coEvery { relyingPartyDao.getRelyingPartyByText(any()) } returns testRpEntity()
            var borrowed: RelyingParty? = null

            assertFailsWith<IllegalStateException> {
                repository.updateRelyingParty(RpId("https://example.com")) { current ->
                    borrowed = current
                    error("synthetic callback failure")
                }
            }

            assertSensitiveTextErased(requireNotNull(borrowed))
            coVerify(exactly = 0) { relyingPartyDao.updateRelyingParty(any()) }
        }

    @Test
    fun `update relying party propagates database shaped callback failure and erases borrowed owner`() =
        runTest {
            coEvery { relyingPartyDao.getRelyingPartyByText(any()) } returns testRpEntity()
            var borrowed: RelyingParty? = null
            val callbackFailure = android.database.SQLException("synthetic callback failure")

            val thrown =
                assertFailsWith<android.database.SQLException> {
                    repository.updateRelyingParty(RpId("https://example.com")) { current ->
                        borrowed = current
                        throw callbackFailure
                    }
                }

            assertSame(callbackFailure, thrown)
            assertSensitiveTextErased(requireNotNull(borrowed))
            coVerify(exactly = 0) { relyingPartyDao.updateRelyingParty(any()) }
        }

    @Test
    fun `update relying party erases both owners when DAO fails`() =
        runTest {
            coEvery { relyingPartyDao.getRelyingPartyByText(any()) } returns testRpEntity()
            coEvery { relyingPartyDao.updateRelyingParty(any()) } throws android.database.SQLException("synthetic")
            var borrowed: RelyingParty? = null
            var transferred: RelyingParty? = null

            val result =
                repository.updateRelyingParty(RpId("https://example.com")) { current ->
                    borrowed = current
                    current.copy(name = "Updated example").also { transferred = it }
                }

            assertIs<Outcome.Error<*>>(result)
            assertSensitiveTextErased(requireNotNull(borrowed))
            assertSensitiveTextErased(requireNotNull(transferred))
        }

    @Test
    fun `update relying party erases both owners and propagates cancellation`() =
        runTest {
            coEvery { relyingPartyDao.getRelyingPartyByText(any()) } returns testRpEntity()
            coEvery { relyingPartyDao.updateRelyingParty(any()) } throws CancellationException("synthetic cancel")
            var borrowed: RelyingParty? = null
            var transferred: RelyingParty? = null

            assertFailsWith<CancellationException> {
                repository.updateRelyingParty(RpId("https://example.com")) { current ->
                    borrowed = current
                    current.copy(name = "Updated example").also { transferred = it }
                }
            }

            assertSensitiveTextErased(requireNotNull(borrowed))
            assertSensitiveTextErased(requireNotNull(transferred))
        }

    private fun testRpEntity() =
        Relying_party(
            id = "https://example.com".encodeToByteArray(),
            created_at = 1_000L,
            last_used_at = 2_000L,
            updated_at = 2_000L,
            credential_count = 0L,
            encrypted_metadata = null,
            icon_url = null,
            is_blocked = 0L,
            name = "Example Website".encodeToByteArray(),
        )

    private fun assertSensitiveTextErased(rp: RelyingParty) {
        listOf(rp.idText, rp.nameText).forEach { owner ->
            val characters = owner.copyCharacters()
            try {
                assertTrue(characters.all { it == '\u0000' })
            } finally {
                characters.fill('\u0000')
            }
        }
    }

    @Nested
    inner class CredentialManagementTests {
        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @ParameterizedTest
        @ValueSource(strings = ["rp-read", "user-read", "search", "projection", "event", "summary", "rp-save"])
        fun `repository diagnostics and errors never expose private metadata or exception messages`(mode: String) =
            runTest {
                val marker = "synthetic-private-marker"
                val failure = android.database.SQLException(marker).apply { initCause(IllegalStateException(marker)) }
                var returnedError: DomainError? = null
                CapturedDiagnostics().use { logs ->
                    when (mode) {
                        "rp-read" -> {
                            every { passkeyCredentialDao.getCredentialsByRpText(any()) } throws failure
                            assertTrue(repository.getCredentialsByRpId(RpId("$marker.example.com")).toList().isEmpty())
                        }
                        "user-read" -> {
                            every { passkeyCredentialDao.getCredentialsByUserId(any()) } throws failure
                            assertTrue(repository.getCredentialsByUserId(UserId(marker)).toList().isEmpty())
                        }
                        "search" -> {
                            every { passkeyCredentialDao.getAllCredentials() } throws failure
                            val query = SensitiveCredentialText.copyOf(marker)
                            try {
                                assertTrue(repository.searchCredentials(query).toList().isEmpty())
                            } finally {
                                query.erase()
                            }
                        }
                        "projection" -> {
                            coEvery { passkeyCredentialDao.insertCredentialProjection(any()) } throws failure
                            returnedError = assertIs<Outcome.Error<*>>(repository.saveCredential(testCredential)).error
                        }
                        "event" -> {
                            coEvery { aggregateService.execute(any(), any()) } returns
                                Result.failure(IllegalStateException(marker))
                            returnedError = assertIs<Outcome.Error<*>>(repository.saveCredential(testCredential)).error
                        }
                        "summary" -> {
                            every { passkeyCredentialDao.getCredentialsByRpText(any()) } throws
                                IllegalArgumentException(marker)
                            returnedError =
                                assertIs<Outcome.Error<*>>(
                                    repository.getCredentialSummariesForRp(RpId("$marker.example.com")),
                                ).error
                        }
                        "rp-save" -> {
                            coEvery { relyingPartyDao.insertOrUpdateRelyingParty(any()) } throws failure
                            returnedError = assertIs<Outcome.Error<*>>(repository.saveRelyingParty(testRp)).error
                        }
                    }
                    returnedError?.let {
                        assertTrue(marker !in it.message)
                        assertNull(it.cause)
                    }
                    assertTrue(logs.size > 0 || mode == "event")
                    logs.assertNoSensitiveValues(marker)
                }
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `invalid hydrated row returns null without escaping validation`() =
            runTest {
                coEvery { passkeyCredentialDao.getCredentialById(testCredential.id) } returns
                    testEntity.copy(derivation_profile = "invalid-current-profile")

                assertNull(repository.getCredentialById(testCredential.id))
                coVerify(exactly = 0) { passkeyCredentialDao.updateCredential(any()) }
                coVerify(exactly = 0) { aggregateService.execute(any(), any()) }
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `get credential by ID propagates mapper SQL failure after erasing source row`() =
            runTest {
                val row = testEntity.copyCredentialRow()
                val mapperFailure = android.database.SQLException("synthetic mapper failure")
                coEvery { passkeyCredentialDao.getCredentialById(testCredential.id) } returns row
                coEvery { publicKeyDecoder.decodePublicKey(any(), any()) } throws mapperFailure

                val thrown =
                    assertFailsWith<android.database.SQLException> {
                        repository.getCredentialById(testCredential.id)
                    }

                assertSame(mapperFailure, thrown)
                listOf(row.rp_id, row.rp_name, row.user_id, row.user_name, row.user_display_name).forEach { bytes ->
                    assertTrue(bytes.all { it == 0.toByte() })
                }
                row.encrypted_metadata?.let { bytes -> assertTrue(bytes.all { it == 0.toByte() }) }
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @ParameterizedTest
        @ValueSource(strings = ["page", "rp-batch"])
        fun `credential batch hydration propagates mapper SQL failure after erasing source rows`(path: String) =
            runTest {
                val row = testEntity.copyCredentialRow()
                val mapperFailure = android.database.SQLException("synthetic batch mapper failure")
                every { publicKeyDecoder.decodePublicKey(any(), any()) } throws mapperFailure
                if (path == "page") {
                    coEvery { passkeyCredentialDao.getPagedCredentials(any(), any()) } returns listOf(row)
                } else {
                    coEvery { passkeyCredentialDao.getCredentialsByRpText(any()) } returns flowOf(listOf(row))
                }

                val thrown =
                    assertFailsWith<android.database.SQLException> {
                        if (path == "page") {
                            repository.getPagedCredentials(10, 0)
                        } else {
                            repository.getCredentialsForRp(RpId(testCredential.rpId.toBoundaryString()))
                        }
                    }

                assertSame(mapperFailure, thrown)
                listOf(row.rp_id, row.rp_name, row.user_id, row.user_name, row.user_display_name).forEach { bytes ->
                    assertTrue(bytes.all { it == 0.toByte() })
                }
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @ParameterizedTest
        @ValueSource(strings = ["by-ids", "creation-validation"])
        fun `credential ID and validation wrappers propagate mapper SQL failure after erasing rows`(path: String) =
            runTest {
                val row = testEntity.copyCredentialRow()
                val mapperFailure = android.database.SQLException("synthetic wrapped mapper failure")
                every { publicKeyDecoder.decodePublicKey(any(), any()) } throws mapperFailure
                if (path == "by-ids") {
                    coEvery { passkeyCredentialDao.getCredentialById(testCredential.id) } returns row
                } else {
                    coEvery { passkeyCredentialDao.getCredentialsByRpText(any()) } returns flowOf(listOf(row))
                }

                val thrown =
                    assertFailsWith<android.database.SQLException> {
                        if (path == "by-ids") {
                            repository.getCredentialsByIds(setOf(testCredential.id), null)
                        } else {
                            repository.validateCredentialCreation(
                                RpId(testCredential.rpId.toBoundaryString()),
                                UserId(testCredential.userId.toBoundaryString()),
                            )
                        }
                    }

                assertSame(mapperFailure, thrown)
                listOf(row.rp_id, row.rp_name, row.user_id, row.user_name, row.user_display_name).forEach { bytes ->
                    assertTrue(bytes.all { it == 0.toByte() })
                }
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `mixed valid and malformed rows preserve healthy list and search results`() =
            runTest {
                val malformedRow =
                    testEntity
                        .copyCredentialRow()
                        .copy(user_name = byteArrayOf(0xC3.toByte(), 0x28))
                val rows = listOf(malformedRow, testEntity.copyCredentialRow())
                val returnedRows = mutableListOf<Passkey_credential>()

                fun freshRows(): List<Passkey_credential> = rows.map { it.copyCredentialRow().also(returnedRows::add) }
                coEvery { passkeyCredentialDao.getAllCredentials() } answers {
                    flowOf(freshRows())
                }
                coEvery { passkeyCredentialDao.getCredentialsByRpText(any()) } answers {
                    flowOf(freshRows())
                }
                coEvery { passkeyCredentialDao.getCredentialsByUserId(any()) } answers {
                    flowOf(freshRows())
                }
                coEvery { passkeyCredentialDao.getPagedCredentials(any(), any()) } answers {
                    freshRows()
                }

                assertEquals(listOf(testCredential.id), repository.getAllCredentials().toList().map { it.id })
                val query = SensitiveCredentialText.copyOf("test")
                try {
                    assertEquals(listOf(testCredential.id), repository.searchCredentials(query).toList().map { it.id })
                    assertTrue(query.contentEquals("test"), "repository borrows the query owner")
                } finally {
                    query.erase()
                }
                assertEquals(
                    listOf(testCredential.id),
                    repository
                        .getCredentialsByRpId(RpId(testCredential.rpId.toBoundaryString()))
                        .toList()
                        .map { it.id },
                )
                assertEquals(
                    listOf(testCredential.id),
                    repository
                        .getCredentialsByUserId(UserId(testCredential.userId.toBoundaryString()))
                        .toList()
                        .map { it.id },
                )
                val page = assertIs<Outcome.Success<List<PasskeyCredential>>>(repository.getPagedCredentials(10, 0))
                assertEquals(listOf(testCredential.id), page.data.map { it.id })
                returnedRows.forEach { row ->
                    listOf(row.rp_id, row.rp_name, row.user_id, row.user_name, row.user_display_name).forEach { bytes ->
                        assertContentEquals(ByteArray(bytes.size), bytes)
                    }
                }
                coVerify(exactly = 0) { passkeyCredentialDao.updateCredential(any()) }
                coVerify(exactly = 0) { aggregateService.execute(any(), any()) }
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `search erases transferred credential when downstream rejects emission`() =
            runTest {
                val row = testEntity.copyCredentialRow()
                coEvery { passkeyCredentialDao.getAllCredentials() } returns flowOf(listOf(row))
                val query = SensitiveCredentialText.copyOf("test")
                var emitted: PasskeyCredential? = null
                var downstreamFailureObserved = false

                try {
                    repository.searchCredentials(query).collect { credential ->
                        emitted = credential
                        throw IllegalStateException("synthetic downstream rejection")
                    }
                } catch (failure: IllegalStateException) {
                    downstreamFailureObserved = failure.message == "synthetic downstream rejection"
                } finally {
                    query.erase()
                }

                assertTrue(downstreamFailureObserved)
                val credential = requireNotNull(emitted)
                listOf(
                    credential.rpId,
                    credential.userId,
                    credential.userName,
                    credential.userDisplayName,
                ).forEach { owner ->
                    val copy = owner.copyCharacters()
                    try {
                        assertContentEquals(CharArray(copy.size), copy)
                    } finally {
                        copy.fill('\u0000')
                    }
                }
                listOf(row.rp_id, row.rp_name, row.user_id, row.user_name, row.user_display_name).forEach { bytes ->
                    assertContentEquals(ByteArray(bytes.size), bytes)
                }
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `summary rejects numeric algorithm truncation without public key hydration`() =
            runTest {
                coEvery { passkeyCredentialDao.getCredentialsByRpText(any()) } returns
                    flowOf(listOf(testEntity.copy(cose_algorithm = 4_294_967_289L)))

                assertIs<Outcome.Error<*>>(
                    repository.getCredentialSummariesForRp(RpId(testCredential.rpId.toBoundaryString())),
                )
                coVerify(exactly = 0) { publicKeyDecoder.decodePublicKey(any(), any()) }
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `summary rejects unsupported persisted credential-protection policies before narrowing`() =
            runTest {
                listOf(Long.MIN_VALUE, -1L, 0L, 4L, 4_294_967_297L, Long.MAX_VALUE).forEach { rawPolicy ->
                    val invalid = testEntity.copyCredentialRow(credProtectPolicy = rawPolicy)
                    val rowOwners =
                        listOf(
                            invalid.rp_id,
                            invalid.rp_name,
                            invalid.user_id,
                            invalid.user_name,
                            invalid.user_display_name,
                        )
                    coEvery { passkeyCredentialDao.getCredentialsByRpText(any()) } returns flowOf(listOf(invalid))

                    val failure = repository.getCredentialSummariesForRp(RpId(testCredential.rpId.toBoundaryString()))

                    assertIs<Outcome.Error<*>>(failure)
                    rowOwners.forEach { bytes -> assertContentEquals(ByteArray(bytes.size), bytes) }
                }
                coVerify(exactly = 0) { publicKeyDecoder.decodePublicKey(any(), any()) }
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `sign counter read failure is returned instead of resetting to zero`() =
            runTest {
                coEvery { passkeyCredentialDao.getSignCount(testCredential.id) } throws
                    android.database.SQLException("synthetic-counter-read-failure")

                val result = repository.getSignCount(testCredential.id)

                val error = assertIs<DomainError.DatabaseError>(assertIs<Outcome.Error<*>>(result).error)
                assertEquals("Failed to read credential signing counter", error.message)
                assertNull(error.cause)
                coVerify(exactly = 1) { passkeyCredentialDao.getSignCount(testCredential.id) }
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `sign counter event failure does not update the SQL projection`() =
            runTest {
                coEvery {
                    aggregateService.execute(testCredential.id.encoded, any())
                } returns Result.failure(IllegalStateException("synthetic-event-append-failure"))

                val result = repository.updateSignCount(testCredential.id, SIGN_COUNT_INCREMENT)

                val error = assertIs<DomainError.DatabaseError>(assertIs<Outcome.Error<*>>(result).error)
                assertEquals("Failed to persist credential authentication event", error.message)
                assertNull(error.cause)
                coVerify(exactly = 1) { aggregateService.execute(testCredential.id.encoded, any()) }
                coVerify(exactly = 0) {
                    passkeyCredentialDao.updateSignCount(testCredential.id, SIGN_COUNT_INCREMENT)
                }
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `credential deletion event failure leaves SQL projection untouched`() =
            runTest {
                coEvery { aggregateService.execute(testCredential.id.encoded, any()) } returns
                    Result.failure(IllegalStateException("synthetic-event-append-failure"))

                val result = repository.deleteCredential(testCredential.id)

                val error = assertIs<DomainError.DatabaseError>(assertIs<Outcome.Error<*>>(result).error)
                assertEquals("Failed to persist credential deletion event", error.message)
                coVerify(exactly = 0) { passkeyCredentialDao.deleteCredential(testCredential.id) }
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `last used event failure leaves SQL projection untouched`() =
            runTest {
                coEvery { aggregateService.execute(testCredential.id.encoded, any()) } returns
                    Result.failure(IllegalStateException("synthetic-event-append-failure"))

                val result = repository.updateLastUsedAt(testCredential.id)

                val error = assertIs<DomainError.DatabaseError>(assertIs<Outcome.Error<*>>(result).error)
                assertEquals("Failed to persist credential last-used event", error.message)
                coVerify(exactly = 0) { passkeyCredentialDao.updateLastUsedAt(testCredential.id) }
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `deletion projection failure retains the already appended event`() =
            runTest {
                coEvery { passkeyCredentialDao.deleteCredential(testCredential.id) } throws
                    android.database.SQLException("synthetic-projection-failure")

                val result = repository.deleteCredential(testCredential.id)

                assertIs<Outcome.Error<*>>(result)
                coVerify(exactly = 1) { aggregateService.execute(testCredential.id.encoded, any()) }
                coVerify(exactly = 1) { passkeyCredentialDao.deleteCredential(testCredential.id) }
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `bulk deletion reports middle event failure and stops after committed items`() =
            runTest {
                val firstId = CredentialId.fromByteArray(ByteArray(CredentialId.MIN_CREDENTIAL_ID_BYTES) { 1 })
                val secondId = CredentialId.fromByteArray(ByteArray(CredentialId.MIN_CREDENTIAL_ID_BYTES) { 2 })
                coEvery { passkeyCredentialDao.deleteCredential(firstId) } just Runs
                coEvery { passkeyCredentialDao.getAllCredentials() } returns
                    flowOf(
                        listOf(
                            testEntity.copyCredentialRow(id = firstId),
                            testEntity.copyCredentialRow(id = secondId),
                        ),
                    )
                var eventNumber = 0
                coEvery { aggregateService.execute(any(), any()) } answers {
                    eventNumber++
                    if (eventNumber == 2) {
                        Result.failure(IllegalStateException("synthetic-middle-event-failure"))
                    } else {
                        Result.success(mockk(relaxed = true))
                    }
                }

                val result = repository.deleteAllCredentials()

                val error = assertIs<DomainError.DatabaseError>(assertIs<Outcome.Error<*>>(result).error)
                assertEquals("Failed to persist credential deletion event", error.message)
                coVerify(exactly = 2) { aggregateService.execute(any(), any()) }
                coVerify(exactly = 1) { passkeyCredentialDao.deleteCredential(firstId) }
                coVerify(exactly = 0) { passkeyCredentialDao.deleteCredential(secondId) }
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `deletion cancellation propagates without changing SQL projection`() =
            runTest {
                coEvery { aggregateService.execute(testCredential.id.encoded, any()) } returns
                    Result.failure(CancellationException("synthetic-event-cancellation"))

                assertFailsWith<CancellationException> { repository.deleteCredential(testCredential.id) }

                coVerify(exactly = 0) { passkeyCredentialDao.deleteCredential(testCredential.id) }
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `sign counter projection failure returns a fixed error after the event append`() =
            runTest {
                val commandSlot = slot<PasskeyCommand>()
                val exceptionMarker = "synthetic-counter-projection-failure"
                coEvery {
                    passkeyCredentialDao.updateSignCount(testCredential.id, SIGN_COUNT_INCREMENT)
                } throws android.database.SQLException(exceptionMarker)

                val result = repository.updateSignCount(testCredential.id, SIGN_COUNT_INCREMENT)

                val error = assertIs<DomainError.DatabaseError>(assertIs<Outcome.Error<*>>(result).error)
                assertEquals("Failed to update credential signing counter", error.message)
                assertNull(error.cause)
                assertTrue(exceptionMarker !in error.message)
                coVerifyOrder {
                    aggregateService.execute(testCredential.id.encoded, capture(commandSlot))
                    passkeyCredentialDao.updateSignCount(testCredential.id, SIGN_COUNT_INCREMENT)
                }
                assertIs<PasskeyCommand.Authenticate>(commandSlot.captured)
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `should successfully save credential`() =
            runTest {
                val result = repository.saveCredential(testCredential)
                assertTrue(result.isSuccess, "Result was $result")
                coVerifyOrder {
                    aggregateService.execute(testCredential.id.encoded, any())
                    passkeyCredentialDao.insertCredentialProjection(testCredential)
                }
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `registration command borrows mutable copies and erases them after dispatch`() =
            runTest {
                lateinit var command: PasskeyCommand.Register
                coEvery { aggregateService.execute(any(), any()) } coAnswers {
                    command = secondArg<PasskeyCommand.Register>()
                    assertContentEquals("https://example.com".toCharArray(), command.rpId)
                    assertContentEquals("user123".toCharArray(), command.userId)
                    assertContentEquals("testuser".toCharArray(), command.userName)
                    assertContentEquals("Test User".toCharArray(), command.userDisplayName)
                    Result.success(mockk(relaxed = true))
                }

                assertTrue(repository.saveCredential(testCredential).isSuccess)

                assertContentEquals(CharArray(command.rpId.size), command.rpId)
                assertContentEquals(CharArray(command.userId.size), command.userId)
                assertContentEquals(CharArray(command.userName.size), command.userName)
                assertContentEquals(CharArray(command.userDisplayName.size), command.userDisplayName)
                assertEquals("https://example.com", testCredential.rpId.toBoundaryString())
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `discarded aggregate states are erased after credential repository operations`() =
            runTest {
                val returnedStates = mutableListOf<PasskeyState>()
                coEvery { aggregateService.execute(any(), any()) } coAnswers {
                    val state =
                        PasskeyState(
                            rpId = "synthetic-rp".toCharArray(),
                            userId = "synthetic-id".toCharArray(),
                            userName = "synthetic-name".toCharArray(),
                            userDisplayName = "synthetic-display".toCharArray(),
                        )
                    returnedStates += state
                    Result.success(state)
                }

                repository.saveCredential(testCredential)
                repository.deleteCredential(testCredential.id)
                repository.updateSignCount(testCredential.id, SIGN_COUNT_INCREMENT)
                repository.updateLastUsedAt(testCredential.id)

                assertEquals(4, returnedStates.size)
                returnedStates.forEach { state ->
                    assertContentEquals(CharArray(state.rpId.size), state.rpId)
                    assertContentEquals(CharArray(state.userId.size), state.userId)
                    assertContentEquals(CharArray(state.userName.size), state.userName)
                    assertContentEquals(CharArray(state.userDisplayName.size), state.userDisplayName)
                }
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `credential batch cancellation clears row buffers and propagates unchanged`() =
            runTest {
                val rows = listOf(testEntity.copyCredentialRow(), testEntity.copyCredentialRow())
                val cancellation = CancellationException("synthetic batch cancellation")
                coEvery { passkeyCredentialDao.getCredentialsByRpText(any()) } returns flowOf(rows)
                every {
                    publicKeyDecoder.decodePublicKey(
                        any(),
                        any(),
                    )
                } returns Outcome.Success(testPublicKey) andThenThrows
                    cancellation

                assertEquals(
                    cancellation,
                    assertFailsWith<CancellationException> {
                        repository.getCredentialsForRp(RpId(testCredential.rpId.toBoundaryString()))
                    },
                )

                rows.forEach { row ->
                    listOf(row.rp_id, row.rp_name, row.user_id, row.user_name, row.user_display_name).forEach { bytes ->
                        assertContentEquals(ByteArray(bytes.size), bytes)
                    }
                }
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `search propagates database-shaped downstream failure after erasing owners`() =
            runTest {
                val row = testEntity.copyCredentialRow()
                coEvery { passkeyCredentialDao.getAllCredentials() } returns flowOf(listOf(row))
                val query = SensitiveCredentialText.copyOf("test")
                var emitted: PasskeyCredential? = null
                val downstreamFailure = android.database.SQLException("synthetic downstream failure")

                try {
                    val failure =
                        assertFailsWith<android.database.SQLException> {
                            repository.searchCredentials(query).collect { credential ->
                                emitted = credential
                                throw downstreamFailure
                            }
                        }
                    assertTrue(failure === downstreamFailure)
                } finally {
                    query.erase()
                }

                val credential = requireNotNull(emitted)
                listOf(
                    credential.rpId,
                    credential.userId,
                    credential.userName,
                    credential.userDisplayName,
                ).forEach { owner ->
                    val copy = owner.copyCharacters()
                    try {
                        assertContentEquals(CharArray(copy.size), copy)
                    } finally {
                        copy.fill('\u0000')
                    }
                }
                listOf(row.rp_id, row.rp_name, row.user_id, row.user_name, row.user_display_name).forEach { bytes ->
                    assertContentEquals(ByteArray(bytes.size), bytes)
                }
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `projection failure is returned after the event append succeeds`() =
            runTest {
                val commandSlot = slot<PasskeyCommand>()
                coEvery { passkeyCredentialDao.insertCredentialProjection(testCredential) } throws
                    android.database.SQLException("projection failed")

                val result = repository.saveCredential(testCredential)

                assertTrue(result is Outcome.Error)
                coVerifyOrder {
                    aggregateService.execute(testCredential.id.encoded, capture(commandSlot))
                    passkeyCredentialDao.insertCredentialProjection(testCredential)
                }
                val registration = commandSlot.captured as PasskeyCommand.Register
                assertEquals(testCredential.coseAlgorithm, registration.coseAlgorithm)
                assertEquals(testCredential.derivationProfile, registration.derivationProfile)
                assertContentEquals(ByteArray(registration.credentialId.size), registration.credentialId)
                assertEquals(
                    java.util.Base64
                        .getEncoder()
                        .encodeToString(testCredential.publicKey.encoded),
                    registration.publicKey,
                )
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `projection cancellation propagates after the registration event phase`() =
            runTest {
                coEvery { passkeyCredentialDao.insertCredentialProjection(testCredential) } throws
                    CancellationException("synthetic projection cancellation")

                assertFailsWith<CancellationException> {
                    repository.saveCredential(testCredential)
                }

                coVerifyOrder {
                    aggregateService.execute(testCredential.id.encoded, any())
                    passkeyCredentialDao.insertCredentialProjection(testCredential)
                }
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `should retrieve credential by id`() =
            runTest {
                coEvery { passkeyCredentialDao.getCredentialById(testCredential.id) } returns testEntity
                val result = repository.getCredentialById(testCredential.id)
                assertNotNull(result)
                assertEquals(testCredential.id, result.id)
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `should return null when credential not found`() =
            runTest {
                val unknownId = testCredentialIdFromLabel("bm9uZXhpc3RlbnQAYm05dVpYaHBjM1JsYm5R")
                coEvery { passkeyCredentialDao.getCredentialById(unknownId) } returns null
                val result = repository.getCredentialById(unknownId)
                assertNull(result)
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `should retrieve credentials by rp id`() =
            runTest {
                coEvery {
                    passkeyCredentialDao.getCredentialsByRpText(match { it.contentEquals(testCredential.rpId) })
                } returns
                    flowOf(listOf(testEntity))

                val flow = repository.getCredentialsByRpId(RpId(testCredential.rpId.toBoundaryString()))
                val result = mutableListOf<PasskeyCredential>()
                flow.collect { result.add(it) }

                assertEquals(1, result.size)
                assertEquals(testCredential.id, result.first().id)
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `should retrieve all credentials`() =
            runTest {
                coEvery { passkeyCredentialDao.getAllCredentials() } returns flowOf(listOf(testEntity))

                val flow = repository.getAllCredentials()
                val result = mutableListOf<PasskeyCredential>()
                flow.collect { result.add(it) }

                assertEquals(1, result.size)
                assertEquals(testCredential.id, result.first().id)
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `get all credentials keeps an admitted child borrower readable until it joins`() =
            runTest {
                val row = testEntity.copyCredentialRow()
                coEvery { passkeyCredentialDao.getAllCredentials() } returns flowOf(listOf(row))
                var retained: PasskeyCredential? = null

                repository.getAllCredentials().collect { credential ->
                    retained = credential
                    coroutineScope {
                        val admitted = CompletableDeferred<Unit>()
                        val release = CompletableDeferred<Unit>()
                        val borrower =
                            launch {
                                admitted.complete(Unit)
                                release.await()
                                assertTrue(credential.userName.contentEquals("testuser"))
                                assertTrue(credential.userDisplayName.contentEquals("Test User"))
                            }

                        admitted.await()
                        assertTrue(credential.userName.contentEquals("testuser"))
                        release.complete(Unit)
                        borrower.join()
                    }
                    assertTrue(credential.userName.contentEquals("testuser"))
                }

                listOfNotNull(
                    row.rp_id,
                    row.rp_name,
                    row.user_id,
                    row.user_name,
                    row.user_display_name,
                    row.rp_id_index,
                    row.user_id_index,
                    row.encrypted_metadata,
                ).forEach { bytes -> assertContentEquals(ByteArray(bytes.size), bytes) }
                val transferred = requireNotNull(retained)
                assertTrue(transferred.userName.contentEquals("testuser"))
                transferred.eraseSensitiveText()
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `independent cold flow collectors receive independently owned credentials`() =
            runTest {
                coEvery { passkeyCredentialDao.getAllCredentials() } answers {
                    flow { emit(listOf(testEntity.copyCredentialRow())) }
                }
                val owners = mutableListOf<PasskeyCredential>()
                val credentials = repository.getAllCredentials()

                credentials.collect(owners::add)
                credentials.collect(owners::add)

                assertEquals(2, owners.size)
                assertTrue(owners[0] !== owners[1])
                assertTrue(owners[0].userName.contentEquals("testuser"))
                assertTrue(owners[1].userName.contentEquals("testuser"))

                owners[0].eraseSensitiveText()

                assertTrue(owners[0].userName.all { it == '\u0000' })
                assertTrue(owners[1].userName.contentEquals("testuser"))
                owners[1].eraseSensitiveText()
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `concurrent cold flow collectors keep independent credential and query owners`() =
            runTest {
                coEvery { passkeyCredentialDao.getAllCredentials() } answers {
                    flow { emit(listOf(testEntity.copyCredentialRow())) }
                }
                val query = SensitiveCredentialText.copyOf("test")
                val firstReceived = CompletableDeferred<PasskeyCredential>()
                val secondReceived = CompletableDeferred<PasskeyCredential>()
                val releaseFirst = CompletableDeferred<Unit>()
                val releaseSecond = CompletableDeferred<Unit>()
                var firstCredential: PasskeyCredential? = null
                var secondCredential: PasskeyCredential? = null
                val firstCollector =
                    launch {
                        repository.searchCredentials(query).collect { credential ->
                            firstCredential = credential
                            firstReceived.complete(credential)
                            releaseFirst.await()
                            credential.eraseSensitiveText()
                        }
                    }
                val secondCollector =
                    launch {
                        repository.searchCredentials(query).collect { credential ->
                            secondCredential = credential
                            secondReceived.complete(credential)
                            releaseSecond.await()
                            credential.eraseSensitiveText()
                        }
                    }

                try {
                    val first = firstReceived.await()
                    val second = secondReceived.await()
                    assertTrue(first !== second)
                    assertTrue(first.userName.contentEquals("testuser"))
                    assertTrue(second.userName.contentEquals("testuser"))

                    firstCollector.cancel()
                    firstCollector.join()

                    assertTrue(first.userName.all { it == '\u0000' })
                    assertTrue(second.userName.contentEquals("testuser"))
                    val queryCopy = query.copyCharacters()
                    try {
                        assertContentEquals("test".toCharArray(), queryCopy)
                    } finally {
                        queryCopy.fill('\u0000')
                    }

                    releaseSecond.complete(Unit)
                    secondCollector.join()

                    assertTrue(second.userName.all { it == '\u0000' })
                } finally {
                    firstCollector.cancel()
                    secondCollector.cancel()
                    firstCredential?.eraseSensitiveText()
                    secondCredential?.eraseSensitiveText()
                    query.erase()
                }
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `should delete credential successfully`() =
            runTest {
                coEvery { passkeyCredentialDao.getCredentialById(testCredential.id) } returns testEntity
                val result = repository.deleteCredential(testCredential.id)
                assertTrue(result.isSuccess, "Result was $result")
                coVerify { passkeyCredentialDao.deleteCredential(testCredential.id) }
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `should delete all credentials`() =
            runTest {
                coEvery { passkeyCredentialDao.getAllCredentials() } returns flowOf(listOf(testEntity))
                coEvery { passkeyCredentialDao.getCredentialById(testCredential.id) } returns testEntity
                val result = repository.deleteAllCredentials()

                assertTrue(result.isSuccess, "Result was $result")
                coVerify { passkeyCredentialDao.deleteCredential(testCredential.id) }
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `should delete all credentials by rpId`() =
            runTest {
                coEvery {
                    passkeyCredentialDao.getCredentialsByRpText(match { it.contentEquals(testCredential.rpId) })
                } returns
                    flowOf(listOf(testEntity))
                coEvery { passkeyCredentialDao.getCredentialById(testCredential.id) } returns testEntity
                val result = repository.deleteAllCredentials(RpId(testCredential.rpId.toBoundaryString()))

                assertTrue(result.isSuccess, "Result was $result")
                coVerify { passkeyCredentialDao.deleteCredential(testCredential.id) }
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `should reset authenticator`() =
            runTest {
                coEvery { passkeyCredentialDao.getAllCredentials() } returns flowOf(listOf(testEntity))
                coEvery { passkeyCredentialDao.getCredentialById(testCredential.id) } returns testEntity
                coEvery { relyingPartyDao.clearAuthenticatorMetadata() } just Runs
                val result = repository.resetAuthenticator()

                assertTrue(result.isSuccess, "Result was $result")
                coVerify { passkeyCredentialDao.deleteCredential(testCredential.id) }
                coVerify(exactly = 1) { relyingPartyDao.clearAuthenticatorMetadata() }
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `FR-005 returns only credentials whose policy requires user verification`() =
            runTest {
                val uvRequired = testEntity.copyCredentialRow(credProtectPolicy = CRED_PROTECT_UV_REQUIRED)
                coEvery { passkeyCredentialDao.getAllCredentials() } returns flowOf(listOf(testEntity, uvRequired))

                val credentials = repository.getCredentialsRequiringUserVerification().toList()

                assertEquals(1, credentials.size)
                assertEquals(uvRequired.id, credentials.single().id.encoded)
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `FR-005 statistics count credentials whose policy requires user verification`() =
            runTest {
                val uvRequired = testEntity.copyCredentialRow(credProtectPolicy = CRED_PROTECT_UV_REQUIRED)
                every { timeProvider.now() } returns testCredential.createdAt
                coEvery { passkeyCredentialDao.getAllCredentials() } returns flowOf(listOf(testEntity, uvRequired))

                val statistics = repository.getCredentialStatistics()

                assertEquals(1, statistics.credentialsRequiringUserVerification)
            }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @ParameterizedTest
    @ValueSource(strings = ["success", "second-row-failure", "cancel-after-owner"])
    fun `mutable candidate batches transfer or erase every staged RP owner`(scenario: String) =
        runTest {
            val source = SensitiveCredentialText.copyOf("https://example.com")
            val retained = mutableListOf<CredentialSummary>()
            val rows =
                if (scenario == "second-row-failure") {
                    listOf(
                        testEntity,
                        testEntity.copyCredentialRow().copy(public_key = "not-base64!"),
                    )
                } else {
                    listOf(testEntity)
                }
            repository.onSummaryCreatedForTest = { summary ->
                retained += summary
                if (scenario == "cancel-after-owner") throw CancellationException("synthetic cancellation")
            }
            coEvery { passkeyCredentialDao.getCredentialsByRpText(match { it.contentEquals(source) }) } returns
                flowOf(rows)
            try {
                if (scenario == "cancel-after-owner") {
                    assertFailsWith<CancellationException> { repository.getCredentialSummariesForRpText(source) }
                } else {
                    val result = repository.getCredentialSummariesForRpText(source)
                    if (scenario == "success") {
                        val summaries = assertIs<Outcome.Success<List<CredentialSummary>>>(result).data
                        assertEquals(1, summaries.size)
                        assertTrue(summaries.single().rpIdText.contentEquals(source))
                        summaries.forEach { it.eraseSensitiveText() }
                    } else {
                        assertIs<Outcome.Error<*>>(result)
                    }
                }
                assertEquals(1, retained.size)
                retained.forEach { assertTrue(it.rpIdText.all { character -> character == '\u0000' }) }
                assertTrue(source.contentEquals("https://example.com"))
                rows.forEach { row ->
                    listOf(row.rp_id, row.rp_name, row.user_id, row.user_name, row.user_display_name).forEach {
                        assertTrue(it.all { byte -> byte == 0.toByte() })
                    }
                }
            } finally {
                retained.forEach { it.eraseSensitiveText() }
                source.erase()
            }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `summary identity gate rejects byte mismatches and erases partial owners and rows`() =
        runTest {
            val credentialBytes =
                java.util.Base64
                    .getDecoder()
                    .decode(testEntity.credential_id)
            val mismatchBytes =
                listOf(
                    credentialBytes.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() },
                    credentialBytes.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() },
                    credentialBytes + byteArrayOf(0x7F),
                )
            val mismatches = mismatchBytes.map { CredentialId.fromByteArray(it).encoded } + "not-valid!"
            val source = SensitiveCredentialText.copyOf("https://example.com")
            val retained = mutableListOf<CredentialSummary>()
            repository.onSummaryCreatedForTest = { retained += it }

            mismatches.forEach { mismatchId ->
                val validRow = testEntity.copyCredentialRow()
                val malformedRow =
                    testEntity
                        .copyCredentialRow()
                        .copy(id = mismatchId)
                val rows = listOf(validRow, malformedRow)
                coEvery { passkeyCredentialDao.getCredentialsByRpText(match { it.contentEquals(source) }) } returns
                    flowOf(rows)

                val result = repository.getCredentialSummariesForRpText(source)

                assertIs<Outcome.Error<*>>(result)
                assertEquals(1, retained.size, "The preceding valid summary is staged before bad identity rejection")
                assertTrue(retained.single().rpIdText.all { it == '\u0000' }, "Partial summary text is erased")
                rows.forEach { row ->
                    listOf(row.rp_id, row.rp_name, row.user_id, row.user_name, row.user_display_name).forEach { bytes ->
                        assertTrue(bytes.all { it == 0.toByte() }, "Source row text is erased")
                    }
                }
                retained.clear()
            }
            verify(exactly = 0) { publicKeyDecoder.decodePublicKey(any(), any()) }
            source.erase()
            credentialBytes.fill(0)
            mismatchBytes.forEach { it.fill(0) }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `summary observer SQLException propagates and erases staged summary and source row`() =
        runTest {
            val source = SensitiveCredentialText.copyOf("https://example.com")
            val retained = mutableListOf<CredentialSummary>()
            val row = testEntity.copyCredentialRow()
            val callbackFailure = android.database.SQLException("synthetic summary observer failure")
            repository.onSummaryCreatedForTest = { summary ->
                retained += summary
                throw callbackFailure
            }
            coEvery { passkeyCredentialDao.getCredentialsByRpText(match { it.contentEquals(source) }) } returns
                flowOf(listOf(row))

            try {
                val thrown =
                    assertFailsWith<android.database.SQLException> {
                        repository.getCredentialSummariesForRpText(source)
                    }

                assertSame(callbackFailure, thrown)
                assertEquals(1, retained.size)
                assertTrue(retained.single().rpIdText.all { it == '\u0000' })
                listOf(row.rp_id, row.rp_name, row.user_id, row.user_name, row.user_display_name).forEach { bytes ->
                    assertTrue(bytes.all { it == 0.toByte() })
                }
                assertTrue(source.contentEquals("https://example.com"))
            } finally {
                retained.forEach { it.eraseSensitiveText() }
                source.erase()
            }
        }

    private fun Passkey_credential.copyCredentialRow(
        aaguid: String = this.aaguid,
        credProtectPolicy: Long = this.cred_protect_policy,
        id: CredentialId? = null,
    ): Passkey_credential {
        val rowId = id ?: testCredential.id
        return copy(
            id = rowId.encoded,
            credential_id =
                java.util.Base64
                    .getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(rowId.toByteArray()),
            aaguid = aaguid,
            cred_protect_policy = credProtectPolicy,
            rp_id = rp_id.copyOf(),
            rp_name = rp_name.copyOf(),
            user_display_name = user_display_name.copyOf(),
            user_id = user_id.copyOf(),
            user_name = user_name.copyOf(),
            rp_id_index = rp_id_index?.copyOf(),
            user_id_index = user_id_index?.copyOf(),
            encrypted_metadata = encrypted_metadata?.copyOf(),
        )
    }
}
