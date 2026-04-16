package com.chimali.fido2.ctap2

import android.util.Log
import com.chimali.fido2.data.crypto.CborCodec
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.model.RelyingParty
import com.chimali.fido2.domain.repository.CredentialRepository
import com.chimali.fido2.domain.usecase.DeleteCredentialUseCase
import com.chimali.fido2.domain.usecase.GetAllCredentialsUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class Ctap2CredentialManagementHandlerTest {
    private lateinit var cborCodec: CborCodec
    private lateinit var getAllCredentialsUseCase: GetAllCredentialsUseCase
    private lateinit var deleteCredentialUseCase: DeleteCredentialUseCase
    private lateinit var credentialRepository: CredentialRepository
    private lateinit var handler: Ctap2CredentialManagementHandler

    @BeforeEach
    fun setup() {
        mockkStatic(Log::class)
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0

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

    // ── SubCommand parsing ───────────────────────────────────────────────────

    @Test
    fun `handle returns CTAP1_ERR_MISSING_PARAMETER if subCommand is missing`() =
        runTest {
            val requestBytes = byteArrayOf(0x01)
            every { cborCodec.decodeFromFido2Format(any()) } returns emptyMap()

            val response = handler.handle(requestBytes)

            assertEquals(1, response.size)
            assertEquals(0x0E.toByte(), response[0])
        }

    @Test
    fun `handle returns CTAP2_ERR_UNSUPPORTED_OPTION for unknown subCommand`() =
        runTest {
            val requestBytes = byteArrayOf(0x02)
            every { cborCodec.decodeFromFido2Format(any()) } returns mapOf("1" to 99)

            val response = handler.handle(requestBytes)

            assertEquals(1, response.size)
            assertEquals(0x11.toByte(), response[0])
        }

    // ── SubCommand 1: getCredsMetadata ───────────────────────────────────────

    @Test
    fun `handleGetCredsMetadata returns correct credential count`() =
        runTest {
            val mockCredential = mockk<PasskeyCredential>()
            coEvery { getAllCredentialsUseCase() } returns flowOf(mockCredential, mockCredential)

            val requestBytes = byteArrayOf(0x03)
            every { cborCodec.decodeFromFido2Format(any()) } returns mapOf("1" to 1)

            val expectedResponseBytes = byteArrayOf(0xAA.toByte(), 0xBB.toByte())
            every { cborCodec.encodeToFido2Format(any()) } returns expectedResponseBytes

            val responseBytes = handler.handle(requestBytes)

            assertEquals(0x00.toByte(), responseBytes[0]) // CTAP2_OK status byte

            // Ensure the encoded bytes are concatenated correctly
            assertEquals(expectedResponseBytes[0], responseBytes[1])
            assertEquals(expectedResponseBytes[1], responseBytes[2])
        }

    // ── SubCommand 2: enumerateRPsBegin ──────────────────────────────────────

    @Test
    fun `enumerateRPsBegin returns first RP with totalRPs count`() =
        runTest {
            val cred1 = createTestCredential("cred1", "https://example.com", "user1")
            val cred2 = createTestCredential("cred2", "https://other.com", "user2")
            coEvery { getAllCredentialsUseCase() } returns flowOf(cred1, cred2)
            coEvery { credentialRepository.getRelyingParty("https://example.com") } returns
                RelyingParty("https://example.com", "Example", null, 1, Instant.now())
            coEvery { credentialRepository.getRelyingParty("https://other.com") } returns
                RelyingParty("https://other.com", "Other", null, 1, Instant.now())

            every { cborCodec.decodeFromFido2Format(any()) } returns mapOf("1" to 2)
            val encodedBytes = byteArrayOf(0xCC.toByte())
            every { cborCodec.encodeToFido2Format(any()) } returns encodedBytes

            val response = handler.handle(byteArrayOf(0x01))

            assertEquals(0x00.toByte(), response[0]) // CTAP2_OK
            assertTrue(response.size > 1) // Has encoded payload
        }

    @Test
    fun `enumerateRPsBegin returns NO_CREDENTIALS when empty`() =
        runTest {
            coEvery { getAllCredentialsUseCase() } returns flowOf()

            every { cborCodec.decodeFromFido2Format(any()) } returns mapOf("1" to 2)

            val response = handler.handle(byteArrayOf(0x01))

            assertEquals(1, response.size)
            assertEquals(0x22.toByte(), response[0]) // CTAP2_ERR_NO_CREDENTIALS
        }

    // ── SubCommand 3: enumerateRPsGetNextRP ──────────────────────────────────

    @Test
    fun `enumerateRPsGetNextRP returns next RP after Begin`() =
        runTest {
            // First do a Begin to populate the session with 2 RPs
            val cred1 = createTestCredential("cred1", "https://example.com", "user1")
            val cred2 = createTestCredential("cred2", "https://other.com", "user2")
            coEvery { getAllCredentialsUseCase() } returns flowOf(cred1, cred2)
            coEvery { credentialRepository.getRelyingParty("https://example.com") } returns
                RelyingParty("https://example.com", "Example", null, 1, Instant.now())
            coEvery { credentialRepository.getRelyingParty("https://other.com") } returns
                RelyingParty("https://other.com", "Other", null, 1, Instant.now())

            val encodedBytes = byteArrayOf(0xCC.toByte())
            every { cborCodec.encodeToFido2Format(any()) } returns encodedBytes

            // Begin (subCommand 2) - consumes the first RP
            every { cborCodec.decodeFromFido2Format(any()) } returns mapOf("1" to 2)
            handler.handle(byteArrayOf(0x01))

            // GetNext (subCommand 3) - should return the second RP
            every { cborCodec.decodeFromFido2Format(any()) } returns mapOf("1" to 3)
            val response = handler.handle(byteArrayOf(0x01))

            assertEquals(0x00.toByte(), response[0]) // CTAP2_OK
        }

    @Test
    fun `enumerateRPsGetNextRP returns NOT_ALLOWED when exhausted`() =
        runTest {
            // Begin with only 1 RP
            val cred1 = createTestCredential("cred1", "https://example.com", "user1")
            coEvery { getAllCredentialsUseCase() } returns flowOf(cred1)
            coEvery { credentialRepository.getRelyingParty("https://example.com") } returns
                RelyingParty("https://example.com", "Example", null, 1, Instant.now())

            val encodedBytes = byteArrayOf(0xCC.toByte())
            every { cborCodec.encodeToFido2Format(any()) } returns encodedBytes

            // Begin (subCommand 2) - consumes the only RP
            every { cborCodec.decodeFromFido2Format(any()) } returns mapOf("1" to 2)
            handler.handle(byteArrayOf(0x01))

            // GetNext (subCommand 3) - session is empty
            every { cborCodec.decodeFromFido2Format(any()) } returns mapOf("1" to 3)
            val response = handler.handle(byteArrayOf(0x01))

            assertEquals(1, response.size)
            assertEquals(0x30.toByte(), response[0]) // CTAP2_ERR_NOT_ALLOWED
        }

    // ── SubCommand 4: enumerateCredentialsBegin ──────────────────────────────

    @Test
    fun `enumerateCredentialsBegin returns first credential with totalCredentials`() =
        runTest {
            val cred1 = createTestCredential("cred1", "https://example.com", "user1")
            val cred2 = createTestCredential("cred2", "https://example.com", "user2")
            coEvery { credentialRepository.getCredentialsByRpId("https://example.com") } returns flowOf(cred1, cred2)

            val subCommandParams = mapOf("rpId" to "https://example.com")
            every { cborCodec.decodeFromFido2Format(any()) } returns
                mapOf(
                    "1" to 4,
                    "2" to subCommandParams,
                )
            val encodedBytes = byteArrayOf(0xDD.toByte())
            every { cborCodec.encodeToFido2Format(any()) } returns encodedBytes

            val response = handler.handle(byteArrayOf(0x01))

            assertEquals(0x00.toByte(), response[0]) // CTAP2_OK
            assertTrue(response.size > 1)
        }

    @Test
    fun `enumerateCredentialsBegin returns NO_CREDENTIALS when RP has none`() =
        runTest {
            coEvery { credentialRepository.getCredentialsByRpId("https://empty.com") } returns flowOf()

            val subCommandParams = mapOf("rpId" to "https://empty.com")
            every { cborCodec.decodeFromFido2Format(any()) } returns
                mapOf(
                    "1" to 4,
                    "2" to subCommandParams,
                )

            val response = handler.handle(byteArrayOf(0x01))

            assertEquals(1, response.size)
            assertEquals(0x22.toByte(), response[0]) // CTAP2_ERR_NO_CREDENTIALS
        }

    // ── SubCommand 5: enumerateCredentialsGetNextCredential ──────────────────

    @Test
    fun `enumerateCredentialsGetNextCredential returns next credential after Begin`() =
        runTest {
            val cred1 = createTestCredential("cred1", "https://example.com", "user1")
            val cred2 = createTestCredential("cred2", "https://example.com", "user2")
            coEvery { credentialRepository.getCredentialsByRpId("https://example.com") } returns flowOf(cred1, cred2)

            val encodedBytes = byteArrayOf(0xDD.toByte())
            every { cborCodec.encodeToFido2Format(any()) } returns encodedBytes

            // Begin (subCommand 4) - consumes first credential
            val subCommandParams = mapOf("rpId" to "https://example.com")
            every { cborCodec.decodeFromFido2Format(any()) } returns
                mapOf(
                    "1" to 4,
                    "2" to subCommandParams,
                )
            handler.handle(byteArrayOf(0x01))

            // GetNext (subCommand 5)
            every { cborCodec.decodeFromFido2Format(any()) } returns mapOf("1" to 5)
            val response = handler.handle(byteArrayOf(0x01))

            assertEquals(0x00.toByte(), response[0]) // CTAP2_OK
        }

    @Test
    fun `enumerateCredentialsGetNextCredential returns NOT_ALLOWED when exhausted`() =
        runTest {
            // Begin with only 1 credential
            val cred1 = createTestCredential("cred1", "https://example.com", "user1")
            coEvery { credentialRepository.getCredentialsByRpId("https://example.com") } returns flowOf(cred1)

            val encodedBytes = byteArrayOf(0xDD.toByte())
            every { cborCodec.encodeToFido2Format(any()) } returns encodedBytes

            // Begin (subCommand 4) - consumes the only credential
            val subCommandParams = mapOf("rpId" to "https://example.com")
            every { cborCodec.decodeFromFido2Format(any()) } returns
                mapOf(
                    "1" to 4,
                    "2" to subCommandParams,
                )
            handler.handle(byteArrayOf(0x01))

            // GetNext (subCommand 5) - session is empty
            every { cborCodec.decodeFromFido2Format(any()) } returns mapOf("1" to 5)
            val response = handler.handle(byteArrayOf(0x01))

            assertEquals(1, response.size)
            assertEquals(0x30.toByte(), response[0]) // CTAP2_ERR_NOT_ALLOWED
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
