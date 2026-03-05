package com.chimali.fido2.data.repository

import com.chimali.fido2.data.dao.PasskeyCredentialDao
import com.chimali.fido2.data.dao.RelyingPartyDao
import com.chimali.fido2.data.dao.UserConsentRecordDao
import com.chimali.fido2.data.crypto.Fido2CryptoService
import com.chimali.fido2.domain.model.*
import com.chimali.fido2.domain.exception.Fido2Exception
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import java.security.KeyPairGenerator

@DisplayName("CredentialRepositoryImpl Tests")
class CredentialRepositoryImplTest {

    private lateinit var passkeyCredentialDao: PasskeyCredentialDao
    private lateinit var relyingPartyDao: RelyingPartyDao
    private lateinit var userConsentRecordDao: UserConsentRecordDao
    private lateinit var cryptoService: Fido2CryptoService
    private lateinit var repository: CredentialRepositoryImpl

    private lateinit var testCredential: PasskeyCredential
    private lateinit var testRp: RelyingParty
    private lateinit var testConsent: UserConsentRecord
    private lateinit var testPublicKey: java.security.PublicKey
    private lateinit var testEntity: com.chimali.fido2.data.database.PasskeyCredential

    @BeforeEach
    fun setUp() = runTest {
        passkeyCredentialDao = mockk()
        relyingPartyDao = mockk()
        userConsentRecordDao = mockk()
        cryptoService = mockk()
        repository = CredentialRepositoryImpl(
            passkeyCredentialDao,
            relyingPartyDao,
            userConsentRecordDao,
            cryptoService
        )

        val keyPairGenerator = KeyPairGenerator.getInstance("EC").apply { initialize(256) }
        testPublicKey = keyPairGenerator.generateKeyPair().public

        testCredential = PasskeyCredential.create(
            id = "test_credential_id",
            rpId = "https://example.com",
            userId = "user123",
            userName = "testuser",
            userDisplayName = "Test User",
            publicKey = testPublicKey,
            privateKeyAlias = "test_private_key_alias",
            aaguid = ByteArray(16),
            credentialId = "cred_id".toByteArray()
        )

        testEntity = com.chimali.fido2.data.database.PasskeyCredential(
            id = "test_credential_id",
            rpId = "https://example.com",
            rpName = "example",
            userId = "user123",
            userName = "testuser",
            userDisplayName = "Test User",
            privateKeyAlias = "test_private_key_alias",
            signCount = 0L,
            createdAt = testCredential.createdAt.toEpochMilli(),
            lastUsedAt = testCredential.lastUsedAt.toEpochMilli(),
            aaguid = java.util.Base64.getEncoder().encodeToString(testCredential.aaguid),
            credentialId = java.util.Base64.getEncoder().encodeToString(testCredential.credentialId),
            publicKey = java.util.Base64.getEncoder().encodeToString(testPublicKey.encoded)
        )

        testRp = RelyingParty.create(
            id = "https://example.com",
            name = "Example Website",
            
        )

        testConsent = UserConsentRecord.create(
            operationType = ConsentOperationType.REGISTRATION,
            rpId = "https://example.com",
            credentialId = "test_credential_id",
            biometricUsed = true,
            pinUsed = false,
            ipAddress = "192.168.1.1",
            userAgent = "Test User Agent",
            deviceId = "test_device_id"
        )

        // Default Mocks
        coEvery { cryptoService.keyExists(any()) } returns true
        coEvery { cryptoService.getPublicKey(any()) } returns testPublicKey
        coEvery { cryptoService.deleteCredentialKey(any()) } returns Result.success(Unit)
        
        coEvery { passkeyCredentialDao.insertCredential(any()) } just Runs
        coEvery { passkeyCredentialDao.getCredentialById(any()) } returns null
        coEvery { passkeyCredentialDao.getCredentialsByRpId(any()) } returns flowOf(listOf())
        coEvery { passkeyCredentialDao.getCredentialsByUserId(any()) } returns flowOf(listOf())
        coEvery { passkeyCredentialDao.getAllCredentials() } returns flowOf(listOf())
        coEvery { passkeyCredentialDao.updateCredential(any()) } just Runs
        coEvery { passkeyCredentialDao.deleteCredential(any()) } just Runs

        coEvery { relyingPartyDao.getRelyingPartyById(any()) } returns null
        coEvery { relyingPartyDao.insertRelyingParty(any()) } just Runs
        coEvery { relyingPartyDao.updateRelyingParty(any()) } just Runs
        coEvery { relyingPartyDao.getAllRelyingParties() } returns flowOf()

        coEvery { userConsentRecordDao.insertConsent(any()) } returns Unit
        coEvery { userConsentRecordDao.getRecentConsent(any(), any()) } returns flowOf()
    }

    @Nested
    @DisplayName("Credential Management Tests")
    inner class CredentialManagementTests {

        @Test
        fun `should successfully save credential`() = runTest {
            val result = repository.saveCredential(testCredential)
            assertTrue(result.isSuccess)
            coVerify { cryptoService.keyExists(testCredential.id) }
            coVerify { passkeyCredentialDao.insertCredential(testCredential) }
        }

        @Test
        fun `should retrieve credential by id`() = runTest {
            coEvery { passkeyCredentialDao.getCredentialById(testCredential.id) } returns testEntity
            val result = repository.getCredentialById(testCredential.id)
            assertNotNull(result)
            assertEquals(testCredential.id, result?.id)
        }

        @Test
        fun `should return null when credential not found`() = runTest {
            coEvery { passkeyCredentialDao.getCredentialById("nonexistent") } returns null
            val result = repository.getCredentialById("nonexistent")
            assertNull(result)
        }

        @Test
        fun `should retrieve credentials by rp id`() = runTest {
            coEvery { passkeyCredentialDao.getCredentialsByRpId(testCredential.rpId) } returns flowOf(listOf(testEntity))
            val result = repository.getCredentialsByRpId(testCredential.rpId).toList()
            assertEquals(1, result.size)
            assertEquals(testCredential.id, result.first().id)
        }

        @Test
        fun `should retrieve all credentials`() = runTest {
            coEvery { passkeyCredentialDao.getAllCredentials() } returns flowOf(listOf(testEntity))
            val result = repository.getAllCredentials().toList()
            assertEquals(1, result.size)
            assertEquals(testCredential.id, result.first().id)
        }

        @Test
        fun `should delete credential successfully`() = runTest {
            coEvery { passkeyCredentialDao.getCredentialById(testCredential.id) } returns testEntity
            val result = repository.deleteCredential(testCredential.id)
            assertTrue(result.isSuccess)
            coVerify { passkeyCredentialDao.deleteCredential(testCredential.id) }
        }

        @Test
        fun `should delete all credentials`() = runTest {
            coEvery { passkeyCredentialDao.getAllCredentials() } returns flowOf(listOf(testEntity))
            coEvery { passkeyCredentialDao.getCredentialById(testCredential.id) } returns testEntity
            val result = repository.deleteAllCredentials()
            
            assertTrue(result.isSuccess)
            coVerify { passkeyCredentialDao.deleteCredential(testCredential.id) }
            coVerify { cryptoService.deleteCredentialKey(testCredential.id) }
        }

        @Test
        fun `should delete all credentials by rpId`() = runTest {
            coEvery { passkeyCredentialDao.getCredentialsByRpId(testCredential.rpId) } returns flowOf(listOf(testEntity))
            coEvery { passkeyCredentialDao.getCredentialById(testCredential.id) } returns testEntity
            val result = repository.deleteAllCredentials(testCredential.rpId)
            
            assertTrue(result.isSuccess)
            coVerify { passkeyCredentialDao.deleteCredential(testCredential.id) }
        }

        @Test
        fun `should reset authenticator`() = runTest {
            coEvery { passkeyCredentialDao.getAllCredentials() } returns flowOf(listOf(testEntity))
            coEvery { passkeyCredentialDao.getCredentialById(testCredential.id) } returns testEntity
            val result = repository.resetAuthenticator()
            
            assertTrue(result.isSuccess)
            coVerify { passkeyCredentialDao.deleteCredential(testCredential.id) }
        }
    }
}
