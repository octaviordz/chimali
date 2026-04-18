package com.chimali.fido2.domain.model

import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.test.BeforeTest
import kotlin.test.Ignore // DisplayName not in kotlin.test
import org.junit.jupiter.api.Nested
import kotlin.test.Test
import kotlin.test.assertFailsWith
import java.time.Instant

class RelyingPartyTest {
    private lateinit var testTimestamp: Instant

    @BeforeTest
    fun setUp() =
        runTest {
            testTimestamp = Instant.now()
        }

    @Nested
    inner class ValidationTests {
        @Test
        fun `should create valid rp with all required fields`() =
            runTest {
                val rp =
                    RelyingParty(
                        id = "https://example.com",
                        name = "Example Website",
                        iconUrl = null,
                        credentialCount = 0,
                        createdAt = testTimestamp,
                        lastUsedAt = null,
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
        fun `should create valid rp with icon url`() =
            runTest {
                val rp =
                    RelyingParty(
                        id = "https://example.com",
                        name = "Example Website",
                        iconUrl = "https://example.com/icon.png",
                        credentialCount = 5,
                        createdAt = testTimestamp,
                        lastUsedAt = testTimestamp,
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
        fun `should throw exception when id is blank`() =
            runTest {
                assertFailsWith<IllegalArgumentException> {
                    RelyingParty(
                        id = "",
                        name = "Example Website",
                        iconUrl = null,
                        credentialCount = 0,
                        createdAt = testTimestamp,
                        lastUsedAt = null,
                    )
                }
            }

        @Test
        fun `should throw exception when name is blank`() =
            runTest {
                assertFailsWith<IllegalArgumentException> {
                    RelyingParty(
                        id = "https://example.com",
                        name = "",
                        iconUrl = null,
                        credentialCount = 0,
                        createdAt = testTimestamp,
                        lastUsedAt = null,
                    )
                }
            }

        @Test
        fun `should throw exception when rp id is invalid`() =
            runTest {
                assertFailsWith<IllegalArgumentException> {
                    RelyingParty(
                        id = "ftp://invalid-rp-id",
                        name = "Example Website",
                        iconUrl = null,
                        credentialCount = 0,
                        createdAt = testTimestamp,
                        lastUsedAt = null,
                    )
                }
            }

        @Test
        fun `should throw exception when name exceeds maximum length`() =
            runTest {
                val longName = "a".repeat(65)
                assertFailsWith<IllegalArgumentException> {
                    RelyingParty(
                        id = "https://example.com",
                        name = longName,
                        iconUrl = null,
                        credentialCount = 0,
                        createdAt = testTimestamp,
                        lastUsedAt = null,
                    )
                }
            }

        @Test
        fun `should throw exception when credential count is negative`() =
            runTest {
                assertFailsWith<IllegalArgumentException> {
                    RelyingParty(
                        id = "https://example.com",
                        name = "Example Website",
                        iconUrl = null,
                        credentialCount = -1,
                        createdAt = testTimestamp,
                        lastUsedAt = null,
                    )
                }
            }

        @Test
        fun `should throw exception when icon url is invalid`() =
            runTest {
                assertFailsWith<IllegalArgumentException> {
                    RelyingParty(
                        id = "https://example.com",
                        name = "Example Website",
                        iconUrl = "invalid-url",
                        credentialCount = 0,
                        createdAt = testTimestamp,
                        lastUsedAt = null,
                    )
                }
            }

        @Test
        fun `should throw exception when icon url uses unsupported protocol`() =
            runTest {
                assertFailsWith<IllegalArgumentException> {
                    RelyingParty(
                        id = "https://example.com",
                        name = "Example Website",
                        iconUrl = "ftp://example.com/icon.png",
                        credentialCount = 0,
                        createdAt = testTimestamp,
                        lastUsedAt = null,
                    )
                }
            }

        @Test
        fun `should throw exception when last used time is before creation time`() =
            runTest {
                val pastTimestamp = testTimestamp.minusSeconds(60)
                assertFailsWith<IllegalArgumentException> {
                    RelyingParty(
                        id = "https://example.com",
                        name = "Example Website",
                        iconUrl = null,
                        credentialCount = 0,
                        createdAt = testTimestamp,
                        lastUsedAt = pastTimestamp,
                    )
                }
            }
    }

    @Nested
    inner class BusinessLogicTests {
        private lateinit var rp: RelyingParty

        @BeforeTest
        fun setUp() =
            runTest {
                rp =
                    RelyingParty(
                        id = "https://example.com",
                        name = "Example Website",
                        iconUrl = "https://example.com/icon.png",
                        credentialCount = 5,
                        createdAt = testTimestamp,
                        lastUsedAt = testTimestamp,
                    )
            }

        @Test
        fun `should correctly check if rp has credentials`() =
            runTest {
                assertTrue(rp.hasCredentials())

                val rpWithoutCredentials = rp.copy(credentialCount = 0)
                assertFalse(rpWithoutCredentials.hasCredentials())
            }

        @Test
        fun `should extract domain from rp id`() =
            runTest {
                assertEquals("example.com", rp.getDomain())

                val rpWithPath = rp.copy(id = "https://subdomain.example.com/path/to/resource")
                assertEquals("subdomain.example.com", rpWithPath.getDomain())

                val rpWithPort = rp.copy(id = "https://localhost:8080")
                assertEquals("localhost:8080", rpWithPort.getDomain())
            }

        @Test
        fun `should handle invalid rp id when extracting domain`() =
            runTest {
                // A bare hostname without a dot (e.g. "invalid-url") is now rejected
                // by domain validation. This verifies that behavior.
                assertFailsWith<IllegalArgumentException> {
                    rp.copy(id = "invalid-url")
                }
            }

        @Test
        fun `should correctly check if rp is trusted`() =
            runTest {
                val trustedDomains = setOf("example.com", "trusted.com")

                assertTrue(rp.isTrusted(trustedDomains))

                val untrustedRp = rp.copy(id = "https://untrusted.com")
                assertFalse(untrustedRp.isTrusted(trustedDomains))
            }

        @Test
        fun `should return safe name`() =
            runTest {
                assertEquals("Example Website", rp.name)

                val rpWithBlankName = RelyingParty.create(id = "https://example.com", name = "")
                assertEquals("example.com", rpWithBlankName.name)
            }

        @Test
        fun `should return correct age in days`() =
            runTest {
                val age = rp.getAgeInDays()
                assertTrue(age >= 0) // Should be non-negative
                assertTrue(age <= 1) // Should be very recent
            }

        @Test
        fun `should create rp with updated credential count`() =
            runTest {
                val updatedRp = rp.withCredentialCount(10)

                assertEquals(10, updatedRp.credentialCount)
                assertEquals(rp.id, updatedRp.id)
                assertEquals(rp.name, updatedRp.name)
                assertNotEquals(rp.lastUsedAt, updatedRp.lastUsedAt) // Should be updated
            }

        @Test
        fun `should create rp with updated last used time`() =
            runTest {
                val newLastUsedAt = rp.createdAt.plusSeconds(30)
                val updatedRp = rp.withLastUsedAt(newLastUsedAt)

                assertEquals(newLastUsedAt, updatedRp.lastUsedAt)
                assertEquals(rp.id, updatedRp.id)
                assertEquals(rp.name, updatedRp.name)
            }

        @Test
        fun `should correctly check if rp was recently used`() =
            runTest {
                assertTrue(rp.isRecentlyUsed(30)) // Should be recent

                val oldCreatedAt = Instant.now().minusSeconds(32 * 24 * 60 * 60)
                val oldRp =
                    rp.copy(
                        createdAt = oldCreatedAt,
                        lastUsedAt = oldCreatedAt.plusSeconds(1 * 24 * 60 * 60),
                    ) // 31 days ago
                assertFalse(oldRp.isRecentlyUsed(30))

                val recentCreatedAt = Instant.now().minusSeconds(30 * 24 * 60 * 60)
                val recentRp =
                    rp.copy(
                        createdAt = recentCreatedAt,
                        lastUsedAt = recentCreatedAt.plusSeconds(1 * 24 * 60 * 60),
                    ) // 29 days ago
                assertTrue(recentRp.isRecentlyUsed(30))
            }
    }

    @Nested
    inner class CompanionObjectTests {
        @Test
        fun `should create rp using companion object factory method`() =
            runTest {
                val rp =
                    RelyingParty.create(
                        id = "https://example.com",
                        name = "Example Website",
                        iconUrl = "https://example.com/icon.png",
                    )

                assertNotNull(rp)
                assertEquals("https://example.com", rp.id)
                assertEquals("Example Website", rp.name)
                assertEquals("https://example.com/icon.png", rp.iconUrl)
                assertEquals(0, rp.credentialCount) // Should start at 0
                assertNull(rp.lastUsedAt) // Should be null initially
            }

        @Test
        fun `should create rp without icon using companion object`() =
            runTest {
                val rp =
                    RelyingParty.create(
                        id = "https://example.com",
                        name = "Example Website",
                    )

                assertNotNull(rp)
                assertEquals("https://example.com", rp.id)
                assertEquals("Example Website", rp.name)
                assertNull(rp.iconUrl)
            }

        @Test
        fun `should validate rp id format correctly`() =
            runTest {
                assertTrue(RelyingParty.isValidRpId("https://example.com"))
                assertTrue(RelyingParty.isValidRpId("http://localhost:8080"))
                assertTrue(RelyingParty.isValidRpId("https://subdomain.example.com/path"))

                assertFalse(RelyingParty.isValidRpId("invalid-url"))
                assertFalse(RelyingParty.isValidRpId("ftp://example.com"))
                assertFalse(RelyingParty.isValidRpId(""))
                assertFalse(RelyingParty.isValidRpId(" "))
            }

        @Test
        fun `should normalize rp id to https format`() =
            runTest {
                assertEquals("https://example.com", RelyingParty.normalizeRpId("example.com"))
                assertEquals("https://example.com", RelyingParty.normalizeRpId("http://example.com"))
                assertEquals("https://example.com", RelyingParty.normalizeRpId("https://example.com"))
                assertEquals("http://localhost:8080", RelyingParty.normalizeRpId("http://localhost:8080"))
            }

        @Test
        fun `should have correct constant values`() =
            runTest {
                assertEquals(64, RelyingParty.MAX_NAME_LENGTH)
                assertEquals(256, RelyingParty.MAX_ICON_URL_LENGTH)
            }
    }

    @Nested
    inner class EdgeCases {
        @Test
        fun `should handle maximum allowed field sizes`() =
            runTest {
                val maxName = "a".repeat(64)
                val maxIconUrl = "https://example.com/" + "a".repeat(236) // Total 256 chars

                val rp =
                    RelyingParty(
                        id = "https://example.com",
                        name = maxName,
                        iconUrl = maxIconUrl,
                        credentialCount = 0,
                        createdAt = testTimestamp,
                        lastUsedAt = null,
                    )

                assertNotNull(rp)
                assertEquals(maxName, rp.name)
                assertEquals(maxIconUrl, rp.iconUrl)
            }

        @Test
        fun `should handle http rp id`() =
            runTest {
                val rp =
                    RelyingParty(
                        id = "http://localhost:8080",
                        name = "Local Development",
                        iconUrl = null,
                        credentialCount = 0,
                        createdAt = testTimestamp,
                        lastUsedAt = null,
                    )

                assertNotNull(rp)
                assertEquals("http://localhost:8080", rp.id)
            }

        @Test
        fun `should handle rp with subdomains and paths`() =
            runTest {
                val rp =
                    RelyingParty(
                        id = "https://api.subdomain.example.com/v1/auth",
                        name = "API Service",
                        iconUrl = null,
                        credentialCount = 0,
                        createdAt = testTimestamp,
                        lastUsedAt = null,
                    )

                assertNotNull(rp)
                assertEquals("api.subdomain.example.com", rp.getDomain())
            }

        @Test
        fun `should handle rp with port numbers`() =
            runTest {
                val rp =
                    RelyingParty(
                        id = "https://localhost:3000",
                        name = "Local Development",
                        iconUrl = null,
                        credentialCount = 0,
                        createdAt = testTimestamp,
                        lastUsedAt = null,
                    )

                assertNotNull(rp)
                assertEquals("localhost:3000", rp.getDomain())
            }
    }
}
