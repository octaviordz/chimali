package com.chimali.fido2.data.dao

import app.cash.sqldelight.Query
import app.cash.sqldelight.TransactionWithoutReturn
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.chimali.core.domain.model.ConsentOperationType
import com.chimali.core.domain.model.RelyingParty
import com.chimali.core.domain.model.SensitiveText
import com.chimali.core.domain.model.UserConsentRecord
import com.chimali.core.domain.time.TimeProvider
import com.chimali.fido2.data.database.Fido2Database
import com.chimali.fido2.data.database.RelyingPartyQueries
import com.chimali.fido2.data.database.UserConsentRecordQueries
import com.chimali.fido2.data.mapper.toDomainModel
import com.chimali.fido2.data.mapper.toDomainModelWithTextDecoder
import com.chimali.fido2.data.service.CredentialMetadataProtectionService
import com.chimali.fido2.domain.model.decodeSensitiveText
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest

class RelyingPartyConsentOwnershipTest {
    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `actual RP and consent BLOB round trips preserve Unicode and erase consumed row buffers`() =
        runTest {
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            Fido2Database.Schema.create(driver)
            val database = Fido2Database(driver)
            val service = mockk<CredentialMetadataProtectionService>()
            every { service.encryptRelyingPartyMetadata(any(), any()) } returns byteArrayOf(7)
            every { service.getRpIdIndex(match { it.contentEquals(RP) }) } returns byteArrayOf(3, 4)
            val rp = RelyingParty.fromOwnedText(SensitiveText.copyOf(RP), SensitiveText.copyOf("München Web"))
            val consent = UserConsentRecord.fromText("consent", ConsentOperationType.AUTHENTICATION, rp.idText)
            try {
                val dao = RelyingPartyDao(database, TimeProvider(), service)
                dao.insertOrUpdateRelyingParty(rp)
                val row = requireNotNull(dao.getRelyingPartyByText(rp.idText))
                val mapped = row.toDomainModel()
                try {
                    assertTrue(row.id.all { it == 0.toByte() })
                    assertTrue(row.name.all { it == 0.toByte() })
                    assertTrue(mapped.idText.contentEquals(RP))
                    assertTrue(mapped.nameText.contentEquals("München Web"))
                    UserConsentRecordDao(database, service).insertConsent(consent)
                    val consentRow = database.userConsentRecordQueries.select_by_id("consent").executeAsOne()
                    val hydratedConsent = consentRow.toDomainModel()
                    try {
                        assertTrue(consentRow.rp_id.all { it == 0.toByte() })
                        assertTrue(hydratedConsent.rpIdText.contentEquals(RP))
                        assertEquals(ConsentOperationType.AUTHENTICATION, hydratedConsent.operationType)
                    } finally {
                        hydratedConsent.eraseSensitiveText()
                    }
                } finally {
                    mapped.eraseSensitiveText()
                }
                assertTrue(rp.idText.contentEquals(RP))
                assertTrue(consent.rpIdText.contentEquals(RP))
            } finally {
                consent.eraseSensitiveText()
                rp.eraseSensitiveText()
                driver.close()
            }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `RP binding buffers erase after SQL failure while the borrowed model stays live`() =
        runTest {
            val database = mockk<Fido2Database>()
            val queries = mockk<RelyingPartyQueries>()
            val creationQuery = mockk<Query<Long>>()
            val service = mockk<CredentialMetadataProtectionService>()
            val retained = mutableListOf<ByteArray>()
            every { database.relyingPartyQueries } returns queries
            every { database.transaction(any(), any<TransactionWithoutReturn.() -> Unit>()) } answers {
                secondArg<TransactionWithoutReturn.() -> Unit>().invoke(mockk(relaxed = true))
            }
            every { queries.select_created_at(any()) } returns creationQuery
            every { creationQuery.executeAsOneOrNull() } returns null
            every { service.encryptRelyingPartyMetadata(any(), any()) } returns byteArrayOf(7)
            every { queries.insert(any(), any(), any(), any(), any(), any(), any(), any(), any()) } answers {
                retained += firstArg<ByteArray>()
                retained += arg<ByteArray>(8)
                throw android.database.SQLException("synthetic failure")
            }
            val model = RelyingParty.fromOwnedText(SensitiveText.copyOf(RP), SensitiveText.copyOf("Private Name"))
            try {
                assertFailsWith<android.database.SQLException> {
                    RelyingPartyDao(database, TimeProvider(), service).insertOrUpdateRelyingParty(model)
                }
                assertEquals(2, retained.size)
                retained.forEach { assertTrue(it.all { byte -> byte == 0.toByte() }) }
                assertTrue(model.idText.contentEquals(RP))
                assertTrue(model.nameText.contentEquals("Private Name"))
            } finally {
                model.eraseSensitiveText()
            }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `consent token and byte bindings erase when its SQL call is cancelled`() {
        val database = mockk<Fido2Database>()
        val queries = mockk<UserConsentRecordQueries>()
        val service = mockk<CredentialMetadataProtectionService>()
        val token = byteArrayOf(3, 4)
        val bytes = mutableListOf<ByteArray>()
        every { database.userConsentRecordQueries } returns queries
        every { service.getRpIdIndex(match { it.contentEquals(RP) }) } returns token
        every {
            queries.insert(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } answers {
            bytes += arg<ByteArray>(10)
            throw CancellationException("synthetic cancellation")
        }
        val source = SensitiveText.copyOf(RP)
        val consent = UserConsentRecord.fromText("consent", ConsentOperationType.REGISTRATION, source)
        try {
            assertFailsWith<CancellationException> { UserConsentRecordDao(database, service).insertConsent(consent) }
            assertEquals(1, bytes.size)
            assertTrue(bytes.single().all { it == 0.toByte() })
            assertTrue(token.all { it == 0.toByte() })
            assertTrue(consent.rpIdText.contentEquals(RP))
            assertTrue(source.contentEquals(RP))
        } finally {
            consent.eraseSensitiveText()
            source.erase()
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `malformed second RP field erases preceding owner and both row buffers with a fixed error`() {
        val row =
            com.chimali.fido2.data.database.Relying_party(
                id = RP.encodeToByteArray(),
                created_at = 1,
                last_used_at = null,
                updated_at = 1,
                credential_count = 0,
                icon_url = null,
                is_blocked = 0,
                name = byteArrayOf(0xC3.toByte(), 0x28),
                encrypted_metadata = null,
            )
        val owners = mutableListOf<SensitiveText>()
        val error =
            assertFailsWith<IllegalArgumentException> {
                row.toDomainModelWithTextDecoder { bytes -> decodeSensitiveText(bytes).also(owners::add) }
            }
        assertEquals("Malformed relying-party record", error.message)
        assertEquals(null, error.cause)
        assertEquals(1, owners.size)
        assertTrue(owners.single().all { it == '\u0000' })
        assertTrue(row.id.all { it == 0.toByte() })
        assertTrue(row.name.all { it == 0.toByte() })
    }

    private companion object {
        const val RP = "https://private.example"
    }
}
