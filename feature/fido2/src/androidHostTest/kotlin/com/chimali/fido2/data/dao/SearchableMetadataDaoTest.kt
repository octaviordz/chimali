package com.chimali.fido2.data.dao

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.chimali.fido2.data.database.Fido2Database
import com.chimali.fido2.data.database.Passkey_credential
import com.chimali.fido2.data.database.User_consent_record
import com.chimali.fido2.data.mapper.eraseSensitiveMetadata
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * T024 [US2] — DAO exact-match token query tests.
 *
 * Verifies that SQLDelight queries on lookup-token columns (rp_id_index, user_id_index)
 * return the correct records and that token-based lookups are exact-match only.
 */
class SearchableMetadataDaoTest {
    private lateinit var database: Fido2Database
    private lateinit var driver: JdbcSqliteDriver
    private val syntheticBuffers = mutableListOf<ByteArray>()
    private val retainedCredentialRows = mutableListOf<Passkey_credential>()
    private val retainedConsentRows = mutableListOf<User_consent_record>()

    @BeforeEach
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Fido2Database.Schema.create(driver)
        database = Fido2Database(driver)
    }

    @AfterEach
    fun tearDown() {
        retainedCredentialRows.forEach { it.eraseSensitiveMetadata() }
        retainedConsentRows.forEach { it.eraseSensitiveMetadata() }
        val credentialRows =
            database.passkeyCredentialQueries
                .select_all()
                .executeAsList()
        val consentRows =
            database.userConsentRecordQueries
                .select_all(100L)
                .executeAsList()
        credentialRows.forEach { it.eraseSensitiveMetadata() }
        consentRows.forEach { it.eraseSensitiveMetadata() }
        syntheticBuffers.forEach { it.fill(0) }
        assertTrue(syntheticBuffers.all { buffer -> buffer.all { it == 0.toByte() } })
        assertCredentialRowsErased(retainedCredentialRows)
        assertCredentialRowsErased(credentialRows)
        assertConsentRowsErased(retainedConsentRows)
        assertConsentRowsErased(consentRows)
        driver.close()
    }

    // -- PasskeyCredential token queries --

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `select_by_rp_id returns credentials matching rp_id_index token`() {
        val rpIdIndex = syntheticBytes(1, 2, 3)
        seedCredential("cred-1", rpIdIndex = rpIdIndex, userIdIndex = syntheticBytes(10))
        seedCredential("cred-2", rpIdIndex = syntheticBytes(99, 98), userIdIndex = syntheticBytes(20))

        val results =
            retainCredentialRows(
                database.passkeyCredentialQueries.select_by_rp_id(rpIdIndex).executeAsList(),
            )
        assertEquals(1, results.size)
        assertEquals("cred-1", results[0].id)
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `select_by_user_id returns credentials matching user_id_index token`() {
        val userIdIndex = syntheticBytes(4, 5, 6)
        seedCredential("cred-1", rpIdIndex = syntheticBytes(10), userIdIndex = userIdIndex)
        seedCredential("cred-2", rpIdIndex = syntheticBytes(20), userIdIndex = syntheticBytes(99))

        val results =
            retainCredentialRows(
                database.passkeyCredentialQueries.select_by_user_id(userIdIndex).executeAsList(),
            )
        assertEquals(1, results.size)
        assertEquals("cred-1", results[0].id)
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `select_by_rp_id_and_user_id returns credentials matching both tokens`() {
        val rpIdIndex = syntheticBytes(1, 2)
        val userIdIndex = syntheticBytes(3, 4)
        seedCredential("match", rpIdIndex = rpIdIndex, userIdIndex = userIdIndex)
        seedCredential("rp-only", rpIdIndex = rpIdIndex, userIdIndex = syntheticBytes(99))
        seedCredential("user-only", rpIdIndex = syntheticBytes(99), userIdIndex = userIdIndex)

        val results =
            retainCredentialRows(
                database.passkeyCredentialQueries
                    .select_by_rp_id_and_user_id(rpIdIndex, userIdIndex)
                    .executeAsList(),
            )
        assertEquals(1, results.size)
        assertEquals("match", results[0].id)
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `count_by_rp_id returns correct count for token`() {
        val rpIdIndex = syntheticBytes(1, 2, 3)
        seedCredential("cred-1", rpIdIndex = rpIdIndex, userIdIndex = syntheticBytes(10))
        seedCredential("cred-2", rpIdIndex = rpIdIndex, userIdIndex = syntheticBytes(20))
        seedCredential("cred-3", rpIdIndex = syntheticBytes(99), userIdIndex = syntheticBytes(30))

        val count = database.passkeyCredentialQueries.count_by_rp_id(rpIdIndex).executeAsOne()
        assertEquals(2L, count)
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `delete_by_rp_id removes only matching token credentials`() {
        val rpIdIndex = syntheticBytes(1, 2, 3)
        seedCredential("cred-1", rpIdIndex = rpIdIndex, userIdIndex = syntheticBytes(10))
        seedCredential("cred-2", rpIdIndex = syntheticBytes(99), userIdIndex = syntheticBytes(20))

        database.passkeyCredentialQueries.delete_by_rp_id(rpIdIndex)
        val remaining =
            retainCredentialRows(database.passkeyCredentialQueries.select_all().executeAsList())
        assertEquals(1, remaining.size)
        assertEquals("cred-2", remaining[0].id)
    }

    // -- UserConsentRecord token queries --

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `select_by_rp_id returns consent records matching rp_id_index token`() {
        val rpIdIndex = syntheticBytes(7, 8, 9)
        seedRelayingParty("rp-1")
        seedConsent("consent-1", rpId = "rp-1", rpIdIndex = rpIdIndex)
        seedConsent("consent-2", rpId = "rp-1", rpIdIndex = syntheticBytes(99))

        val results =
            retainConsentRows(
                database.userConsentRecordQueries
                    .select_by_rp_id(rpIdIndex, 100L)
                    .executeAsList(),
            )
        assertEquals(1, results.size)
        assertEquals("consent-1", results[0].id)
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `count_by_rp_id returns correct consent count for token`() {
        val rpIdIndex = syntheticBytes(7, 8)
        seedRelayingParty("rp-1")
        seedConsent("c-1", rpId = "rp-1", rpIdIndex = rpIdIndex)
        seedConsent("c-2", rpId = "rp-1", rpIdIndex = rpIdIndex)
        seedConsent("c-3", rpId = "rp-1", rpIdIndex = syntheticBytes(99))

        val count = database.userConsentRecordQueries.count_by_rp_id(rpIdIndex).executeAsOne()
        assertEquals(2L, count)
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `search_by_rp_id uses token for exact RP match and LIKE for display search`() {
        val rpIdIndex = syntheticBytes(1, 2, 3)
        seedCredential("cred-1", rpIdIndex = rpIdIndex, userIdIndex = syntheticBytes(10), userName = "alice")
        seedCredential("cred-2", rpIdIndex = rpIdIndex, userIdIndex = syntheticBytes(20), userName = "bob")

        val results =
            retainCredentialRows(
                database.passkeyCredentialQueries
                    .search_by_rp_id(rp_id_index = rpIdIndex, query = "%ali%")
                    .executeAsList(),
            )
        assertEquals(1, results.size)
        assertEquals("cred-1", results[0].id)
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `null tokens return no results for token-based queries`() {
        seedCredential("cred-1", rpIdIndex = null, userIdIndex = null)

        val rpResults =
            retainCredentialRows(
                database.passkeyCredentialQueries.select_by_rp_id(syntheticBytes(1, 2)).executeAsList(),
            )
        assertTrue(rpResults.isEmpty())

        val userResults =
            retainCredentialRows(
                database.passkeyCredentialQueries.select_by_user_id(syntheticBytes(3, 4)).executeAsList(),
            )
        assertTrue(userResults.isEmpty())
    }

    // -- Helpers --

    private fun syntheticBytes(vararg values: Int): ByteArray =
        ByteArray(values.size) { values[it].toByte() }.also { syntheticBuffers.add(it) }

    private fun syntheticText(value: String): ByteArray = value.encodeToByteArray().also { syntheticBuffers.add(it) }

    private fun retainCredentialRows(rows: List<Passkey_credential>): List<Passkey_credential> =
        rows.also { retainedCredentialRows.addAll(it) }

    private fun retainConsentRows(rows: List<User_consent_record>): List<User_consent_record> =
        rows.also { retainedConsentRows.addAll(it) }

    private fun assertCredentialRowsErased(rows: List<Passkey_credential>) {
        assertTrue(
            rows.all { row ->
                listOf(
                    row.rp_id,
                    row.rp_name,
                    row.user_display_name,
                    row.user_id,
                    row.user_name,
                    row.rp_id_index,
                    row.user_id_index,
                    row.encrypted_metadata,
                ).all { buffer -> buffer == null || buffer.all { it == 0.toByte() } }
            },
        )
    }

    private fun assertConsentRowsErased(rows: List<User_consent_record>) {
        assertTrue(
            rows.all { row ->
                listOf(row.rp_id, row.rp_id_index).all { buffer ->
                    buffer == null || buffer.all { it == 0.toByte() }
                }
            },
        )
    }

    private fun seedRelayingParty(id: String) {
        database.relyingPartyQueries.insert(
            id = syntheticText(id),
            created_at = 1000L,
            updated_at = 1000L,
            last_used_at = null,
            credential_count = 0L,
            icon_url = null,
            is_blocked = 0L,
            name = syntheticText("RP $id"),
            encrypted_metadata = null,
        )
    }

    private fun seedCredential(
        id: String,
        rpIdIndex: ByteArray? = null,
        userIdIndex: ByteArray? = null,
        userName: String = "user",
    ) {
        database.passkeyCredentialQueries.insert(
            id = id,
            created_at = 1000L,
            updated_at = 1000L,
            last_used_at = 2000L,
            aaguid = "aaguid",
            cose_algorithm = -7L,
            credential_id = "cred-id-$id",
            cred_protect_policy = 1L,
            label = null,
            derivation_profile = "chimali-local-hdk-p256-v1",
            public_key = "pubkey-$id",
            rp_id = syntheticText("rp-1"),
            rp_name = syntheticText("RP Name"),
            sign_count = 0L,
            user_display_name = syntheticText("Display"),
            user_id = syntheticText("user-$id"),
            user_name = syntheticText(userName),
            rp_id_index = rpIdIndex,
            user_id_index = userIdIndex,
            encrypted_metadata = null,
        )
    }

    private fun seedConsent(
        id: String,
        rpId: String,
        rpIdIndex: ByteArray? = null,
    ) {
        database.userConsentRecordQueries.insert(
            id = id,
            created_at = 1000L,
            timestamp = 1000L,
            updated_at = 1000L,
            biometric_used = 0L,
            credential_id = null,
            device_id = null,
            ip_address = null,
            operation_type = "AUTHENTICATION",
            pin_used = 0L,
            rp_id = syntheticText(rpId),
            user_agent = null,
            rp_id_index = rpIdIndex,
        )
    }
}
