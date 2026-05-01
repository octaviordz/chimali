package com.chimali.core.domain.model

import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.RpId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class DomainModelSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testRelyingPartySerialization() {
        val rp =
            RelyingParty(
                id = RpId("https://example.com"),
                name = "Example RP",
                iconUrl = "https://example.com/icon.png",
                credentialCount = 5,
                createdAt = Clock.System.now(),
                lastUsedAt = Clock.System.now(),
                isBlocked = false,
            )

        val encoded = json.encodeToString(rp)
        val decoded = json.decodeFromString<RelyingParty>(encoded)

        assertEquals(rp.id, decoded.id)
        assertEquals(rp.name, decoded.name)
        assertEquals(rp.iconUrl, decoded.iconUrl)
        assertEquals(rp.credentialCount, decoded.credentialCount)
        assertEquals(rp.createdAt, decoded.createdAt)
        assertEquals(rp.lastUsedAt, decoded.lastUsedAt)
        assertEquals(rp.isBlocked, decoded.isBlocked)
    }

    @Test
    fun testCredentialSummarySerialization() {
        val summary =
            CredentialSummary(
                id = "test-uuid",
                rpId = RpId("example.com"),
                credentialId = CredentialId.fromByteArray("test-id".encodeToByteArray()),
                lastUsedAt = Clock.System.now(),
                coseAlgorithm = -7,
                credProtectPolicy = 1,
            )

        val encoded = json.encodeToString(summary)
        val decoded = json.decodeFromString<CredentialSummary>(encoded)

        assertEquals(summary.id, decoded.id)
        assertEquals(summary.rpId, decoded.rpId)
        assertEquals(summary.credentialId, decoded.credentialId)
        assertEquals(summary.lastUsedAt, decoded.lastUsedAt)
        assertEquals(summary.coseAlgorithm, decoded.coseAlgorithm)
        assertEquals(summary.credProtectPolicy, decoded.credProtectPolicy)
    }

    @Test
    fun testUserConsentRecordSerialization() {
        val record =
            UserConsentRecord(
                id = "consent-uuid",
                operationType = ConsentOperationType.AUTHENTICATION,
                rpId = RpId("https://example.com"),
                credentialId = CredentialId.fromByteArray("test-id".encodeToByteArray()),
                timestamp = Clock.System.now(),
                biometricUsed = true,
                pinUsed = false,
                ipAddress = "127.0.0.1",
                userAgent = "Mozilla/5.0",
                deviceId = "device-123",
            )

        val encoded = json.encodeToString(record)
        val decoded = json.decodeFromString<UserConsentRecord>(encoded)

        assertEquals(record.id, decoded.id)
        assertEquals(record.operationType, decoded.operationType)
        assertEquals(record.rpId, decoded.rpId)
        assertEquals(record.credentialId, decoded.credentialId)
        assertEquals(record.timestamp, decoded.timestamp)
        assertEquals(record.biometricUsed, decoded.biometricUsed)
        assertEquals(record.pinUsed, decoded.pinUsed)
        assertEquals(record.ipAddress, decoded.ipAddress)
        assertEquals(record.userAgent, decoded.userAgent)
        assertEquals(record.deviceId, decoded.deviceId)
    }
}
