package com.chimali.fido2.data.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.chimali.core.common.annotation.Req
import com.chimali.core.common.result.Outcome
import com.chimali.core.domain.model.ConsentOperationType
import com.chimali.core.domain.model.CredentialKeyDescriptor
import com.chimali.core.domain.model.RelyingParty
import com.chimali.core.domain.model.UserConsentRecord
import com.chimali.core.domain.time.TimeProvider
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.RpId
import com.chimali.core.domain.valueobject.UserId
import com.chimali.fido2.data.crypto.PublicKeyDecoder
import com.chimali.fido2.data.dao.PasskeyCredentialDao
import com.chimali.fido2.data.dao.RelyingPartyDao
import com.chimali.fido2.data.dao.UserConsentRecordDao
import com.chimali.fido2.data.mapper.toDomainModel
import com.chimali.fido2.data.service.CredentialMetadataProtectionService
import com.chimali.fido2.domain.model.PasskeyCredential
import io.mockk.every
import io.mockk.mockk
import java.security.KeyPairGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** Exercises the generated SQLDelight schema and the production DAO bindings against real SQLite. */
@Req("NFR-SEC-041", "TR-AUTH-031")
class CredentialAuditSchemaTest {
    @Test
    fun `fresh credential RP and consent rows have audit times and advance them on updates`() =
        runTest {
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            Fido2Database.Schema.create(driver)
            val database = Fido2Database(driver)
            assertEquals(
                listOf(
                    "id",
                    "created_at",
                    "last_used_at",
                    "updated_at",
                    "aaguid",
                    "cose_algorithm",
                    "credential_id",
                    "cred_protect_policy",
                    "derivation_profile",
                    "encrypted_metadata",
                    "label",
                    "public_key",
                    "rp_id",
                    "rp_id_index",
                    "rp_name",
                    "sign_count",
                    "user_display_name",
                    "user_id",
                    "user_id_index",
                    "user_name",
                ),
                tableColumns(driver, "passkey_credential"),
            )
            assertEquals(
                listOf(
                    "id",
                    "created_at",
                    "last_used_at",
                    "updated_at",
                    "credential_count",
                    "encrypted_metadata",
                    "icon_url",
                    "is_blocked",
                    "name",
                ),
                tableColumns(driver, "relying_party"),
            )
            assertEquals(
                listOf(
                    "id",
                    "created_at",
                    "timestamp",
                    "updated_at",
                    "biometric_used",
                    "credential_id",
                    "device_id",
                    "ip_address",
                    "operation_type",
                    "pin_used",
                    "rp_id",
                    "rp_id_index",
                    "user_agent",
                ),
                tableColumns(driver, "user_consent_record"),
            )
            var currentTime = TimeProvider().now()
            val timeProvider = mockk<TimeProvider>()
            every { timeProvider.now() } answers { currentTime }
            val metadata = mockk<CredentialMetadataProtectionService>()
            every { metadata.getRpIdIndex(any()) } answers { "rp-index".encodeToByteArray() }
            every { metadata.getUserIdIndex(any()) } answers { "user-index".encodeToByteArray() }
            every { metadata.encryptCredentialMetadata(any(), any()) } returns byteArrayOf(1, 2)
            every { metadata.encryptRelyingPartyMetadata(any(), any()) } returns byteArrayOf(3, 4)
            every { metadata.decryptCredentialMetadata(any(), any()) } throws SecurityException("synthetic unavailable")

            val now = currentTime
            val rp =
                RelyingParty(
                    RpId(RP),
                    "Audit RP",
                    null,
                    0,
                    now.minus(kotlin.time.Duration.parse("2s")),
                    now.plus(kotlin.time.Duration.parse("5s")),
                )
            val consent =
                UserConsentRecord(
                    "consent-audit",
                    ConsentOperationType.AUTHENTICATION,
                    RpId(RP),
                    null,
                    now,
                    false,
                    false,
                    null,
                    null,
                    null,
                )
            val credentialId =
                CredentialId.fromByteArray(
                    ByteArray(CredentialId.MIN_CREDENTIAL_ID_BYTES) { it.toByte() },
                )
            val publicKey =
                KeyPairGenerator
                    .getInstance("EC")
                    .apply { initialize(256) }
                    .generateKeyPair()
                    .public
            val originalCredential =
                PasskeyCredential.create(
                    id = credentialId,
                    rpId = RpId(RP),
                    userId = UserId("audit-user"),
                    userName = "audit-user",
                    userDisplayName = "Audit User",
                    publicKey = publicKey,
                    aaguid = ByteArray(PasskeyCredential.AAGUID_LENGTH),
                    credentialId = credentialId.toByteArray(),
                    derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
                )
            val credential =
                originalCredential.copy(
                    createdAt = now.minus(kotlin.time.Duration.parse("2s")),
                    lastUsedAt = now.plus(kotlin.time.Duration.parse("5s")),
                )

            try {
                RelyingPartyDao(database, timeProvider, metadata).insertOrUpdateRelyingParty(rp)
                UserConsentRecordDao(database, metadata).insertConsent(consent)
                val passkeyDao = PasskeyCredentialDao(database, timeProvider, metadata)
                passkeyDao.insertCredential(credential)

                val initialRp =
                    requireNotNull(
                        database.relyingPartyQueries.select_by_id(RP.encodeToByteArray()).executeAsOneOrNull(),
                    )
                val initialConsent = database.userConsentRecordQueries.select_by_id(consent.id).executeAsOne()
                val initialCredential =
                    database.passkeyCredentialQueries
                        .select_by_id(
                            credentialId.encoded,
                        ).executeAsOne()
                assertTrue(initialRp.updated_at >= requireNotNull(initialRp.last_used_at))
                assertEquals(now.toEpochMilliseconds(), initialConsent.created_at)
                assertEquals(initialConsent.timestamp, initialConsent.created_at)
                assertEquals(initialConsent.timestamp, initialConsent.updated_at)
                assertTrue(initialCredential.updated_at >= requireNotNull(initialCredential.last_used_at))

                val insertedCredential =
                    assertIs<Outcome.Success<*>>(
                        initialCredential.copy().toDomainModel(PublicKeyDecoder(), metadata),
                    ).data as PasskeyCredential
                assertEquals(initialCredential.created_at, insertedCredential.createdAt.toEpochMilliseconds())
                assertEquals(initialCredential.last_used_at, insertedCredential.lastUsedAt.toEpochMilliseconds())
                insertedCredential.eraseSensitiveText()
                val insertedRp = initialRp.copy().toDomainModel()
                assertEquals(initialRp.created_at, insertedRp.createdAt.toEpochMilliseconds())
                assertEquals(initialRp.last_used_at, insertedRp.lastUsedAt?.toEpochMilliseconds())
                insertedRp.eraseSensitiveText()

                currentTime = now.plus(kotlin.time.Duration.parse("10s"))
                val updatedCredentialModel = credential.copy(lastUsedAt = now.plus(kotlin.time.Duration.parse("9s")))
                passkeyDao.updateCredential(updatedCredentialModel)
                val usedCredentialRow =
                    database.passkeyCredentialQueries.select_by_id(credentialId.encoded).executeAsOne()
                assertTrue(usedCredentialRow.updated_at >= requireNotNull(usedCredentialRow.last_used_at))
                val mappedUpdatedCredential =
                    assertIs<Outcome.Success<*>>(
                        usedCredentialRow.copy().toDomainModel(PublicKeyDecoder(), metadata),
                    ).data as PasskeyCredential
                assertEquals(usedCredentialRow.last_used_at, mappedUpdatedCredential.lastUsedAt.toEpochMilliseconds())
                mappedUpdatedCredential.eraseSensitiveText()
                updatedCredentialModel.eraseSensitiveText()

                val updatedRpModel = rp.copy(lastUsedAt = now.plus(kotlin.time.Duration.parse("12s")))
                RelyingPartyDao(database, timeProvider, metadata).updateRelyingParty(updatedRpModel)
                val usedRpRow = database.relyingPartyQueries.select_by_id(RP.encodeToByteArray()).executeAsOne()
                assertTrue(usedRpRow.updated_at >= requireNotNull(usedRpRow.last_used_at))
                val rpAfterTimestampUpdate = usedRpRow.copy().toDomainModel()
                assertEquals(usedRpRow.last_used_at, rpAfterTimestampUpdate.lastUsedAt?.toEpochMilliseconds())
                rpAfterTimestampUpdate.eraseSensitiveText()
                updatedRpModel.eraseSensitiveText()

                val rpDao = RelyingPartyDao(database, timeProvider, metadata)
                val historicalCreation = now.minus(kotlin.time.Duration.parse("1d"))
                val omittedActivity =
                    rp.copy(
                        createdAt = historicalCreation,
                        lastUsedAt = null,
                    )
                rpDao.insertOrUpdateRelyingParty(omittedActivity)
                val afterOmittedActivity =
                    database.relyingPartyQueries
                        .select_by_id(RP.encodeToByteArray())
                        .executeAsOne()
                assertEquals(usedRpRow.created_at, afterOmittedActivity.created_at)
                assertEquals(usedRpRow.last_used_at, afterOmittedActivity.last_used_at)
                assertTrue(afterOmittedActivity.updated_at > usedRpRow.updated_at)
                val omittedActivityModel = afterOmittedActivity.copy().toDomainModel()
                assertEquals(
                    afterOmittedActivity.created_at,
                    omittedActivityModel.createdAt.toEpochMilliseconds(),
                )
                assertEquals(
                    afterOmittedActivity.last_used_at,
                    omittedActivityModel.lastUsedAt?.toEpochMilliseconds(),
                )
                omittedActivityModel.eraseSensitiveText()
                omittedActivity.eraseSensitiveText()

                val newerActivity =
                    rp.copy(
                        createdAt = historicalCreation,
                        lastUsedAt = now.plus(kotlin.time.Duration.parse("20s")),
                    )
                rpDao.insertOrUpdateRelyingParty(newerActivity)
                val afterNewerActivity =
                    database.relyingPartyQueries
                        .select_by_id(RP.encodeToByteArray())
                        .executeAsOne()
                assertEquals(usedRpRow.created_at, afterNewerActivity.created_at)
                assertEquals(newerActivity.lastUsedAt?.toEpochMilliseconds(), afterNewerActivity.last_used_at)
                assertTrue(afterNewerActivity.updated_at > afterOmittedActivity.updated_at)
                val newerActivityModel = afterNewerActivity.copy().toDomainModel()
                assertEquals(
                    afterNewerActivity.last_used_at,
                    newerActivityModel.lastUsedAt?.toEpochMilliseconds(),
                )
                newerActivityModel.eraseSensitiveText()
                newerActivity.eraseSensitiveText()

                val olderValidActivity =
                    rp.copy(
                        createdAt = historicalCreation,
                        lastUsedAt = now.plus(kotlin.time.Duration.parse("10s")),
                    )
                rpDao.insertOrUpdateRelyingParty(olderValidActivity)
                val afterOlderValidActivity =
                    database.relyingPartyQueries
                        .select_by_id(RP.encodeToByteArray())
                        .executeAsOne()
                assertEquals(afterNewerActivity.last_used_at, afterOlderValidActivity.last_used_at)
                assertTrue(afterOlderValidActivity.updated_at > afterNewerActivity.updated_at)
                val preservedName = afterOlderValidActivity.name.copyOf()
                val preservedMetadata = afterOlderValidActivity.encrypted_metadata?.copyOf()
                val olderValidActivityModel = afterOlderValidActivity.copy().toDomainModel()
                assertEquals(
                    afterOlderValidActivity.last_used_at,
                    olderValidActivityModel.lastUsedAt?.toEpochMilliseconds(),
                )
                olderValidActivityModel.eraseSensitiveText()
                olderValidActivity.eraseSensitiveText()

                val rejectedActivity =
                    rp.copy(
                        createdAt = historicalCreation,
                        lastUsedAt = now.minus(kotlin.time.Duration.parse("3s")),
                    )
                assertFailsWith<IllegalArgumentException> {
                    rpDao.insertOrUpdateRelyingParty(rejectedActivity)
                }
                val afterRejectedActivity =
                    database.relyingPartyQueries
                        .select_by_id(RP.encodeToByteArray())
                        .executeAsOne()
                assertEquals(afterOlderValidActivity.created_at, afterRejectedActivity.created_at)
                assertEquals(afterOlderValidActivity.last_used_at, afterRejectedActivity.last_used_at)
                assertEquals(afterOlderValidActivity.updated_at, afterRejectedActivity.updated_at)
                assertEquals(afterOlderValidActivity.credential_count, afterRejectedActivity.credential_count)
                assertEquals(afterOlderValidActivity.icon_url, afterRejectedActivity.icon_url)
                assertEquals(afterOlderValidActivity.is_blocked, afterRejectedActivity.is_blocked)
                assertTrue(preservedName.contentEquals(afterRejectedActivity.name))
                assertTrue(
                    preservedMetadata?.contentEquals(afterRejectedActivity.encrypted_metadata) == true,
                )
                preservedName.fill(0)
                preservedMetadata?.fill(0)
                afterRejectedActivity.copy().toDomainModel().eraseSensitiveText()
                rejectedActivity.eraseSensitiveText()

                currentTime = now.minus(kotlin.time.Duration.parse("30s"))
                val regressedClockActivity =
                    rp.copy(
                        createdAt = historicalCreation,
                        lastUsedAt = null,
                    )
                rpDao.insertOrUpdateRelyingParty(regressedClockActivity)
                val afterClockRegression =
                    database.relyingPartyQueries
                        .select_by_id(RP.encodeToByteArray())
                        .executeAsOne()
                assertEquals(afterOlderValidActivity.last_used_at, afterClockRegression.last_used_at)
                assertTrue(afterClockRegression.updated_at > afterOlderValidActivity.updated_at)
                assertTrue(afterClockRegression.updated_at >= afterClockRegression.created_at)
                val clockRegressionModel = afterClockRegression.copy().toDomainModel()
                assertEquals(
                    afterClockRegression.last_used_at,
                    clockRegressionModel.lastUsedAt?.toEpochMilliseconds(),
                )
                clockRegressionModel.eraseSensitiveText()
                regressedClockActivity.eraseSensitiveText()

                currentTime = now.plus(kotlin.time.Duration.parse("15s"))
                passkeyDao.updateLastUsedAt(credentialId)
                val lastUsedRow = database.passkeyCredentialQueries.select_by_id(credentialId.encoded).executeAsOne()
                assertTrue(lastUsedRow.updated_at >= requireNotNull(lastUsedRow.last_used_at))
                val lastUsedCredential =
                    assertIs<Outcome.Success<*>>(
                        lastUsedRow.copy().toDomainModel(PublicKeyDecoder(), metadata),
                    ).data as PasskeyCredential
                lastUsedCredential.eraseSensitiveText()

                database.relyingPartyQueries.update_blocked_status(is_blocked = 1L, id = RP.encodeToByteArray())
                database.userConsentRecordQueries.update_rp_id_index(rp_id_index = byteArrayOf(8, 9), id = consent.id)
                passkeyDao.updateSignCount(credentialId, 1L)

                val updatedRpRow = database.relyingPartyQueries.select_by_id(RP.encodeToByteArray()).executeAsOne()
                val updatedConsent = database.userConsentRecordQueries.select_by_id(consent.id).executeAsOne()
                val updatedCredential =
                    database.passkeyCredentialQueries
                        .select_by_id(
                            credentialId.encoded,
                        ).executeAsOne()
                assertEquals(initialRp.created_at, updatedRpRow.created_at)
                assertEquals(
                    now.plus(kotlin.time.Duration.parse("20s")).toEpochMilliseconds(),
                    updatedRpRow.last_used_at,
                )
                assertTrue(updatedRpRow.updated_at > initialRp.updated_at)
                assertEquals(initialConsent.timestamp, updatedConsent.timestamp)
                assertTrue(updatedConsent.updated_at > initialConsent.updated_at)
                assertEquals(
                    now.plus(kotlin.time.Duration.parse("15s")).toEpochMilliseconds(),
                    updatedCredential.last_used_at,
                )
                assertEquals(1L, updatedCredential.sign_count)
                assertTrue(updatedCredential.updated_at > initialCredential.updated_at)

                assertFailsWith<IllegalStateException> {
                    database.transaction {
                        database.relyingPartyQueries.update_blocked_status(is_blocked = 0L, id = RP.encodeToByteArray())
                        database.passkeyCredentialQueries.update_sign_count(99L, credentialId.encoded)
                        error("synthetic transaction rollback")
                    }
                }
                val rolledBackRp = database.relyingPartyQueries.select_by_id(RP.encodeToByteArray()).executeAsOne()
                val rolledBackCredential =
                    database.passkeyCredentialQueries
                        .select_by_id(
                            credentialId.encoded,
                        ).executeAsOne()
                assertEquals(updatedRpRow.updated_at, rolledBackRp.updated_at)
                assertEquals(1L, rolledBackCredential.sign_count)
                assertEquals(updatedCredential.updated_at, rolledBackCredential.updated_at)

                val failedReplacement =
                    credential.copy(
                        id = CredentialId.fromByteArray(ByteArray(CredentialId.MIN_CREDENTIAL_ID_BYTES) { 6 }),
                        credentialId = ByteArray(CredentialId.MIN_CREDENTIAL_ID_BYTES) { 6 },
                        lastUsedAt = now.plus(kotlin.time.Duration.parse("20s")),
                    )
                every {
                    metadata.encryptCredentialMetadata(any(), any())
                } throws IllegalStateException("synthetic failure")
                assertFailsWith<IllegalStateException> {
                    passkeyDao.replaceCredentialsForRpAndUser(failedReplacement)
                }
                val retainedAfterRollback =
                    database.passkeyCredentialQueries.select_by_id(credentialId.encoded).executeAsOne()
                assertEquals(updatedCredential.last_used_at, retainedAfterRollback.last_used_at)
                assertEquals(updatedCredential.sign_count, retainedAfterRollback.sign_count)
                assertTrue(retainedAfterRollback.updated_at >= requireNotNull(retainedAfterRollback.last_used_at))
                assertEquals(
                    null,
                    database.passkeyCredentialQueries.select_by_id(failedReplacement.id.encoded).executeAsOneOrNull(),
                )
                every { metadata.encryptCredentialMetadata(any(), any()) } returns byteArrayOf(1, 2)
                failedReplacement.eraseSensitiveText()

                val replacement =
                    credential.copy(
                        id = CredentialId.fromByteArray(ByteArray(CredentialId.MIN_CREDENTIAL_ID_BYTES) { 7 }),
                        credentialId = ByteArray(CredentialId.MIN_CREDENTIAL_ID_BYTES) { 7 },
                        lastUsedAt = now.plus(kotlin.time.Duration.parse("20s")),
                    )
                passkeyDao.replaceCredentialsForRpAndUser(replacement)
                val replacementRow =
                    database.passkeyCredentialQueries.select_by_id(replacement.id.encoded).executeAsOne()
                assertTrue(replacementRow.updated_at >= requireNotNull(replacementRow.last_used_at))
                val replacementCredential =
                    assertIs<Outcome.Success<*>>(
                        replacementRow.toDomainModel(PublicKeyDecoder(), metadata),
                    ).data as PasskeyCredential
                assertEquals(replacement.id.encoded, replacementCredential.id.encoded)
                assertEquals(
                    replacement.lastUsedAt.toEpochMilliseconds(),
                    replacementCredential.lastUsedAt.toEpochMilliseconds(),
                )
                replacementCredential.eraseSensitiveText()
                replacement.eraseSensitiveText()
            } finally {
                credential.eraseSensitiveText()
                originalCredential.eraseSensitiveText()
                consent.eraseSensitiveText()
                rp.eraseSensitiveText()
                driver.close()
            }
        }

    private companion object {
        const val RP = "https://audit.example"
    }

    private fun tableColumns(
        driver: JdbcSqliteDriver,
        table: String,
    ): List<String> =
        requireNotNull(
            driver
                .executeQuery(
                    identifier = null,
                    sql = "PRAGMA table_info($table)",
                    mapper = { cursor ->
                        val names = mutableListOf<String>()
                        while (cursor.next().value) names += requireNotNull(cursor.getString(1))
                        QueryResult.Value(names)
                    },
                    parameters = 0,
                ).value,
        )
}
