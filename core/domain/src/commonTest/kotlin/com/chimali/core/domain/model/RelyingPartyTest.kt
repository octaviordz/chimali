package com.chimali.core.domain.model

import com.chimali.core.domain.valueobject.RpId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlinx.datetime.Clock

class RelyingPartyTest {
    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `RP ID parser accepts secure origins domains and localhost forms`() {
        listOf(
            "example.com",
            "localhost",
            "localhost:8080",
            "https://example.com/path",
            "HTTP://example.com",
        ).forEach { rpId ->
            assertTrue(RelyingParty.isValidRpId(rpId), "Expected valid RP ID: $rpId")
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `RP ID parser rejects blank unsupported and malformed forms`() {
        listOf("", "  ", "ftp://example.com", "https://", "local host", "localhostish", "example com")
            .forEach { rpId ->
                assertFalse(RelyingParty.isValidRpId(rpId), "Expected invalid RP ID: $rpId")
            }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `normalization preserves HTTPS and localhost HTTP and upgrades other HTTP`() {
        assertEquals("https://example.com", RelyingParty.normalizeRpId("example.com"))
        assertEquals("https://example.com/path", RelyingParty.normalizeRpId("http://example.com/path"))
        assertEquals("https://example.com/path", RelyingParty.normalizeRpId("https://example.com/path"))
        assertEquals("http://localhost:8080/path", RelyingParty.normalizeRpId("http://localhost:8080/path"))
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `domain extraction and trust checks use the normalized host only`() {
        val rp = relyingParty(id = "https://example.com:8443/login")

        assertEquals("example.com:8443", rp.getDomain())
        assertTrue(rp.isTrusted(setOf("example.com:8443")))
        assertFalse(rp.isTrusted(setOf("example.com")))
        assertEquals("example.com", relyingParty(id = "example.com").getDomain())
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `factory derives a display name from a blank input`() {
        val originName = RelyingParty.create(RpId("https://example.com/login"), " ").name
        val domainName = RelyingParty.create(RpId("example.net"), "").name

        assertEquals("example.com", originName)
        assertEquals("example.net", domainName)
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `constructor accepts documented name and icon length boundaries`() {
        val rp =
            relyingParty(
                name = "N".repeat(RelyingParty.MAX_NAME_LENGTH),
                iconUrl = "https://" + "x".repeat(RelyingParty.MAX_ICON_URL_LENGTH - "https://".length),
            )

        assertEquals(RelyingParty.MAX_NAME_LENGTH, rp.name.length)
        assertEquals(RelyingParty.MAX_ICON_URL_LENGTH, rp.iconUrl?.length)
        assertFalse(rp.hasCredentials())
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `constructor rejects blank name invalid RP ID and negative credential count`() {
        assertFailsWith<IllegalArgumentException> { relyingParty(name = " ") }
        assertFailsWith<IllegalArgumentException> { relyingParty(id = "not a domain") }
        assertFailsWith<IllegalArgumentException> { relyingParty(credentialCount = -1) }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `constructor rejects invalid optional icon values`() {
        assertFailsWith<IllegalArgumentException> { relyingParty(iconUrl = " ") }
        assertFailsWith<IllegalArgumentException> {
            relyingParty(iconUrl = "https://" + "x".repeat(RelyingParty.MAX_ICON_URL_LENGTH))
        }
        assertFailsWith<IllegalArgumentException> { relyingParty(iconUrl = "file://icon.png") }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `constructor enforces future and last-used timestamp ordering`() {
        val now = Clock.System.now()
        assertFailsWith<IllegalArgumentException> { relyingParty(createdAt = now + 61.seconds) }
        assertFailsWith<IllegalArgumentException> {
            relyingParty(createdAt = now - 1.days, lastUsedAt = now + 61.seconds)
        }
        assertFailsWith<IllegalArgumentException> {
            relyingParty(createdAt = now, lastUsedAt = now - 1.seconds)
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `copy helpers update activity and recent-use outcomes`() {
        val now = Clock.System.now()
        val rp = relyingParty(createdAt = now - 40.days, lastUsedAt = now - 29.days)
        val countUpdated = rp.withCredentialCount(3)
        val activityUpdated = rp.withLastUsedAt(now)

        assertEquals(3, countUpdated.credentialCount)
        assertNotNull(countUpdated.lastUsedAt)
        assertEquals(now, activityUpdated.lastUsedAt)
        assertEquals(40, rp.getAgeInDays())
        assertTrue(rp.hasCredentials().not())
        assertTrue(rp.isRecentlyUsed())
        assertFalse(relyingParty(createdAt = now - 40.days, lastUsedAt = now - 31.days).isRecentlyUsed())
        assertFalse(relyingParty(lastUsedAt = null).isRecentlyUsed())
    }

    private fun relyingParty(
        id: String = "example.com",
        name: String = "Example",
        iconUrl: String? = null,
        credentialCount: Int = 0,
        createdAt: kotlinx.datetime.Instant = Clock.System.now() - 2.days,
        lastUsedAt: kotlinx.datetime.Instant? = null,
    ): RelyingParty =
        RelyingParty(
            id = RpId(id),
            name = name,
            iconUrl = iconUrl,
            credentialCount = credentialCount,
            createdAt = createdAt,
            lastUsedAt = lastUsedAt,
        )
}
