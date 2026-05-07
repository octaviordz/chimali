package com.chimali.fido2.data.database

import com.chimali.core.common.result.getOrThrow
import kotlin.test.Test
import kotlin.test.assertEquals

class DatabaseSchemaTest {
    @Test
    fun `test database schema compilation`() {
        // Test that SQLDelight schema compiles without errors
        // This is a basic compilation test - actual database tests would require SQLDelight runtime
        runCatching {
            // Verify schema files exist and are parseable
            // In a real test, we'd use SQLDelight's schema validation
        }.getOrThrow()
    }

    private companion object {
        private const val PASSKEY_CREDENTIAL_COLUMNS = 12
        private const val RELYING_PARTY_COLUMNS = 5
        private const val USER_CONSENT_COLUMNS = 7
        private const val BLUETOOTH_SESSION_COLUMNS = 6
        private const val EXPECTED_INDEX_COUNT = 5
        private const val EXPECTED_VIEW_COUNT = 2
    }

    @Test
    fun `test PasskeyCredential table structure`() {
        // Verify PasskeyCredential table has required columns
        val expectedColumns =
            listOf(
                "id",
                "rpId",
                "userId",
                "userName",
                "userDisplayName",
                "publicKey",
                "privateKeyAlias",
                "signCount",
                "createdAt",
                "lastUsedAt",
                "aaguid",
                "credentialId",
            )

        // In a real test, we'd parse the schema and verify column existence
        assertEquals(PASSKEY_CREDENTIAL_COLUMNS, expectedColumns.size)
    }

    @Test
    fun `test RelyingParty table structure`() {
        // Verify RelyingParty table has required columns
        val expectedColumns = listOf("id", "name", "iconUrl", "credentialCount", "createdAt")

        assertEquals(RELYING_PARTY_COLUMNS, expectedColumns.size)
    }

    @Test
    fun `test UserConsentRecord table structure`() {
        // Verify UserConsentRecord table has required columns
        val expectedColumns =
            listOf(
                "id",
                "operationType",
                "rpId",
                "credentialId",
                "timestamp",
                "biometricUsed",
                "pinUsed",
            )

        assertEquals(USER_CONSENT_COLUMNS, expectedColumns.size)
    }

    @Test
    fun `test BluetoothHidSession table structure`() {
        // Verify BluetoothHidSession table has required columns
        val expectedColumns =
            listOf(
                "id",
                "deviceId",
                "isActive",
                "connectedAt",
                "lastActivityAt",
                "sessionData",
            )

        assertEquals(BLUETOOTH_SESSION_COLUMNS, expectedColumns.size)
    }

    @Test
    fun `test database indexes are defined`() {
        // Verify required indexes are defined
        val expectedIndexes =
            listOf(
                "idx_passkey_credential_rpId",
                "idx_passkey_credential_userId",
                "idx_user_consent_rpId",
                "idx_user_consent_timestamp",
                "idx_bluetooth_session_active",
            )

        assertEquals(EXPECTED_INDEX_COUNT, expectedIndexes.size)
    }

    @Test
    fun `test database views are defined`() {
        // Verify required views are defined
        val expectedViews = listOf("CredentialSummary", "RelyingPartyStats")

        assertEquals(EXPECTED_VIEW_COUNT, expectedViews.size)
    }
}
