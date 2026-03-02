package com.chimali.fido2.data.repository

import com.chimali.fido2.data.dao.PasskeyCredentialDao
import com.chimali.fido2.data.dao.RelyingPartyDao
import com.chimali.fido2.data.dao.UserConsentRecordDao
import com.chimali.fido2.data.service.CredentialStorageService
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
import java.time.Instant

@DisplayName("CredentialRepositoryImpl Tests")
class CredentialRepositoryImplTest {
    
    private lateinit var passkeyCredentialDao: PasskeyCredentialDao
    private lateinit var relyingPartyDao: RelyingPartyDao
    private lateinit var userConsentRecordDao: UserConsentRecordDao
    private lateinit var credentialStorageService: CredentialStorageService
    private lateinit var repository: CredentialRepositoryImpl
    
    private lateinit var testCredential: PasskeyCredential
    private lateinit var testRp: RelyingParty
    private lateinit var testConsent: UserConsentRecord
    private lateinit var testPublicKey: java.security.PublicKey
    
    @BeforeEach
    fun setUp() = runTest {
        passkeyCredentialDao = mockk()
        relyingPartyDao = mockk()
        userConsentRecordDao = mockk()
        credentialStorageService = mockk()
        repository = CredentialRepositoryImpl(
            passkeyCredentialDao,
            relyingPartyDao,
            userConsentRecordDao,
            credentialStorageService
        )
        
        // Setup test data
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        keyPairGenerator.initialize(256)
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
        
        testRp = RelyingParty.create(
            id = "https://example.com",
            name = "Example Website",
            icon = "https://example.com/icon.png"
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
        
        // Setup default mock responses
        coEvery { credentialStorageService.storePrivateKey(any(), any()) } returns Result.success(Unit)
        coEvery { credentialStorageService.getPublicKey(any()) } returns testPublicKey
        coEvery { credentialStorageService.deletePrivateKey(any()) } returns Result.success(Unit)
        coEvery { passkeyCredentialDao.insertCredential(any()) } just Runs
        coEvery { passkeyCredentialDao.getCredentialById(any()) } returns null
        coEvery { passkeyCredentialDao.getCredentialsByRpId(any()) } returns flowOf()
        coEvery { passkeyCredentialDao.getCredentialsByUserId(any()) } returns flowOf()
        coEvery { passkeyCredentialDao.getAllCredentials() } returns flowOf()
        coEvery { passkeyCredentialDao.updateCredential(any()) } just Runs
        coEvery { passkeyCredentialDao.deleteCredential(any()) } just Runs
        coEvery { relyingPartyDao.getRelyingPartyById(any()) } returns null
        coEvery { relyingPartyDao.insertRelyingParty(any()) } just Runs
        coEvery { relyingPartyDao.updateRelyingParty(any()) } just Runs
        coEvery { relyingPartyDao.getAllRelyingParties() } returns flowOf()
        coEvery { userConsentRecordDao.insertConsent(any()) } returns Result.success(Unit)
        coEvery { userConsentRecordDao.getRecentConsent(any(), any()) } returns flowOf()
    }
    
    @Nested
    @DisplayName("Credential Management Tests")
    inner class CredentialManagementTests {
        
        @Test
        @DisplayName("Should successfully save credential")
        fun `should successfully save credential`() = runTest {
            val result = repository.saveCredential(testCredential)
            
            assertTrue(result.isSuccess)
            coVerify { credentialStorageService.storePrivateKey(testCredential.privateKeyAlias, testCredential.publicKey) }
            coVerify { passkeyCredentialDao.insertCredential(testCredential) }
        }
        
        @Test
        @DisplayName("Should fail to save credential when key storage fails")
        fun `should fail to save credential when key storage fails`() = runTest {
            coEvery { credentialStorageService.storePrivateKey(any(), any()) } returns Result.failure(
                Fido2Exception.CredentialStorageFailed()
            )
            
            val result = repository.saveCredential(testCredential)
            
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is Fido2Exception.CredentialStorageFailed)
            coVerify(exactly = 0) { passkeyCredentialDao.insertCredential(any()) }
        }
        
        @Test
        @DisplayName("Should retrieve credential by ID")
        fun `should retrieve credential by id`() = runTest {
            val credentialEntity = mockk<com.chimali.fido2.data.database.PasskeyCredentialEntity> {
                every { toDomainModel(any()) } returns testCredential
            }
            coEvery { passkeyCredentialDao.getCredentialById(testCredential.id) } returns credentialEntity
            
            val result = repository.getCredentialById(testCredential.id)
            
            assertNotNull(result)
            assertEquals(testCredential.id, result?.id)
            coVerify { credentialStorageService.getPublicKey(testCredential.privateKeyAlias) }
        }
        
        @Test
        @DisplayName("Should return null when credential not found")
        fun `should return null when credential not found`() = runTest {
            coEvery { passkeyCredentialDao.getCredentialById("nonexistent") } returns null
            
            val result = repository.getCredentialById("nonexistent")
            
            assertNull(result)
        }
        
        @Test
        @DisplayName("Should retrieve credentials by RP ID")
        fun `should retrieve credentials by rp id`() = runTest {
            val credentialEntity = mockk<com.chimali.fido2.data.database.PasskeyCredentialEntity> {
                every { toDomainModel(any()) } returns testCredential
            }
            coEvery { passkeyCredentialDao.getCredentialsByRpId(testCredential.rpId) } returns flowOf(credentialEntity)
            
            val result = repository.getCredentialsByRpId(testCredential.rpId).toList()
            
            assertEquals(1, result.size)
            assertEquals(testCredential.id, result.first().id)
        }
        
        @Test
        @DisplayName("Should retrieve credentials by user ID")
        fun `should retrieve credentials by user id`() = runTest {
            val credentialEntity = mockk<com.chimali.fido2.data.database.PasskeyCredentialEntity> {
                every { toDomainModel(any()) } returns testCredential
            }
            coEvery { passkeyCredentialDao.getCredentialsByUserId(testCredential.userId) } returns flowOf(credentialEntity)
            
            val result = repository.getCredentialsByUserId(testCredential.userId).toList()
            
            assertEquals(1, result.size)
            assertEquals(testCredential.id, result.first().id)
        }
        
        @Test
        @DisplayName("Should retrieve all credentials")
        fun `should retrieve all credentials`() = runTest {
            val credentialEntity = mockk<com.chimali.fido2.data.database.PasskeyCredentialEntity> {
                every { toDomainModel(any()) } returns testCredential
            }
            coEvery { passkeyCredentialDao.getAllCredentials() } returns flowOf(credentialEntity)
            
            val result = repository.getAllCredentials().toList()
            
            assertEquals(1, result.size)
            assertEquals(testCredential.id, result.first().id)
        }
        
        @Test
        @DisplayName("Should update credential successfully")
        fun `should update credential successfully`() = runTest {
            coEvery { passkeyCredentialDao.getCredentialById(testCredential.id) } returns mockk {
                every { toDomainModel(any()) } returns testCredential
            }
            
            val updatedCredential = testCredential.copy(signCount = 5L)
            val result = repository.updateCredential(testCredential.id) { updatedCredential }
            
            assertTrue(result.isSuccess)
            coVerify { passkeyCredentialDao.updateCredential(updatedCredential) }
        }
        
        @Test
        @DisplayName("Should fail to update nonexistent credential")
        fun `should fail to update nonexistent credential`() = runTest {
            coEvery { passkeyCredentialDao.getCredentialById("nonexistent") } returns null
            
            val result = repository.updateCredential("nonexistent") { testCredential }
            
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is Fido2Exception.CredentialNotFound)
        }
        
        @Test
        @DisplayName("Should delete credential successfully")
        fun `should delete credential successfully`() = runTest {
            coEvery { passkeyCredentialDao.getCredentialById(testCredential.id) } returns mockk {
                every { toDomainModel(any()) } returns testCredential
            }
            
            val result = repository.deleteCredential(testCredential.id)
            
            assertTrue(result.isSuccess)
            coVerify { credentialStorageService.deletePrivateKey(testCredential.privateKeyAlias) }
            coVerify { passkeyCredentialDao.deleteCredential(testCredential.id) }
        }
        
        @Test
        @DisplayName("Should fail to delete nonexistent credential")
        fun `should fail to delete nonexistent credential`() = runTest {
            coEvery { passkeyCredentialDao.getCredentialById("nonexistent") } returns null
            
            val result = repository.deleteCredential("nonexistent")
            
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is Fido2Exception.CredentialNotFound)
        }
        
        @Test
        @DisplayName("Should delete credentials by RP ID")
        fun `should delete credentials by rp id`() = runTest {
            coEvery { passkeyCredentialDao.getCredentialsByRpId(testCredential.rpId) } returns flowOf(
                mockk { every { toDomainModel(any()) } returns testCredential }
            )
            
            val result = repository.deleteCredentialsByRpId(testCredential.rpId)
            
            assertTrue(result.isSuccess)
            assertEquals(1, result.getOrThrow())
        }
    }
    
    @Nested
    @DisplayName("Relying Party Management Tests")
    inner class RelyingPartyManagementTests {
        
        @Test
        @DisplayName("Should retrieve relying party by ID")
        fun `should retrieve relying party by id`() = runTest {
            val rpEntity = mockk<com.chimali.fido2.data.database.RelyingPartyEntity> {
                every { toDomainModel() } returns testRp
            }
            coEvery { relyingPartyDao.getRelyingPartyById(testRp.id) } returns rpEntity
            
            val result = repository.getRelyingParty(testRp.id)
            
            assertNotNull(result)
            assertEquals(testRp.id, result?.id)
        }
        
        @Test
        @DisplayName("Should return null when RP not found")
        fun `should return null when rp not found`() = runTest {
            coEvery { relyingPartyDao.getRelyingPartyById("nonexistent") } returns null
            
            val result = repository.getRelyingParty("nonexistent")
            
            assertNull(result)
        }
        
        @Test
        @DisplayName("Should update existing RP")
        fun `should update existing rp`() = runTest {
            coEvery { relyingPartyDao.getRelyingPartyById(testRp.id) } returns mockk {
                every { toDomainModel() } returns testRp
            }
            
            val updatedRp = testRp.copy(credentialCount = 5)
            val result = repository.updateRelyingParty(testRp.id) { updatedRp }
            
            assertTrue(result.isSuccess)
            coVerify { relyingPartyDao.updateRelyingParty(updatedRp) }
        }
        
        @Test
        @DisplayName("Should create new RP when updating nonexistent RP")
        fun `should create new rp when updating nonexistent rp`() = runTest {
            coEvery { relyingPartyDao.getRelyingPartyById(testRp.id) } returns null
            
            val result = repository.updateRelyingParty(testRp.id) { testRp }
            
            assertTrue(result.isSuccess)
            coVerify { relyingPartyDao.insertRelyingParty(testRp) }
        }
        
        @Test
        @DisplayName("Should retrieve all RPs")
        fun `should retrieve all rps`() = runTest {
            val rpEntity = mockk<com.chimali.fido2.data.database.RelyingPartyEntity> {
                every { toDomainModel() } returns testRp
            }
            coEvery { relyingPartyDao.getAllRelyingParties() } returns flowOf(rpEntity)
            
            val result = repository.getAllRelyingParties().toList()
            
            assertEquals(1, result.size)
            assertEquals(testRp.id, result.first().id)
        }
        
        @Test
        @DisplayName("Should get RP statistics")
        fun `should get rp statistics`() = runTest {
            coEvery { relyingPartyDao.getRelyingPartyById(testRp.id) } returns mockk {
                every { toDomainModel() } returns testRp
            }
            coEvery { passkeyCredentialDao.getCredentialsByRpId(testRp.id) } returns flowOf(
                mockk { every { toDomainModel(any()) } returns testCredential }
            )
            
            val result = repository.getRelyingPartyStatistics(testRp.id)
            
            assertNotNull(result)
            assertEquals(testRp.id, result?.rpId)
            assertEquals(1, result?.credentialCount)
        }
        
        @Test
        @DisplayName("Should return null statistics for nonexistent RP")
        fun `should return null statistics for nonexistent rp`() = runTest {
            coEvery { relyingPartyDao.getRelyingPartyById("nonexistent") } returns null
            
            val result = repository.getRelyingPartyStatistics("nonexistent")
            
            assertNull(result)
        }
    }
    
    @Nested
    @DisplayName("User Consent Management Tests")
    inner class UserConsentManagementTests {
        
        @Test
        @DisplayName("Should save user consent")
        fun `should save user consent`() = runTest {
            val result = repository.saveUserConsent(testConsent)
            
            assertTrue(result.isSuccess)
            coVerify { userConsentRecordDao.insertConsent(testConsent) }
        }
        
        @Test
        @DisplayName("Should fail to save consent when DAO fails")
        fun `should fail to save consent when dao fails`() = runTest {
            coEvery { userConsentRecordDao.insertConsent(any()) } returns Result.failure(
                Fido2Exception.ConsentStorageFailed()
            )
            
            val result = repository.saveUserConsent(testConsent)
            
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is Fido2Exception.ConsentStorageFailed)
        }
        
        @Test
        @DisplayName("Should retrieve recent consent records")
        fun `should retrieve recent consent records`() = runTest {
            val consentEntity = mockk<com.chimali.fido2.data.database.UserConsentRecordEntity> {
                every { toDomainModel() } returns testConsent
            }
            coEvery { userConsentRecordDao.getRecentConsent(any(), any()) } returns flowOf(consentEntity)
            
            val result = repository.getRecentUserConsent(testConsent.rpId, 50).toList()
            
            assertEquals(1, result.size)
            assertEquals(testConsent.id, result.first().id)
        }
        
        @Test
        @DisplayName("Should retrieve consent by credential ID")
        fun `should retrieve consent by credential id`() = runTest {
            val consentEntity = mockk<com.chimali.fido2.data.database.UserConsentRecordEntity> {
                every { toDomainModel() } returns testConsent
            }
            coEvery { userConsentRecordDao.getConsentByCredential(testConsent.credentialId!!, any()) } returns flowOf(consentEntity)
            
            val result = repository.getUserConsentByCredential(testConsent.credentialId!!, 50).toList()
            
            assertEquals(1, result.size)
            assertEquals(testConsent.id, result.first().id)
        }
        
        @Test
        @DisplayName("Should retrieve consent by operation type")
        fun `should retrieve consent by operation type`() = runTest {
            val consentEntity = mockk<com.chimali.fido2.data.database.UserConsentRecordEntity> {
                every { toDomainModel() } returns testConsent
            }
            coEvery { userConsentRecordDao.getConsentByOperation(testConsent.operationType, any(), any()) } returns flowOf(consentEntity)
            
            val result = repository.getUserConsentByOperation(testConsent.operationType, testConsent.rpId, 50).toList()
            
            assertEquals(1, result.size)
            assertEquals(testConsent.id, result.first().id)
        }
        
        @Test
        @DisplayName("Should delete consent by ID")
        fun `should delete consent by id`() = runTest {
            val result = repository.deleteUserConsent(testConsent.id)
            
            assertTrue(result.isSuccess)
            coVerify { userConsentRecordDao.deleteConsent(testConsent.id) }
        }
        
        @Test
        @DisplayName("Should delete old consent records")
        fun `should delete old consent records`() = runTest {
            val cutoffDate = Instant.now().minusSeconds(86400) // 1 day ago
            coEvery { userConsentRecordDao.deleteConsentBefore(cutoffDate) } returns 5
            
            val result = repository.deleteOldConsent(cutoffDate)
            
            assertTrue(result.isSuccess)
            assertEquals(5, result.getOrThrow())
        }
    }
    
    @Nested
    @DisplayName("Validation Tests")
    inner class ValidationTests {
        
        @Test
        @DisplayName("Should validate credential creation successfully")
        fun `should validate credential creation successfully`() = runTest {
            coEvery { passkeyCredentialDao.getCredentialsByRpId(testCredential.rpId) } returns flowOf()
            coEvery { relyingPartyDao.getRelyingPartyById(testCredential.rpId) } returns null
            
            val result = repository.validateCredentialCreation(testCredential.rpId, testCredential.userId)
            
            assertTrue(result.isSuccess)
        }
        
        @Test
        @DisplayName("Should fail validation when too many credentials exist")
        fun `should fail validation when too many credentials exist`() = runTest {
            val existingCredentials = (1..10).map { i ->
                mockk<com.chimali.fido2.data.database.PasskeyCredentialEntity> {
                    every { toDomainModel(any()) } returns testCredential.copy(
                        id = "cred_$i",
                        userId = testCredential.userId.uppercase() // Same user, different case
                    )
                }
            }
            coEvery { passkeyCredentialDao.getCredentialsByRpId(testCredential.rpId) } returns flowOf(*existingCredentials.toTypedArray())
            
            val result = repository.validateCredentialCreation(testCredential.rpId, testCredential.userId)
            
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is Fido2Exception.TooManyCredentials)
        }
        
        @Test
        @DisplayName("Should fail validation when RP is blocked")
        fun `should fail validation when rp is blocked`() = runTest {
            val blockedRp = testRp.copy(isBlocked = true)
            coEvery { passkeyCredentialDao.getCredentialsByRpId(testCredential.rpId) } returns flowOf()
            coEvery { relyingPartyDao.getRelyingPartyById(testCredential.rpId) } returns mockk {
                every { toDomainModel() } returns blockedRp
            }
            
            val result = repository.validateCredentialCreation(testCredential.rpId, testCredential.userId)
            
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is Fido2Exception.RelyingPartyBlocked)
        }
    }
    
    @Nested
    @DisplayName("Search and Filter Tests")
    inner class SearchAndFilterTests {
        
        @Test
        @DisplayName("Should search credentials")
        fun `should search credentials`() = runTest {
            val credentialEntity = mockk<com.chimali.fido2.data.database.PasskeyCredentialEntity> {
                every { toDomainModel(any()) } returns testCredential
            }
            coEvery { passkeyCredentialDao.searchCredentials(any(), any()) } returns flowOf(credentialEntity)
            
            val result = repository.searchCredentials("test", testCredential.rpId).toList()
            
            assertEquals(1, result.size)
            assertEquals(testCredential.id, result.first().id)
        }
        
        @Test
        @DisplayName("Should get expired credentials")
        fun `should get expired credentials`() = runTest {
            val credentialEntity = mockk<com.chimali.fido2.data.database.PasskeyCredentialEntity> {
                every { toDomainModel(any()) } returns testCredential
            }
            coEvery { passkeyCredentialDao.getExpiredCredentials(any()) } returns flowOf(credentialEntity)
            
            val result = repository.getExpiredCredentials(730).toList()
            
            assertEquals(1, result.size)
            assertEquals(testCredential.id, result.first().id)
        }
        
        @Test
        @DisplayName("Should cleanup expired credentials")
        fun `should cleanup expired credentials`() = runTest {
            coEvery { passkeyCredentialDao.getExpiredCredentials(any()) } returns flowOf(
                mockk { every { toDomainModel(any()) } returns testCredential }
            )
            coEvery { credentialStorageService.deletePrivateKey(any()) } returns Result.success(Unit)
            coEvery { passkeyCredentialDao.deleteCredential(any()) } just Runs
            
            val result = repository.cleanupExpiredCredentials(730)
            
            assertTrue(result.isSuccess)
            assertEquals(1, result.getOrThrow())
        }
    }
    
    @Nested
    @DisplayName("Statistics Tests")
    inner class StatisticsTests {
        
        @Test
        @DisplayName("Should get repository statistics")
        fun `should get repository statistics`() = runTest {
            val credentialEntity = mockk<com.chimali.fido2.data.database.PasskeyCredentialEntity> {
                every { toDomainModel(any()) } returns testCredential
            }
            val rpEntity = mockk<com.chimali.fido2.data.database.RelyingPartyEntity> {
                every { toDomainModel() } returns testRp
            }
            val consentEntity = mockk<com.chimali.fido2.data.database.UserConsentRecordEntity> {
                every { toDomainModel() } returns testConsent
            }
            
            coEvery { passkeyCredentialDao.getAllCredentials() } returns flowOf(credentialEntity)
            coEvery { relyingPartyDao.getAllRelyingParties() } returns flowOf(rpEntity)
            coEvery { userConsentRecordDao.getRecentConsent(any(), any()) } returns flowOf(consentEntity)
            
            val result = repository.getRepositoryStatistics()
            
            assertEquals(1, result.totalCredentials)
            assertEquals(1, result.totalRelyingParties)
            assertEquals(1, result.totalConsentRecords)
            assertTrue(result.credentialsByRp.containsKey(testCredential.rpId))
            assertTrue(result.credentialsByUser.containsKey(testCredential.userId))
        }
    }
    
    @Nested
    @DisplayName("Error Handling Tests")
    inner class ErrorHandlingTests {
        
        @Test
        @DisplayName("Should handle database errors gracefully")
        fun `should handle database errors gracefully`() = runTest {
            coEvery { passkeyCredentialDao.insertCredential(any()) } throws RuntimeException("Database error")
            
            val result = repository.saveCredential(testCredential)
            
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is Fido2Exception.CredentialStorageFailed)
        }
        
        @Test
        @DisplayName("Should handle key storage errors gracefully")
        fun `should handle key storage errors gracefully`() = runTest {
            coEvery { credentialStorageService.storePrivateKey(any(), any()) } throws RuntimeException("KeyStore error")
            
            val result = repository.saveCredential(testCredential)
            
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is Fido2Exception.CredentialStorageFailed)
        }
        
        @Test
        @DisplayName("Should handle missing public key gracefully")
        fun `should handle missing public key gracefully`() = runTest {
            val credentialEntity = mockk<com.chimali.fido2.data.database.PasskeyCredentialEntity>()
            coEvery { passkeyCredentialDao.getCredentialById(testCredential.id) } returns credentialEntity
            coEvery { credentialStorageService.getPublicKey(any()) } returns null
            
            val result = repository.getCredentialById(testCredential.id)
            
            assertNull(result)
        }
    }
}
