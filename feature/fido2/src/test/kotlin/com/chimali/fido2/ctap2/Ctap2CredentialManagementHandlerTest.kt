package com.chimali.fido2.ctap2

import com.chimali.fido2.data.crypto.CborCodec
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.model.RelyingParty
import com.chimali.fido2.domain.repository.CredentialRepository
import com.chimali.fido2.domain.usecase.DeleteCredentialUseCase
import com.chimali.fido2.domain.usecase.GetAllCredentialsUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class Ctap2CredentialManagementHandlerTest {
    private lateinit var cborCodec: CborCodec
    private lateinit var getAllCredentialsUseCase: GetAllCredentialsUseCase
    private lateinit var deleteCredentialUseCase: DeleteCredentialUseCase
    private lateinit var credentialRepository: CredentialRepository
    private lateinit var handler: Ctap2CredentialManagementHandler

    @BeforeTest
    fun setup() {
        cborCodec = mockk()
        getAllCredentialsUseCase = mockk()
        deleteCredentialUseCase = mockk()
        credentialRepository = mockk()

        handler =
            Ctap2CredentialManagementHandler(
                cborCodec = cborCodec,
                getAllCredentialsUseCase = getAllCredentialsUseCase,
                deleteCredentialUseCase = deleteCredentialUseCase,
                credentialRepository = credentialRepository,
            )
    }

    private companion object {
        private const val SUB_GET_METADATA = 1
        private const val SUB_ENUM_RP_BEGIN = 2
        private const val SUB_ENUM_RP_NEXT = 3
        private const val SUB_ENUM_CRED_BEGIN = 4
        private const val SUB_ENUM_CRED_NEXT = 5

        private const val CTAP1_ERR_MISSING_PARAMETER = 0x0E.toByte()
        private const val CTAP2_ERR_UNSUPPORTED_OPTION = 0x11.toByte()
        private const val CTAP2_ERR_NO_CREDENTIALS = 0x22.toByte()
        private const val CTAP2_ERR_NOT_ALLOWED = 0x30.toByte()
        private const val CTAP2_OK = 0x00.toByte()

        private const val DUMMY_BYTE_01 = 0x01.toByte()
        private const val DUMMY_BYTE_02 = 0x02.toByte()
        private const val DUMMY_BYTE_03 = 0x03.toByte()
        private const val DUMMY_BYTE_AA = 0xAA.toByte()
        private const val DUMMY_BYTE_BB = 0xBB.toByte()
        private const val DUMMY_BYTE_CC = 0xCC.toByte()
        private const val DUMMY_BYTE_DD = 0xDD.toByte()
        private const val UNKNOWN_SUBCOMMAND_99 = 99
        private const val INDEX_2 = 2
    }

    // ── SubCommand parsing ───────────────────────────────────────────────────

    @Test
    fun `handle returns CTAP1_ERR_MISSING_PARAMETER if subCommand is missing`() =
        runTest {
            val requestBytes = byteArrayOf(DUMMY_BYTE_01)
            every { cborCodec.decodeFromFido2Format(any()) } returns emptyMap()

            val response = handler.handle(requestBytes)

            assertEquals(1, response.size)
            assertEquals(CTAP1_ERR_MISSING_PARAMETER, response[0])
        }

    @Test
    fun `handle returns CTAP2_ERR_UNSUPPORTED_OPTION for unknown subCommand`() =
        runTest {
            val requestBytes = byteArrayOf(DUMMY_BYTE_02)
            every { cborCodec.decodeFromFido2Format(any()) } returns mapOf("1" to UNKNOWN_SUBCOMMAND_99)

            val response = handler.handle(requestBytes)

            assertEquals(1, response.size)
            assertEquals(CTAP2_ERR_UNSUPPORTED_OPTION, response[0])
        }

    // ── SubCommand 1: getCredsMetadata ───────────────────────────────────────

    @Test
    fun `handleGetCredsMetadata returns correct credential count`() =
        runTest {
            val mockCredential = mockk<PasskeyCredential>()
            coEvery {
                getAllCredentialsUseCase(any<Long>(), any<Long>())
            } returns Result.success(listOf(mockCredential, mockCredential))

            val requestBytes = byteArrayOf(DUMMY_BYTE_03)
            every { cborCodec.decodeFromFido2Format(any()) } returns mapOf("1" to SUB_GET_METADATA)

            val expectedResponseBytes = byteArrayOf(DUMMY_BYTE_AA, DUMMY_BYTE_BB)
            every { cborCodec.encodeToFido2Format(any()) } returns expectedResponseBytes

            val responseBytes = handler.handle(requestBytes)

            assertEquals(CTAP2_OK, responseBytes[0]) // CTAP2_OK status byte

            // Ensure the encoded bytes are concatenated correctly
            assertEquals(expectedResponseBytes[0], responseBytes[1])
            assertEquals(expectedResponseBytes[1], responseBytes[INDEX_2])
        }

    // ── SubCommand 2: enumerateRPsBegin ──────────────────────────────────────

    @Test
    fun `enumerateRPsBegin returns first RP with totalRPs count`() =
        runTest {
            val cred1 = createTestCredential("cred1", "https://example.com", "user1")
            val cred2 = createTestCredential("cred2", "https://other.com", "user2")
            coEvery { getAllCredentialsUseCase(any<Long>(), any<Long>()) } returns Result.success(listOf(cred1, cred2))
            coEvery { credentialRepository.getRelyingParty("https://example.com") } returns
                RelyingParty("https://example.com", "Example", null, 1, Instant.now())
            coEvery { credentialRepository.getRelyingParty("https://other.com") } returns
                RelyingParty("https://other.com", "Other", null, 1, Instant.now())

            every { cborCodec.decodeFromFido2Format(any()) } returns mapOf("1" to SUB_ENUM_RP_BEGIN)
            val encodedBytes = byteArrayOf(DUMMY_BYTE_CC)
            every { cborCodec.encodeToFido2Format(any()) } returns encodedBytes

            val response = handler.handle(byteArrayOf(DUMMY_BYTE_01))

            assertEquals(CTAP2_OK, response[0]) // CTAP2_OK
            assertTrue(response.size > 1) // Has encoded payload
        }

    @Test
    fun `enumerateRPsBegin returns NO_CREDENTIALS when empty`() =
        runTest {
            coEvery { getAllCredentialsUseCase(any<Long>(), any<Long>()) } returns Result.success(emptyList())

            every { cborCodec.decodeFromFido2Format(any()) } returns mapOf("1" to SUB_ENUM_RP_BEGIN)

            val response = handler.handle(byteArrayOf(DUMMY_BYTE_01))

            assertEquals(1, response.size)
            assertEquals(CTAP2_ERR_NO_CREDENTIALS, response[0]) // CTAP2_ERR_NO_CREDENTIALS
        }

    // ── SubCommand 3: enumerateRPsGetNextRP ──────────────────────────────────

    @Test
    fun `enumerateRPsGetNextRP returns next RP after Begin`() =
        runTest {
            // First do a Begin to populate the session with 2 RPs
            val cred1 = createTestCredential("cred1", "https://example.com", "user1")
            val cred2 = createTestCredential("cred2", "https://other.com", "user2")
            coEvery { getAllCredentialsUseCase(any<Long>(), any<Long>()) } returns Result.success(listOf(cred1, cred2))
            coEvery { credentialRepository.getRelyingParty("https://example.com") } returns
                RelyingParty("https://example.com", "Example", null, 1, Instant.now())
            coEvery { credentialRepository.getRelyingParty("https://other.com") } returns
                RelyingParty("https://other.com", "Other", null, 1, Instant.now())

            val encodedBytes = byteArrayOf(DUMMY_BYTE_CC)
            every { cborCodec.encodeToFido2Format(any()) } returns encodedBytes

            // Begin (subCommand 2) - consumes the first RP
            every { cborCodec.decodeFromFido2Format(any()) } returns mapOf("1" to SUB_ENUM_RP_BEGIN)
            handler.handle(byteArrayOf(DUMMY_BYTE_01))

            // GetNext (subCommand 3) - should return the second RP
            every { cborCodec.decodeFromFido2Format(any()) } returns mapOf("1" to SUB_ENUM_RP_NEXT)
            val response = handler.handle(byteArrayOf(DUMMY_BYTE_01))

            assertEquals(CTAP2_OK, response[0]) // CTAP2_OK
        }

    @Test
    fun `enumerateRPsGetNextRP returns NOT_ALLOWED when exhausted`() =
        runTest {
            // Begin with only 1 RP
            val cred1 = createTestCredential("cred1", "https://example.com", "user1")
            coEvery { getAllCredentialsUseCase(any<Long>(), any<Long>()) } returns Result.success(listOf(cred1))
            coEvery { credentialRepository.getRelyingParty("https://example.com") } returns
                RelyingParty("https://example.com", "Example", null, 1, Instant.now())

            val encodedBytes = byteArrayOf(DUMMY_BYTE_CC)
            every { cborCodec.encodeToFido2Format(any()) } returns encodedBytes

            // Begin (subCommand 2) - consumes the only RP
            every { cborCodec.decodeFromFido2Format(any()) } returns mapOf("1" to SUB_ENUM_RP_BEGIN)
            handler.handle(byteArrayOf(DUMMY_BYTE_01))

            // GetNext (subCommand 3) - session is empty
            every { cborCodec.decodeFromFido2Format(any()) } returns mapOf("1" to SUB_ENUM_RP_NEXT)
            val response = handler.handle(byteArrayOf(DUMMY_BYTE_01))

            assertEquals(1, response.size)
            assertEquals(CTAP2_ERR_NOT_ALLOWED, response[0]) // CTAP2_ERR_NOT_ALLOWED
        }

    // ── SubCommand 4: enumerateCredentialsBegin ──────────────────────────────

    @Test
    fun `enumerateCredentialsBegin returns first credential with totalCredentials`() =
        runTest {
            val cred1 = createTestCredential("cred1", "https://example.com", "user1")
            val cred2 = createTestCredential("cred2", "https://example.com", "user2")
            coEvery {
                credentialRepository.getCredentialsForRp("https://example.com")
            } returns Result.success(listOf(cred1, cred2))

            val subCommandParams = mapOf("rpId" to "https://example.com")
            every { cborCodec.decodeFromFido2Format(any()) } returns
                mapOf(
                    "1" to SUB_ENUM_CRED_BEGIN,
                    "2" to subCommandParams,
                )
            val encodedBytes = byteArrayOf(DUMMY_BYTE_DD)
            every { cborCodec.encodeToFido2Format(any()) } returns encodedBytes

            val response = handler.handle(byteArrayOf(DUMMY_BYTE_01))

            assertEquals(CTAP2_OK, response[0]) // CTAP2_OK
            assertTrue(response.size > 1)
        }

    @Test
    fun `enumerateCredentialsBegin returns NO_CREDENTIALS when RP has none`() =
        runTest {
            coEvery {
                credentialRepository.getCredentialsForRp("https://empty.com")
            } returns Result.success(emptyList())

            val subCommandParams = mapOf("rpId" to "https://empty.com")
            every { cborCodec.decodeFromFido2Format(any()) } returns
                mapOf(
                    "1" to SUB_ENUM_CRED_BEGIN,
                    "2" to subCommandParams,
                )

            val response = handler.handle(byteArrayOf(DUMMY_BYTE_01))

            assertEquals(1, response.size)
            assertEquals(CTAP2_ERR_NO_CREDENTIALS, response[0]) // CTAP2_ERR_NO_CREDENTIALS
        }

    // ── SubCommand 5: enumerateCredentialsGetNextCredential ──────────────────

    @Test
    fun `enumerateCredentialsGetNextCredential returns next credential after Begin`() =
        runTest {
            val cred1 = createTestCredential("cred1", "https://example.com", "user1")
            val cred2 = createTestCredential("cred2", "https://example.com", "user2")
            coEvery {
                credentialRepository.getCredentialsForRp("https://example.com")
            } returns Result.success(listOf(cred1, cred2))

            val encodedBytes = byteArrayOf(DUMMY_BYTE_DD)
            every { cborCodec.encodeToFido2Format(any()) } returns encodedBytes

            // Begin (subCommand 4) - consumes first credential
            val subCommandParams = mapOf("rpId" to "https://example.com")
            every { cborCodec.decodeFromFido2Format(any()) } returns
                mapOf(
                    "1" to SUB_ENUM_CRED_BEGIN,
                    "2" to subCommandParams,
                )
            handler.handle(byteArrayOf(DUMMY_BYTE_01))

            // GetNext (subCommand 5)
            every { cborCodec.decodeFromFido2Format(any()) } returns mapOf("1" to SUB_ENUM_CRED_NEXT)
            val response = handler.handle(byteArrayOf(DUMMY_BYTE_01))

            assertEquals(CTAP2_OK, response[0]) // CTAP2_OK
        }

    @Test
    fun `enumerateCredentialsGetNextCredential returns NOT_ALLOWED when exhausted`() =
        runTest {
            // Begin with only 1 credential
            val cred1 = createTestCredential("cred1", "https://example.com", "user1")
            coEvery {
                credentialRepository.getCredentialsForRp("https://example.com")
            } returns Result.success(listOf(cred1))

            val encodedBytes = byteArrayOf(DUMMY_BYTE_DD)
            every { cborCodec.encodeToFido2Format(any()) } returns encodedBytes

            // Begin (subCommand 4) - consumes the only credential
            val subCommandParams = mapOf("rpId" to "https://example.com")
            every { cborCodec.decodeFromFido2Format(any()) } returns
                mapOf(
                    "1" to SUB_ENUM_CRED_BEGIN,
                    "2" to subCommandParams,
                )
            handler.handle(byteArrayOf(DUMMY_BYTE_01))

            // GetNext (subCommand 5) - session is empty
            every { cborCodec.decodeFromFido2Format(any()) } returns mapOf("1" to SUB_ENUM_CRED_NEXT)
            val response = handler.handle(byteArrayOf(DUMMY_BYTE_01))

            assertEquals(1, response.size)
            assertEquals(CTAP2_ERR_NOT_ALLOWED, response[0]) // CTAP2_ERR_NOT_ALLOWED
        }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun createTestCredential(
        id: String,
        rpId: String,
        userName: String,
    ): PasskeyCredential {
        return PasskeyCredential.createTest(id = id, rpId = rpId, userName = userName)
    }
}
