package com.chimali.fido2.data.dao

import com.chimali.fido2.data.database.Fido2Database
import com.chimali.fido2.data.database.PasskeyCredentialEntity
import com.chimali.fido2.domain.model.PasskeyCredential
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

@DisplayName("PasskeyCredentialDao Tests")
class PasskeyCredentialDaoTest {
    
    private lateinit var database: Fido2Database
    private lateinit var dao: PasskeyCredentialDao
    private lateinit var testCredential: PasskeyCredential
    private lateinit var testCredentialEntity: PasskeyCredentialEntity
    
    @BeforeEach
    fun setUp() = runTest {
        database = mockk()
        dao = PasskeyCredentialDao(database)
        
        // Setup test data
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        keyPairGenerator.initialize(256)
        val publicKey = keyPairGenerator.generateKeyPair().public
        
        testCredential = PasskeyCredential.create(
            id = "test_credential_id",
            rpId = "https://example.com",
            userId = "user123",
            userName = "testuser",
            userDisplayName = "Test User",
            publicKey = publicKey,
            privateKeyAlias = "test_private_key_alias",
            aaguid = ByteArray(16),
            credentialId = "cred_id".toByteArray()
        )
        
        testCredentialEntity = mockk {
            every { id } returns testCredential.id
            every { rpId } returns testCredential.rpId
            every { userId } returns testCredential.userId
            every { userName } returns testCredential.userName
            every { userDisplayName } returns testCredential.userDisplayName
            every { privateKeyAlias } returns testCredential.privateKeyAlias
            every { signCount } returns testCredential.signCount
            every { createdAt } returns testCredential.createdAt.toEpochMilli()
            every { lastUsedAt } returns testCredential.lastUsedAt.toEpochMilli()
            every { aaguid } returns testCredential.aaguid
            every { credentialId } returns testCredential.credentialId
            every { publicKeyEncoded } returns publicKey.encoded
        }
        
        // Setup default mock responses
        val insertQueries = mockk<com.chimali.fido2.data.database.PasskeyCredentialQueries.Insert>()
        val selectQueries = mockk<com.chimali.fido2.data.database.PasskeyCredentialQueries.SelectById>()
        val updateQueries = mockk<com.chimali.fido2.data.database.PasskeyCredentialQueries.Update>()
        val deleteQueries = mockk<com.chimali.fido2.data.database.PasskeyCredentialQueries.DeleteById>()
        
        every { database.passkeyCredentialQueries } returns mockk {
            every { insert(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns insertQueries
            every { selectById(any()) } returns selectQueries
            every { update(any(), any(), any(), any(), any(), any(), any()) } returns updateQueries
            every { deleteById(any()) } returns deleteQueries
        }
        
        every { insertQueries.executeAsOne() } just Runs
        every { selectQueries.executeAsOneOrNull() } returns testCredentialEntity
        every { updateQueries.executeAsOne() } just Runs
        every { deleteQueries.executeAsOne() } just Runs
    }
    
    @Nested
    @DisplayName("Insert Operations")
    inner class InsertOperations {
        
        @Test
        @DisplayName("Should insert credential successfully")
        fun `should insert credential successfully`() = runTest {
            dao.insertCredential(testCredential)
            
            verify { database.passkeyCredentialQueries.insert(
                id = testCredential.id,
                rpId = testCredential.rpId,
                userId = testCredential.userId,
                userName = testCredential.userName,
                userDisplayName = testCredential.userDisplayName,
                privateKeyAlias = testCredential.privateKeyAlias,
                signCount = testCredential.signCount,
                createdAt = testCredential.createdAt.toEpochMilli(),
                lastUsedAt = testCredential.lastUsedAt.toEpochMilli(),
                aaguid = testCredential.aaguid,
                credentialId = testCredential.credentialId,
                publicKeyEncoded = testCredential.publicKey.encoded
            ) }
        }
        
        @Test
        @DisplayName("Should handle insert errors gracefully")
        fun `should handle insert errors gracefully`() = runTest {
            val insertQueries = mockk<com.chimali.fido2.data.database.PasskeyCredentialQueries.Insert>()
            every { insertQueries.executeAsOne() } throws RuntimeException("Database error")
            every { database.passkeyCredentialQueries.insert(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns insertQueries
            
            assertThrows<RuntimeException> {
                dao.insertCredential(testCredential)
            }
        }
    }
    
    @Nested
    @DisplayName("Select Operations")
    inner class SelectOperations {
        
        @Test
        @DisplayName("Should select credential by ID")
        fun `should select credential by id`() = runTest {
            val result = dao.getCredentialById(testCredential.id)
            
            assertEquals(testCredentialEntity, result)
            verify { database.passkeyCredentialQueries.selectById(testCredential.id) }
        }
        
        @Test
        @DisplayName("Should return null when credential not found")
        fun `should return null when credential not found`() = runTest {
            val selectQueries = mockk<com.chimali.fido2.data.database.PasskeyCredentialQueries.SelectById>()
            every { selectQueries.executeAsOneOrNull() } returns null
            every { database.passkeyCredentialQueries.selectById(any()) } returns selectQueries
            
            val result = dao.getCredentialById("nonexistent")
            
            assertNull(result)
        }
        
        @Test
        @DisplayName("Should select credentials by RP ID")
        fun `should select credentials by rp id`() = runTest {
            val selectQueries = mockk<com.chimali.fido2.data.database.PasskeyCredentialQueries.SelectByRpId>()
            val query = mockk<com.squareup.sqldelight.Query>()
            every { selectQueries.asFlow() } returns flowOf(query)
            every { query.executeAsList() } returns listOf(testCredentialEntity)
            every { database.passkeyCredentialQueries.selectByRpId(any()) } returns selectQueries
            
            val result = dao.getCredentialsByRpId(testCredential.rpId).toList()
            
            assertEquals(1, result.size)
            assertEquals(testCredentialEntity, result.first())
        }
        
        @Test
        @DisplayName("Should select credentials by user ID")
        fun `should select credentials by user id`() = runTest {
            val selectQueries = mockk<com.chimali.fido2.data.database.PasskeyCredentialQueries.SelectByUserId>()
            val query = mockk<com.squareup.sqldelight.Query>()
            every { selectQueries.asFlow() } returns flowOf(query)
            every { query.executeAsList() } returns listOf(testCredentialEntity)
            every { database.passkeyCredentialQueries.selectByUserId(any()) } returns selectQueries
            
            val result = dao.getCredentialsByUserId(testCredential.userId).toList()
            
            assertEquals(1, result.size)
            assertEquals(testCredentialEntity, result.first())
        }
        
        @Test
        @DisplayName("Should select all credentials")
        fun `should select all credentials`() = runTest {
            val selectQueries = mockk<com.chimali.fido2.data.database.PasskeyCredentialQueries.SelectAll>()
            val query = mockk<com.squareup.sqldelight.Query>()
            every { selectQueries.asFlow() } returns flowOf(query)
            every { query.executeAsList() } returns listOf(testCredentialEntity)
            every { database.passkeyCredentialQueries.selectAll() } returns selectQueries
            
            val result = dao.getAllCredentials().toList()
            
            assertEquals(1, result.size)
            assertEquals(testCredentialEntity, result.first())
        }
        
        @Test
        @DisplayName("Should search credentials by query")
        fun `should search credentials by query`() = runTest {
            val searchQueries = mockk<com.chimali.fido2.data.database.PasskeyCredentialQueries.Search>()
            val query = mockk<com.squareup.sqldelight.Query>()
            every { searchQueries.asFlow() } returns flowOf(query)
            every { query.executeAsList() } returns listOf(testCredentialEntity)
            every { database.passkeyCredentialQueries.searchByRpId(any(), any()) } returns searchQueries
            
            val result = dao.searchCredentials("test", testCredential.rpId).toList()
            
            assertEquals(1, result.size)
            assertEquals(testCredentialEntity, result.first())
        }
        
        @Test
        @DisplayName("Should search all credentials by query")
        fun `should search all credentials by query`() = runTest {
            val searchQueries = mockk<com.chimali.fido2.data.database.PasskeyCredentialQueries.SearchAll>()
            val query = mockk<com.squareup.sqldelight.Query>()
            every { searchQueries.asFlow() } returns flowOf(query)
            every { query.executeAsList() } returns listOf(testCredentialEntity)
            every { database.passkeyCredentialQueries.searchAll(any()) } returns searchQueries
            
            val result = dao.searchCredentials("test", null).toList()
            
            assertEquals(1, result.size)
            assertEquals(testCredentialEntity, result.first())
        }
    }
    
    @Nested
    @DisplayName("Update Operations")
    inner class UpdateOperations {
        
        @Test
        @DisplayName("Should update credential successfully")
        fun `should update credential successfully`() = runTest {
            dao.updateCredential(testCredential)
            
            verify { database.passkeyCredentialQueries.update(
                id = testCredential.id,
                rpId = testCredential.rpId,
                userId = testCredential.userId,
                userName = testCredential.userName,
                userDisplayName = testCredential.userDisplayName,
                privateKeyAlias = testCredential.privateKeyAlias,
                signCount = testCredential.signCount,
                lastUsedAt = testCredential.lastUsedAt.toEpochMilli()
            ) }
        }
        
        @Test
        @DisplayName("Should update sign count")
        fun `should update sign count`() = runTest {
            val newSignCount = 5L
            dao.updateSignCount(testCredential.id, newSignCount)
            
            verify { database.passkeyCredentialQueries.updateSignCount(
                credentialId = testCredential.id,
                signCount = newSignCount,
                lastUsedAt = any()
            ) }
        }
        
        @Test
        @DisplayName("Should update last used timestamp")
        fun `should update last used timestamp`() = runTest {
            dao.updateLastUsedAt(testCredential.id)
            
            verify { database.passkeyCredentialQueries.updateLastUsedAt(
                credentialId = testCredential.id,
                lastUsedAt = any()
            ) }
        }
    }
    
    @Nested
    @DisplayName("Delete Operations")
    inner class DeleteOperations {
        
        @Test
        @DisplayName("Should delete credential by ID")
        fun `should delete credential by id`() = runTest {
            dao.deleteCredential(testCredential.id)
            
            verify { database.passkeyCredentialQueries.deleteById(testCredential.id) }
        }
        
        @Test
        @DisplayName("Should delete credentials by RP ID")
        fun `should delete credentials by rp id`() = runTest {
            val deleteQueries = mockk<com.chimali.fido2.data.database.PasskeyCredentialQueries.DeleteByRpId>()
            every { deleteQueries.executeAsOne() } returns 1
            every { database.passkeyCredentialQueries.deleteByRpId(any()) } returns deleteQueries
            every { database.passkeyCredentialQueries.changes() } returns mockk { every { executeAsOne() } returns 1 }
            
            val result = dao.deleteCredentialsByRpId(testCredential.rpId)
            
            assertEquals(1, result)
            verify { database.passkeyCredentialQueries.deleteByRpId(testCredential.rpId) }
        }
        
        @Test
        @DisplayName("Should delete credentials by user ID")
        fun `should delete credentials by user id`() = runTest {
            val deleteQueries = mockk<com.chimali.fido2.data.database.PasskeyCredentialQueries.DeleteByUserId>()
            every { deleteQueries.executeAsOne() } returns 1
            every { database.passkeyCredentialQueries.deleteByUserId(any()) } returns deleteQueries
            every { database.passkeyCredentialQueries.changes() } returns mockk { every { executeAsOne() } returns 1 }
            
            val result = dao.deleteCredentialsByUserId(testCredential.userId)
            
            assertEquals(1, result)
            verify { database.passkeyCredentialQueries.deleteByUserId(testCredential.userId) }
        }
    }
    
    @Nested
    @DisplayName("Count Operations")
    inner class CountOperations {
        
        @Test
        @DisplayName("Should count all credentials")
        fun `should count all credentials`() = runTest {
            val countQueries = mockk<com.chimali.fido2.data.database.PasskeyCredentialQueries.CountAll>()
            every { countQueries.executeAsOne() } returns 5L
            every { database.passkeyCredentialQueries.countAll() } returns countQueries
            
            val result = dao.countAllCredentials()
            
            assertEquals(5L, result)
        }
        
        @Test
        @DisplayName("Should count credentials by RP ID")
        fun `should count credentials by rp id`() = runTest {
            val countQueries = mockk<com.chimali.fido2.data.database.PasskeyCredentialQueries.CountByRpId>()
            every { countQueries.executeAsOne() } returns 3L
            every { database.passkeyCredentialQueries.countByRpId(any()) } returns countQueries
            
            val result = dao.countCredentialsByRpId(testCredential.rpId)
            
            assertEquals(3L, result)
        }
        
        @Test
        @DisplayName("Should count credentials by user ID")
        fun `should count credentials by user id`() = runTest {
            val countQueries = mockk<com.chimali.fido2.data.database.PasskeyCredentialQueries.CountByUserId>()
            every { countQueries.executeAsOne() } returns 2L
            every { database.passkeyCredentialQueries.countByUserId(any()) } returns countQueries
            
            val result = dao.countCredentialsByUserId(testCredential.userId)
            
            assertEquals(2L, result)
        }
    }
    
    @Nested
    @DisplayName("Existence Operations")
    inner class ExistenceOperations {
        
        @Test
        @DisplayName("Should check if credential exists")
        fun `should check if credential exists`() = runTest {
            val existsQueries = mockk<com.chimali.fido2.data.database.PasskeyCredentialQueries.ExistsById>()
            every { existsQueries.executeAsOne() } returns true
            every { database.passkeyCredentialQueries.existsById(any()) } returns existsQueries
            
            val result = dao.credentialExists(testCredential.id)
            
            assertTrue(result)
        }
        
        @Test
        @DisplayName("Should return false when credential doesn't exist")
        fun `should return false when credential doesn't exist`() = runTest {
            val existsQueries = mockk<com.chimali.fido2.data.database.PasskeyCredentialQueries.ExistsById>()
            every { existsQueries.executeAsOne() } returns false
            every { database.passkeyCredentialQueries.existsById(any()) } returns existsQueries
            
            val result = dao.credentialExists("nonexistent")
            
            assertFalse(result)
        }
    }
    
    @Nested
    @DisplayName("Specialized Queries")
    inner class SpecializedQueries {
        
        @Test
        @DisplayName("Should get expired credentials")
        fun `should get expired credentials`() = runTest {
            val cutoffDate = Instant.now().minusSeconds(86400) // 1 day ago
            val expiredQueries = mockk<com.chimali.fido2.data.database.PasskeyCredentialQueries.SelectExpired>()
            val query = mockk<com.squareup.sqldelight.Query>()
            every { expiredQueries.asFlow() } returns flowOf(query)
            every { query.executeAsList() } returns listOf(testCredentialEntity)
            every { database.passkeyCredentialQueries.selectExpired(any()) } returns expiredQueries
            
            val result = dao.getExpiredCredentials(cutoffDate).toList()
            
            assertEquals(1, result.size)
            assertEquals(testCredentialEntity, result.first())
        }
        
        @Test
        @DisplayName("Should get unused credentials")
        fun `should get unused credentials`() = runTest {
            val unusedQueries = mockk<com.chimali.fido2.data.database.PasskeyCredentialQueries.SelectUnused>()
            val query = mockk<com.squareup.sqldelight.Query>()
            every { unusedQueries.asFlow() } returns flowOf(query)
            every { query.executeAsList() } returns listOf(testCredentialEntity)
            every { database.passkeyCredentialQueries.selectUnused(any()) } returns unusedQueries
            
            val result = dao.getUnusedCredentials(30).toList()
            
            assertEquals(1, result.size)
            assertEquals(testCredentialEntity, result.first())
        }
        
        @Test
        @DisplayName("Should get credentials by last used")
        fun `should get credentials by last used`() = runTest {
            val lastUsedQueries = mockk<com.chimali.fido2.data.database.PasskeyCredentialQueries.SelectByLastUsed>()
            val query = mockk<com.squareup.sqldelight.Query>()
            every { lastUsedQueries.asFlow() } returns flowOf(query)
            every { query.executeAsList() } returns listOf(testCredentialEntity)
            every { database.passkeyCredentialQueries.selectByLastUsed(any()) } returns lastUsedQueries
            
            val result = dao.getCredentialsByLastUsed(50).toList()
            
            assertEquals(1, result.size)
            assertEquals(testCredentialEntity, result.first())
        }
        
        @Test
        @DisplayName("Should get credentials by creation date")
        fun `should get credentials by creation date`() = runTest {
            val creationQueries = mockk<com.chimali.fido2.data.database.PasskeyCredentialQueries.SelectByCreationDate>()
            val query = mockk<com.squareup.sqldelight.Query>()
            every { creationQueries.asFlow() } returns flowOf(query)
            every { query.executeAsList() } returns listOf(testCredentialEntity)
            every { database.passkeyCredentialQueries.selectByCreationDate(any()) } returns creationQueries
            
            val result = dao.getCredentialsByCreationDate(50).toList()
            
            assertEquals(1, result.size)
            assertEquals(testCredentialEntity, result.first())
        }
    }
    
    @Nested
    @DisplayName("Batch Operations")
    inner class BatchOperations {
        
        @Test
        @DisplayName("Should update multiple credentials")
        fun `should update multiple credentials`() = runTest {
            val credentials = listOf(testCredential, testCredential.copy(id = "cred2"))
            
            dao.updateCredentials(credentials)
            
            verify { database.passkeyCredentialQueries.update(any()) }
        }
        
        @Test
        @DisplayName("Should delete multiple credentials")
        fun `should delete multiple credentials`() = runTest {
            val credentialIds = listOf(testCredential.id, "cred2")
            
            val result = dao.deleteCredentials(credentialIds)
            
            assertEquals(2, result)
            verify { database.passkeyCredentialQueries.deleteById(testCredential.id) }
            verify { database.passkeyCredentialQueries.deleteById("cred2") }
        }
    }
    
    @Nested
    @DisplayName("Statistics Operations")
    inner class StatisticsOperations {
        
        @Test
        @DisplayName("Should get credential statistics")
        fun `should get credential statistics`() = runTest {
            val statsQueries = mockk<com.chimali.fido2.data.database.PasskeyCredentialQueries.GetStatisticsByRpId>()
            val rpStats = mockk<com.chimali.fido2.data.database.PasskeyCredentialQueries.GetStatisticsByRpId.RpStatistics>()
            every { rpStats.rp_id } returns testCredential.rpId
            every { rpStats.count } returns 3L
            every { statsQueries.executeAsList() } returns listOf(rpStats)
            every { database.passkeyCredentialQueries.getStatisticsByRpId() } returns statsQueries
            every { database.passkeyCredentialQueries.countAll() } returns mockk { every { executeAsOne() } returns 5L }
            
            val result = dao.getCredentialStatistics()
            
            assertEquals(5, result.totalCredentials)
            assertTrue(result.credentialsByRp.containsKey(testCredential.rpId))
            assertEquals(3L, result.credentialsByRp[testCredential.rpId])
        }
    }
    
    @Nested
    @DisplayName("Error Handling")
    inner class ErrorHandling {
        
        @Test
        @DisplayName("Should handle database connection errors")
        fun `should handle database connection errors`() = runTest {
            val selectQueries = mockk<com.chimali.fido2.data.database.PasskeyCredentialQueries.SelectById>()
            every { selectQueries.executeAsOneOrNull() } throws RuntimeException("Connection lost")
            every { database.passkeyCredentialQueries.selectById(any()) } returns selectQueries
            
            assertThrows<RuntimeException> {
                dao.getCredentialById(testCredential.id)
            }
        }
        
        @Test
        @DisplayName("Should handle query execution errors")
        fun `should handle query execution errors`() = runTest {
            val selectQueries = mockk<com.chimali.fido2.data.database.PasskeyCredentialQueries.SelectByRpId>()
            val query = mockk<com.squareup.sqldelight.Query>()
            every { selectQueries.asFlow() } returns flowOf(query)
            every { query.executeAsList() } throws RuntimeException("Query failed")
            every { database.passkeyCredentialQueries.selectByRpId(any()) } returns selectQueries
            
            assertThrows<RuntimeException> {
                dao.getCredentialsByRpId(testCredential.rpId).toList()
            }
        }
    }
}
