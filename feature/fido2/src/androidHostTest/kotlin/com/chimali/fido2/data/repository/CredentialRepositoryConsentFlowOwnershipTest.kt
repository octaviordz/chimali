package com.chimali.fido2.data.repository

import com.chimali.core.common.annotation.Req
import com.chimali.core.domain.eventsourcing.AggregateService
import com.chimali.core.domain.eventsourcing.passkey.PasskeyCommand
import com.chimali.core.domain.eventsourcing.passkey.PasskeyState
import com.chimali.core.domain.model.UserConsentRecord
import com.chimali.core.domain.time.TimeProvider
import com.chimali.fido2.data.crypto.PublicKeyDecoder
import com.chimali.fido2.data.dao.PasskeyCredentialDao
import com.chimali.fido2.data.dao.RelyingPartyDao
import com.chimali.fido2.data.dao.UserConsentRecordDao
import com.chimali.fido2.data.database.User_consent_record
import com.chimali.fido2.data.service.CredentialMetadataProtectionService
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

@Req("NFR-SEC-041", "TR-AUTH-031")
class CredentialRepositoryConsentFlowOwnershipTest {
    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `t157 successful consent emission transfers a live owner and erases the row`() =
        runTest {
            val row = consentRow()
            val repository = repository(row)
            var retained: UserConsentRecord? = null

            repository.getRecentUserConsent(null, 1).collect { consent ->
                retained = consent
                assertOwned(consent)
            }

            assertTrue(row.rp_id.all { it == 0.toByte() })
            val received = requireNotNull(retained)
            assertOwned(received)
            received.eraseSensitiveText()
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `t157 failed consent emission erases retained owner and source row`() =
        runTest {
            val row = consentRow()
            val repository = repository(row)
            var retained: UserConsentRecord? = null

            assertFailsWith<IllegalStateException> {
                repository.getRecentUserConsent(null, 1).collect { consent ->
                    retained = consent
                    assertOwned(consent)
                    error("synthetic downstream failure")
                }
            }

            assertErased(requireNotNull(retained))
            assertContentEquals(ByteArray(row.rp_id.size), row.rp_id)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `t157 cancelled consent emission erases retained owner and source row`() =
        runTest {
            val row = consentRow()
            val repository = repository(row)
            var retained: UserConsentRecord? = null

            assertFailsWith<CancellationException> {
                repository.getRecentUserConsent(null, 1).collect { consent ->
                    retained = consent
                    assertOwned(consent)
                    throw CancellationException("synthetic downstream cancellation")
                }
            }

            assertErased(requireNotNull(retained))
            assertContentEquals(ByteArray(row.rp_id.size), row.rp_id)
        }

    private fun repository(row: User_consent_record): CredentialRepositoryImpl {
        val consentDao = mockk<UserConsentRecordDao>()
        every { consentDao.getRecentConsent(any(), any()) } returns flowOf(listOf(row))
        return CredentialRepositoryImpl(
            passkeyCredentialDao = mockk<PasskeyCredentialDao>(relaxed = true),
            relyingPartyDao = mockk<RelyingPartyDao>(relaxed = true),
            userConsentRecordDao = consentDao,
            publicKeyDecoder = mockk<PublicKeyDecoder>(relaxed = true),
            timeProvider = mockk<TimeProvider>(relaxed = true),
            aggregateService = mockk<AggregateService<PasskeyCommand, PasskeyState>>(relaxed = true),
            metadataProtectionService = mockk<CredentialMetadataProtectionService>(relaxed = true),
        )
    }

    private fun consentRow() =
        User_consent_record(
            id = "synthetic-consent",
            created_at = 1_000,
            timestamp = 1_000,
            updated_at = 1_000,
            biometric_used = 0,
            credential_id = null,
            device_id = null,
            ip_address = null,
            operation_type = "AUTHENTICATION",
            pin_used = 0,
            rp_id = "https://synthetic.example".encodeToByteArray(),
            rp_id_index = null,
            user_agent = null,
        )

    private fun assertOwned(consent: UserConsentRecord) {
        val chars = consent.rpIdText.copyCharacters()
        try {
            assertEquals("https://synthetic.example", chars.concatToString())
        } finally {
            chars.fill('\u0000')
        }
    }

    private fun assertErased(consent: UserConsentRecord) {
        val chars = consent.rpIdText.copyCharacters()
        try {
            assertTrue(chars.all { it == '\u0000' })
        } finally {
            chars.fill('\u0000')
        }
    }
}
