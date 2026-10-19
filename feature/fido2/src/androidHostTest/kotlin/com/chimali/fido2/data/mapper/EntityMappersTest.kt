package com.chimali.fido2.data.mapper

import com.chimali.core.common.annotation.Req
import com.chimali.core.common.result.Outcome
import com.chimali.core.domain.model.CredentialKeyDescriptor
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.fido2.data.crypto.PublicKeyDecoder
import com.chimali.fido2.data.database.Passkey_credential
import com.chimali.fido2.data.service.CredentialMetadataProtectionService
import com.chimali.fido2.domain.model.SensitiveCredentialText
import io.mockk.every
import io.mockk.mockk
import java.security.KeyPairGenerator
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.CancellationException
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/** Typed reconstruction boundaries for current stored credentials. @see NFR-SEC-041 and TR-AUTH-031 */
class EntityMappersTest {
    private val metadata = mockk<CredentialMetadataProtectionService>()
    private val decoder = PublicKeyDecoder()
    private val publicKey =
        KeyPairGenerator
            .getInstance("EC")
            .apply { initialize(256) }
            .generateKeyPair()
            .public

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @ParameterizedTest
    @ValueSource(
        strings = [
            "invalid-id", "short-id", "invalid-credential-id", "different-credential-id", "invalid-aaguid",
            "first-byte-credential-mismatch", "last-byte-credential-mismatch", "different-length-credential-id",
            "short-aaguid", "invalid-profile", "algorithm-overflow", "unsupported-algorithm", "negative-counter",
            "blank-name", "invalid-rp", "future-timestamp", "updated-before-created", "updated-before-last-used",
            "invalid-public-key",
        ],
    )
    fun `malformed stored fields return fixed typed errors without exception payloads`(field: String) {
        val valid = row()
        val invalid =
            when (field) {
                "invalid-id" -> valid.copy(id = "synthetic-private-marker%")
                "short-id" -> valid.copy(id = "AQ")
                "invalid-credential-id" -> valid.copy(credential_id = "synthetic-private-marker%")
                "different-credential-id" ->
                    valid.copy(
                        credential_id = CredentialId.fromByteArray(ByteArray(32) { 2 }).encoded,
                    )
                "first-byte-credential-mismatch" ->
                    valid.copy(
                        credential_id =
                            CredentialId.fromByteArray(ByteArray(32) { if (it == 0) 2 else 1 }).encoded,
                    )
                "last-byte-credential-mismatch" ->
                    valid.copy(
                        credential_id =
                            CredentialId.fromByteArray(ByteArray(32) { if (it == 31) 2 else 1 }).encoded,
                    )
                "different-length-credential-id" ->
                    valid.copy(credential_id = CredentialId.fromByteArray(ByteArray(33) { 1 }).encoded)
                "invalid-aaguid" -> valid.copy(aaguid = "synthetic-private-marker%")
                "short-aaguid" -> valid.copy(aaguid = Base64.getEncoder().encodeToString(ByteArray(15)))
                "invalid-profile" -> valid.copy(derivation_profile = "synthetic-private-marker")
                "algorithm-overflow" -> valid.copy(cose_algorithm = 4_294_967_289L)
                "unsupported-algorithm" -> valid.copy(cose_algorithm = -257L)
                "negative-counter" -> valid.copy(sign_count = -1L)
                "blank-name" -> valid.copy(user_name = "".encodeToByteArray())
                "invalid-rp" -> valid.copy(rp_id = "synthetic-private-marker invalid rp".encodeToByteArray())
                "future-timestamp" -> valid.copy(created_at = Long.MAX_VALUE)
                "updated-before-created" -> valid.copy(created_at = 3000L, updated_at = 2000L)
                "updated-before-last-used" -> valid.copy(last_used_at = 3000L, updated_at = 2000L)
                "invalid-public-key" -> valid.copy(public_key = "synthetic-private-marker%")
                else -> error("Unknown fixture")
            }

        val rowBuffers =
            listOf(
                invalid.rp_id,
                invalid.rp_name,
                invalid.user_id,
                invalid.user_name,
                invalid.user_display_name,
            )
        val failure = assertIs<Outcome.Error<*>>(invalid.toDomainModel(decoder, metadata)).error
        assertEquals("Malformed credential record", failure.message)
        assertNull(failure.cause)
        rowBuffers.forEach { assertContentEquals(ByteArray(it.size), it) }
    }

    /** @see FR-003, FR-016 and TR-AUTH-031 */
    @Req("FR-003", "FR-016", "NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `audit timestamps accept equal and advanced update times and clear row owners`() {
        listOf(2000L, 3000L).forEach { updatedAt ->
            val stored = row().copy(updated_at = updatedAt)
            val rowOwners =
                listOf(
                    stored.rp_id,
                    stored.rp_name,
                    stored.user_id,
                    stored.user_name,
                    stored.user_display_name,
                )

            assertIs<Outcome.Success<*>>(stored.toDomainModel(decoder, metadata))

            rowOwners.forEach { bytes -> assertContentEquals(ByteArray(bytes.size), bytes) }
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `invalid persisted credential-protection policies fail closed and clear row owners`() {
        listOf(Long.MIN_VALUE, -1L, 0L, 4L, 4_294_967_297L, Long.MAX_VALUE).forEach { rawPolicy ->
            val invalid = row().copy(cred_protect_policy = rawPolicy)
            val ownedRows =
                listOf(invalid.rp_id, invalid.rp_name, invalid.user_id, invalid.user_name, invalid.user_display_name)

            val failure = assertIs<Outcome.Error<*>>(invalid.toDomainModel(decoder, metadata)).error

            assertEquals("Malformed credential record", failure.message)
            assertNull(failure.cause)
            ownedRows.forEach { bytes -> assertContentEquals(ByteArray(bytes.size), bytes) }
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @ParameterizedTest
    @ValueSource(strings = ["rp_id", "user_id", "user_name", "user_display_name"])
    fun `malformed UTF-8 in every metadata column returns a redacted error and clears row bytes`(field: String) {
        val valid = row()
        val malformed = byteArrayOf(0xC3.toByte(), 0x28)
        val invalid =
            when (field) {
                "rp_id" -> valid.copy(rp_id = malformed)
                "user_id" -> valid.copy(user_id = malformed)
                "user_name" -> valid.copy(user_name = malformed)
                "user_display_name" -> valid.copy(user_display_name = malformed)
                else -> error("Unknown fixture")
            }
        val sensitiveRows =
            listOf(
                invalid.rp_id,
                invalid.rp_name,
                invalid.user_id,
                invalid.user_name,
                invalid.user_display_name,
            )
        val decodedOwners = mutableListOf<SensitiveCredentialText>()

        val failure =
            assertIs<Outcome.Error<*>>(
                invalid.toDomainModelWithTextDecoder(decoder, metadata) { bytes ->
                    SensitiveCredentialText.fromUtf8Bytes(bytes).also(decodedOwners::add)
                },
            ).error

        assertEquals("Malformed credential record", failure.message)
        assertNull(failure.cause)
        sensitiveRows.forEach { assertContentEquals(ByteArray(it.size), it) }
        val expectedOwnerCount =
            when (field) {
                "rp_id" -> 0
                "user_id" -> 1
                "user_name" -> 2
                "user_display_name" -> 3
                else -> error("Unknown fixture")
            }
        assertEquals(expectedOwnerCount, decodedOwners.size)
        decodedOwners.forEach { owner ->
            val retained = owner.copyCharacters()
            assertContentEquals(CharArray(retained.size), retained)
            retained.fill('\u0000')
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `valid record preserves identity profile registered key and counter`() {
        val stored = row()
        val rpIdBytes = stored.rp_id
        val rpNameBytes = stored.rp_name
        val userIdBytes = stored.user_id
        val userNameBytes = stored.user_name
        val displayNameBytes = stored.user_display_name
        val restored = assertIs<Outcome.Success<*>>(stored.toDomainModel(decoder, metadata)).data
        val credential = assertIs<com.chimali.fido2.domain.model.PasskeyCredential>(restored)
        assertEquals(stored.id, credential.id.encoded)
        assertContentEquals(credential.id.toByteArray(), credential.credentialId)
        assertEquals(CredentialKeyDescriptor.P256_PROFILE, credential.derivationProfile)
        assertContentEquals(publicKey.encoded, credential.publicKey.encoded)
        assertEquals(7L, credential.signCount)
        listOf(rpIdBytes, rpNameBytes, userIdBytes, userNameBytes, displayNameBytes).forEach { bytes ->
            assertContentEquals(ByteArray(bytes.size), bytes)
        }
        assertEquals("example.com", credential.rpId.toBoundaryString())
        assertEquals("synthetic-user", credential.userId.toBoundaryString())
        assertEquals("synthetic-account", credential.userName.toBoundaryString())
        assertEquals("Synthetic account", credential.userDisplayName.toBoundaryString())
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `metadata cancellation propagates unchanged`() {
        val cancellation = CancellationException("synthetic cancellation")
        every { metadata.decryptCredentialMetadata(any(), any()) } throws cancellation
        assertEquals(
            cancellation,
            assertFailsWith<CancellationException> {
                row().copy(encrypted_metadata = byteArrayOf(1)).toDomainModel(decoder, metadata)
            },
        )
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `parsed metadata owners erase on both valid and inconsistent hydration`(matching: Boolean) {
        val fields =
            CredentialMetadataProtectionService.CredentialMetadata(
                (if (matching) "example.com" else "different.example.com").toCharArray(),
                "synthetic-user".toCharArray(),
            )
        every { metadata.decryptCredentialMetadata(any(), any()) } returns fields

        val result = row().copy(encrypted_metadata = byteArrayOf(1)).toDomainModel(decoder, metadata)

        if (matching) {
            assertIs<Outcome.Success<*>>(result)
        } else {
            assertEquals("Malformed credential record", assertIs<Outcome.Error<*>>(result).error.message)
        }
        assertContentEquals(CharArray(fields.rpId.size), fields.rpId)
        assertContentEquals(CharArray(fields.userId.size), fields.userId)
    }

    private fun row(): Passkey_credential {
        val id = CredentialId.fromByteArray(ByteArray(32) { 1 }).encoded
        return Passkey_credential(
            id = id,
            created_at = 1000L,
            last_used_at = 2000L,
            updated_at = 2000L,
            aaguid = Base64.getEncoder().encodeToString(ByteArray(16)),
            cose_algorithm = -7L,
            credential_id = id,
            cred_protect_policy = 1L,
            derivation_profile = CredentialKeyDescriptor.P256_PROFILE,
            label = null,
            public_key = Base64.getEncoder().encodeToString(publicKey.encoded),
            rp_id = "example.com".encodeToByteArray(),
            rp_name = "Synthetic RP".encodeToByteArray(),
            sign_count = 7L,
            user_display_name = "Synthetic account".encodeToByteArray(),
            user_id = "synthetic-user".encodeToByteArray(),
            user_name = "synthetic-account".encodeToByteArray(),
            rp_id_index = null,
            user_id_index = null,
            encrypted_metadata = null,
        )
    }
}
