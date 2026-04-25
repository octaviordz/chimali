package com.chimali.fido2.data.repository

import com.chimali.fido2.data.crypto.Fido2CryptoService
import com.chimali.fido2.data.dao.PasskeyCredentialDao
import com.chimali.fido2.data.dao.RelyingPartyDao
import com.chimali.fido2.data.dao.UserConsentRecordDao
import com.chimali.fido2.domain.model.ConsentOperationType
import com.chimali.fido2.domain.model.CredentialId
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.model.RelyingParty
import com.chimali.fido2.domain.model.UserConsentRecord
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import java.security.KeyPairGenerator
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested

class CredentialRepositoryImplTest {
    private lateinit var passkeyCredentialDao: PasskeyCredentialDao
    private lateinit var relyingPartyDao: RelyingPartyDao
    private lateinit var userConsentRecordDao: UserConsentRecordDao
    private lateinit var cryptoService: Fido2CryptoService
    private lateinit var publicKeyDecoder: com.chimali.fido2.data.crypto.PublicKeyDecoder
    private lateinit var corruptedKeyRepairWorker: com.chimali.fido2.data.worker.CorruptedKeyRepairWorker
    private lateinit var repository: CredentialRepositoryImpl

    private lateinit var testCredential: PasskeyCredential
    private lateinit var testRp: RelyingParty
    private lateinit var testConsent: UserConsentRecord
    private lateinit var testPublicKey: java.security.PublicKey
    private lateinit var testEntity: com.chimali.fido2.data.database.PasskeyCredential

    private companion object {
        private const val KEY_SIZE_256 = 256
        private const val AAGUID_SIZE_16 = 16
    }

    @BeforeTest
    fun setUp() =
        runTest {
            passkeyCredentialDao = mockk()
            relyingPartyDao = mockk()
            userConsentRecordDao = mockk()
            cryptoService = mockk()
            publicKeyDecoder = mockk()
            corruptedKeyRepairWorker = mockk()
            repository =
                CredentialRepositoryImpl(
                    passkeyCredentialDao,
                    relyingPartyDao,
                    userConsentRecordDao,
                    cryptoService,
                    publicKeyDecoder,
                    corruptedKeyRepairWorker,
                )

            val keyPairGenerator = KeyPairGenerator.getInstance("EC").apply { initialize(KEY_SIZE_256) }
            testPublicKey = keyPairGenerator.generateKeyPair().public

            testCredential =
                PasskeyCredential.create(
                    id = "test_credential_id",
                    rpId = "https://example.com",
                    userId = "user123",
                    userName = "testuser",
                    userDisplayName = "Test User",
                    publicKey = testPublicKey,
                    privateKeyAlias = "test_private_key_alias",
                    aaguid = ByteArray(AAGUID_SIZE_16),
                    credentialId = "cred_id".toByteArray(),
                )

            testEntity =
                com.chimali.fido2.data.database.PasskeyCredential(
                    id = "test_credential_id",
                    createdAt = testCredential.createdAt.toEpochMilli(),
                    lastUsedAt = testCredential.lastUsedAt.toEpochMilli(),
                    aaguid = java.util.Base64.getEncoder().encodeToString(testCredential.aaguid),
                    coseAlgorithm = PasskeyCredential.COSE_ES256.toLong(),
                    credentialId = java.util.Base64.getEncoder().encodeToString(testCredential.credentialId),
                    credProtectPolicy = testCredential.credProtectPolicy.toLong(),
                    label = null,
                    privateKeyAlias = "test_private_key_alias",
                    publicKey = java.util.Base64.getEncoder().encodeToString(testPublicKey.encoded),
                    rpId = "https://example.com",
                    rpName = "example",
                    signCount = 0L,
                    userDisplayName = "Test User",
                    userId = "user123",
                    userName = "testuser",
                )

            testRp =
                RelyingParty.create(
                    id = "https://example.com",
                    name = "Example Website",
                )

            testConsent =
                UserConsentRecord.create(
                    operationType = ConsentOperationType.REGISTRATION,
                    rpId = "https://example.com",
                    credentialId = "test_credential_id",
                    biometricUsed = true,
                    pinUsed = false,
                    ipAddress = "192.168.1.1",
                    userAgent = "Test User Agent",
                    deviceId = "test_device_id",
                )

            // Common stubs
            coEvery { publicKeyDecoder.decodePublicKey(any(), any()) } returns Result.success(testPublicKey)
            // Default Mocks
            coEvery { cryptoService.keyExists(any()) } returns true
            coEvery { cryptoService.getPublicKey(any(), any()) } returns testPublicKey
            coEvery { cryptoService.deleteCredentialKey(any()) } returns Result.success(Unit)

            coEvery { passkeyCredentialDao.insertCredential(any()) } just Runs
            coEvery { passkeyCredentialDao.getCredentialById(any()) } returns null
            coEvery { passkeyCredentialDao.getCredentialsByRpId(any()) } returns flowOf(listOf())
            coEvery { passkeyCredentialDao.getCredentialsByUserId(any()) } returns flowOf(listOf())
            coEvery { passkeyCredentialDao.getAllCredentials() } returns flowOf(listOf())
            coEvery { passkeyCredentialDao.updateCredential(any()) } just Runs
            coEvery { passkeyCredentialDao.deleteCredential(any()) } just Runs

            coEvery { relyingPartyDao.getRelyingPartyById(any()) } returns null
            coEvery { relyingPartyDao.insertOrUpdateRelyingParty(any()) } just Runs
            coEvery { relyingPartyDao.updateRelyingParty(any()) } just Runs
            coEvery { relyingPartyDao.getAllRelyingParties() } returns flowOf()

            coEvery { userConsentRecordDao.insertConsent(any()) } returns Unit
            coEvery { userConsentRecordDao.getRecentConsent(any(), any()) } returns flowOf()
        }

    @Nested
    inner class CredentialManagementTests {
        @Test
        fun `should successfully save credential`() =
            runTest {
                val result = repository.saveCredential(testCredential)
                assertTrue(result.isSuccess)
                coVerify { cryptoService.keyExists(CredentialId.fromString(testCredential.id)) }
                coVerify { passkeyCredentialDao.insertCredential(testCredential) }
            }

        @Test
        fun `should retrieve credential by id`() =
            runTest {
                coEvery { passkeyCredentialDao.getCredentialById(testCredential.id) } returns testEntity
                val result = repository.getCredentialById(testCredential.id)
                assertNotNull(result)
                assertEquals(testCredential.id, result?.id)
            }

        @Test
        fun `should return null when credential not found`() =
            runTest {
                coEvery { passkeyCredentialDao.getCredentialById("nonexistent") } returns null
                val result = repository.getCredentialById("nonexistent")
                assertNull(result)
            }

        @Test
        fun `should retrieve credentials by rp id`() =
            runTest {
                coEvery { passkeyCredentialDao.getCredentialsByRpId(testCredential.rpId) } returns
                    flowOf(listOf(testEntity))

                val flow = repository.getCredentialsByRpId(testCredential.rpId)
                val result = mutableListOf<PasskeyCredential>()
                flow.collect { result.add(it) }

                assertEquals(1, result.size)
                assertEquals(testCredential.id, result.first().id)
            }

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

        @Test
        fun `should delete credential successfully`() =
            runTest {
                coEvery { passkeyCredentialDao.getCredentialById(testCredential.id) } returns testEntity
                val result = repository.deleteCredential(testCredential.id)
                assertTrue(result.isSuccess)
                coVerify { passkeyCredentialDao.deleteCredential(testCredential.id) }
            }

        @Test
        fun `should delete all credentials`() =
            runTest {
                coEvery { passkeyCredentialDao.getAllCredentials() } returns flowOf(listOf(testEntity))
                coEvery { passkeyCredentialDao.getCredentialById(testCredential.id) } returns testEntity
                val result = repository.deleteAllCredentials()

                assertTrue(result.isSuccess)
                coVerify { passkeyCredentialDao.deleteCredential(testCredential.id) }
                coVerify { cryptoService.deleteCredentialKey(CredentialId.fromString(testCredential.id)) }
            }

        @Test
        fun `should delete all credentials by rpId`() =
            runTest {
                coEvery { passkeyCredentialDao.getCredentialsByRpId(testCredential.rpId) } returns
                    flowOf(listOf(testEntity))
                coEvery { passkeyCredentialDao.getCredentialById(testCredential.id) } returns testEntity
                val result = repository.deleteAllCredentials(testCredential.rpId)

                assertTrue(result.isSuccess)
                coVerify { passkeyCredentialDao.deleteCredential(testCredential.id) }
            }

        @Test
        fun `should reset authenticator`() =
            runTest {
                coEvery { passkeyCredentialDao.getAllCredentials() } returns flowOf(listOf(testEntity))
                coEvery { passkeyCredentialDao.getCredentialById(testCredential.id) } returns testEntity
                val result = repository.resetAuthenticator()

                assertTrue(result.isSuccess)
                coVerify { passkeyCredentialDao.deleteCredential(testCredential.id) }
            }
    }
}
