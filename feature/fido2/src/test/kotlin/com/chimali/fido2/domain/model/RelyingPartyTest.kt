package com.chimali.fido2.domain.model

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.net.URI

@DisplayName("RelyingParty Domain Model Tests")
class RelyingPartyTest {
    
    private lateinit var testTimestamp: Instant
    
    @BeforeEach
    fun setUp() = runTest {
        testTimestamp = Instant.now()
    }
    
    @Nested
    @DisplayName("Validation Tests")
    inner class ValidationTests {
        
        @Test
        @DisplayName("Should create valid RP with all required fields")
        fun `should create valid rp with all required fields`() = runTest {
            val rp = RelyingParty(
                id = "https://example.com",
                name = "Example Website",
                iconUrl = null,
                credentialCount = 0,
                createdAt = testTimestamp,
                lastUsedAt = null
            )
            
            assertNotNull(rp)
            assertEquals("https://example.com", rp.id)
            assertEquals("Example Website", rp.name)
            assertNull(rp.iconUrl)
            assertEquals(0, rp.credentialCount)
            assertEquals(testTimestamp, rp.createdAt)
            assertNull(rp.lastUsedAt)
        }
        
        @Test
        @DisplayName("Should create valid RP with icon URL")
        fun `should create valid rp with icon url`() = runTest {
            val rp = RelyingParty(
                id = "https://example.com",
                name = "Example Website",
                iconUrl = "https://example.com/icon.png",
                credentialCount = 5,
                createdAt = testTimestamp,
                lastUsedAt = testTimestamp
            )
            
            assertNotNull(rp)
            assertEquals("https://example.com", rp.id)
            assertEquals("Example Website", rp.name)
            assertEquals("https://example.com/icon.png", rp.iconUrl)
            assertEquals(5, rp.credentialCount)
            assertEquals(testTimestamp, rp.createdAt)
            assertEquals(testTimestamp, rp.lastUsedAt)
        }
        
        @Test
        @DisplayName("Should throw exception when ID is blank")
        fun `should throw exception when id is blank`() = runTest {
            assertThrows<IllegalArgumentException> {
                RelyingParty(
                    id = "",
                    name = "Example Website",
                    iconUrl = null,
                    credentialCount = 0,
                    createdAt = testTimestamp,
                    lastUsedAt = null
                )
            }
        }
        
        @Test
        @DisplayName("Should throw exception when name is blank")
        fun `should throw exception when name is blank`() = runTest {
            assertThrows<IllegalArgumentException> {
                RelyingParty(
                    id = "https://example.com",
                    name = "",
                    iconUrl = null,
                    credentialCount = 0,
                    createdAt = testTimestamp,
                    lastUsedAt = null
                )
            }
        }
        
        @Test
        @DisplayName("Should throw exception when RP ID is invalid")
        fun `should throw exception when rp id is invalid`() = runTest {
            assertThrows<IllegalArgumentException> {
                RelyingParty(
                    id = "invalid-rp-id",
                    name = "Example Website",
                    iconUrl = null,
                    credentialCount = 0,
                    createdAt = testTimestamp,
                    lastUsedAt = null
                )
            }
        }
        
        @Test
        @DisplayName("Should throw exception when name exceeds maximum length")
        fun `should throw exception when name exceeds maximum length`() = runTest {
            val longName = "a".repeat(65)
            assertThrows<IllegalArgumentException> {
                RelyingParty(
                    id = "https://example.com",
                    name = longName,
                    iconUrl = null,
                    credentialCount = 0,
                    createdAt = testTimestamp,
                    lastUsedAt = null
                )
            }
        }
        
        @Test
        @DisplayName("Should throw exception when credential count is negative")
        fun `should throw exception when credential count is negative`() = runTest {
            assertThrows<IllegalArgumentException> {
                RelyingParty(
                    id = "https://example.com",
                    name = "Example Website",
                    iconUrl = null,
                    credentialCount = -1,
                    createdAt = testTimestamp,
                    lastUsedAt = null
                )
            }
        }
        
        @Test
        @DisplayName("Should throw exception when icon URL is invalid")
        fun `should throw exception when icon url is invalid`() = runTest {
            assertThrows<IllegalArgumentException> {
                RelyingParty(
                    id = "https://example.com",
                    name = "Example Website",
                    iconUrl = "invalid-url",
                    credentialCount = 0,
                    createdAt = testTimestamp,
                    lastUsedAt = null
                )
            }
        }
        
        @Test
        @DisplayName("Should throw exception when icon URL uses unsupported protocol")
        fun `should throw exception when icon url uses unsupported protocol`() = runTest {
            assertThrows<IllegalArgumentException> {
                RelyingParty(
                    id = "https://example.com",
                    name = "Example Website",
                    iconUrl = "ftp://example.com/icon.png",
                    credentialCount = 0,
                    createdAt = testTimestamp,
                    lastUsedAt = null
                )
            }
        }
        
        @Test
        @DisplayName("Should throw exception when last used time is before creation time")
        fun `should throw exception when last used time is before creation time`() = runTest {
            val pastTimestamp = testTimestamp.minusSeconds(60)
            assertThrows<IllegalArgumentException> {
                RelyingParty(
                    id = "https://example.com",
                    name = "Example Website",
                    iconUrl = null,
                    credentialCount = 0,
                    createdAt = testTimestamp,
                    lastUsedAt = pastTimestamp
                )
            }
        }
    }
    
    @Nested
    @DisplayName("Business Logic Tests")
    inner class BusinessLogicTests {
        
        private lateinit var rp: RelyingParty
        
        @BeforeEach
        fun setUp() = runTest {
            rp = RelyingParty(
                id = "https://example.com",
                name = "Example Website",
                iconUrl = "https://example.com/icon.png",
                credentialCount = 5,
                createdAt = testTimestamp,
                lastUsedAt = testTimestamp
            )
        }
        
        @Test
        @DisplayName("Should correctly check if RP has credentials")
        fun `should correctly check if rp has credentials`() = runTest {
            assertTrue(rp.hasCredentials())
            
            val rpWithoutCredentials = rp.copy(credentialCount = 0)
            assertFalse(rpWithoutCredentials.hasCredentials())
        }
        
        @Test
        @DisplayName("Should extract domain from RP ID")
        fun `should extract domain from rp id`() = runTest {
            assertEquals("example.com", rp.getDomain())
            
            val rpWithPath = rp.copy(id = "https://subdomain.example.com/path/to/resource")
            assertEquals("subdomain.example.com", rpWithPath.getDomain())
            
            val rpWithPort = rp.copy(id = "https://localhost:8080")
            assertEquals("localhost:8080", rpWithPort.getDomain())
        }
        
        @Test
        @DisplayName("Should handle invalid RP ID when extracting domain")
        fun `should handle invalid rp id when extracting domain`() = runTest {
            val rpWithInvalidId = rp.copy(id = "invalid-url")
            assertEquals("invalid-url", rpWithInvalidId.getDomain())
        }
        
        @Test
        @DisplayName("Should correctly check if RP is trusted")
        fun `should correctly check if rp is trusted`() = runTest {
            val trustedDomains = setOf("example.com", "trusted.com")
            
            assertTrue(rp.isTrusted(trustedDomains))
            
            val untrustedRp = rp.copy(id = "https://untrusted.com")
            assertFalse(untrustedRp.isTrusted(trustedDomains))
        }
        
        @Test
        @DisplayName("Should return safe name")
        fun `should return safe name`() = runTest {
            assertEquals("Example Website", rp.name)
            
            val rpWithBlankName = rp.copy(name = "")
            assertEquals("example.com", rpWithBlankName.name)
        }
        
        @Test
        @DisplayName("Should return correct age in days")
        fun `should return correct age in days`() = runTest {
            val age = rp.getAgeInDays()
            assertTrue(age >= 0) // Should be non-negative
            assertTrue(age <= 1) // Should be very recent
        }
        
        @Test
        @DisplayName("Should create RP with updated credential count")
        fun `should create rp with updated credential count`() = runTest {
            val updatedRp = rp.withCredentialCount(10)
            
            assertEquals(10, updatedRp.credentialCount)
            assertEquals(rp.id, updatedRp.id)
            assertEquals(rp.name, updatedRp.name)
            assertNotEquals(rp.lastUsedAt, updatedRp.lastUsedAt) // Should be updated
        }
        
        @Test
        @DisplayName("Should create RP with updated last used time")
        fun `should create rp with updated last used time`() = runTest {
            val newLastUsedAt = Instant.now().plusSeconds(60)
            val updatedRp = rp.withLastUsedAt(newLastUsedAt)
            
            assertEquals(newLastUsedAt, updatedRp.lastUsedAt)
            assertEquals(rp.id, updatedRp.id)
            assertEquals(rp.name, updatedRp.name)
        }
        
        @Test
        @DisplayName("Should correctly check if RP was recently used")
        fun `should correctly check if rp was recently used`() = runTest {
            assertTrue(rp.isRecentlyUsed(30)) // Should be recent
            
            val oldRp = rp.copy(lastUsedAt = Instant.now().minusSeconds(31 * 24 * 60 * 60)) // 31 days ago
            assertFalse(oldRp.isRecentlyUsed(30))
            
            val recentRp = rp.copy(lastUsedAt = Instant.now().minusSeconds(29 * 24 * 60 * 60)) // 29 days ago
            assertTrue(recentRp.isRecentlyUsed(30))
        }
    }
    
    @Nested
    @DisplayName("Companion Object Tests")
    inner class CompanionObjectTests {
        
        @Test
        @DisplayName("Should create RP using companion object factory method")
        fun `should create rp using companion object factory method`() = runTest {
            val rp = RelyingParty.create(
                id = "https://example.com",
                name = "Example Website",
                iconUrl = "https://example.com/icon.png"
            )
            
            assertNotNull(rp)
            assertEquals("https://example.com", rp.id)
            assertEquals("Example Website", rp.name)
            assertEquals("https://example.com/icon.png", rp.iconUrl)
            assertEquals(0, rp.credentialCount) // Should start at 0
            assertNull(rp.lastUsedAt) // Should be null initially
        }
        
        @Test
        @DisplayName("Should create RP without icon using companion object")
        fun `should create rp without icon using companion object`() = runTest {
            val rp = RelyingParty.create(
                id = "https://example.com",
                name = "Example Website"
            )
            
            assertNotNull(rp)
            assertEquals("https://example.com", rp.id)
            assertEquals("Example Website", rp.name)
            assertNull(rp.iconUrl)
        }
        
        @Test
        @DisplayName("Should validate RP ID format correctly")
        fun `should validate rp id format correctly`() = runTest {
            assertTrue(RelyingParty.isValidRpId("https://example.com"))
            assertTrue(RelyingParty.isValidRpId("http://localhost:8080"))
            assertTrue(RelyingParty.isValidRpId("https://subdomain.example.com/path"))
            
            assertFalse(RelyingParty.isValidRpId("invalid-url"))
            assertFalse(RelyingParty.isValidRpId("ftp://example.com"))
            assertFalse(RelyingParty.isValidRpId(""))
            assertFalse(RelyingParty.isValidRpId(" "))
        }
        
        @Test
        @DisplayName("Should normalize RP ID to HTTPS format")
        fun `should normalize rp id to https format`() = runTest {
            assertEquals("https://example.com", RelyingParty.normalizeRpId("example.com"))
            assertEquals("https://example.com", RelyingParty.normalizeRpId("http://example.com"))
            assertEquals("https://example.com", RelyingParty.normalizeRpId("https://example.com"))
            assertEquals("http://localhost:8080", RelyingParty.normalizeRpId("http://localhost:8080"))
        }
        
        @Test
        @DisplayName("Should have correct constant values")
        fun `should have correct constant values`() = runTest {
            assertEquals(64, RelyingParty.MAX_NAME_LENGTH)
            assertEquals(256, RelyingParty.MAX_ICON_URL_LENGTH)
        }
    }
    
    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCases {
        
        @Test
        @DisplayName("Should handle maximum allowed field sizes")
        fun `should handle maximum allowed field sizes`() = runTest {
            val maxName = "a".repeat(64)
            val maxIconUrl = "https://example.com/" + "a".repeat(236) // Total 256 chars
            
            val rp = RelyingParty(
                id = "https://example.com",
                name = maxName,
                iconUrl = maxIconUrl,
                credentialCount = 0,
                createdAt = testTimestamp,
                lastUsedAt = null
            )
            
            assertNotNull(rp)
            assertEquals(maxName, rp.name)
            assertEquals(maxIconUrl, rp.iconUrl)
        }
        
        @Test
        @DisplayName("Should handle HTTP RP ID")
        fun `should handle http rp id`() = runTest {
            val rp = RelyingParty(
                id = "http://localhost:8080",
                name = "Local Development",
                iconUrl = null,
                credentialCount = 0,
                createdAt = testTimestamp,
                lastUsedAt = null
            )
            
            assertNotNull(rp)
            assertEquals("http://localhost:8080", rp.id)
        }
        
        @Test
        @DisplayName("Should handle RP with subdomains and paths")
        fun `should handle rp with subdomains and paths`() = runTest {
            val rp = RelyingParty(
                id = "https://api.subdomain.example.com/v1/auth",
                name = "API Service",
                iconUrl = null,
                credentialCount = 0,
                createdAt = testTimestamp,
                lastUsedAt = null
            )
            
            assertNotNull(rp)
            assertEquals("api.subdomain.example.com", rp.getDomain())
        }
        
        @Test
        @DisplayName("Should handle RP with port numbers")
        fun `should handle rp with port numbers`() = runTest {
            val rp = RelyingParty(
                id = "https://localhost:3000",
                name = "Local Development",
                iconUrl = null,
                credentialCount = 0,
                createdAt = testTimestamp,
                lastUsedAt = null
            )
            
            assertNotNull(rp)
            assertEquals("localhost:3000", rp.getDomain())
        }
    }
}
