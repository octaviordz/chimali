package com.chimali.fido2.ctap2

import com.chimali.core.common.annotation.Req
import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.domain.model.CredentialKeyDescriptor
import com.chimali.core.domain.valueobject.RpId
import com.chimali.core.domain.valueobject.UserId
import com.chimali.fido2.bluetooth.CtapHidMessage
import com.chimali.fido2.bluetooth.HidReportParser
import com.chimali.fido2.data.crypto.CborCodec
import com.chimali.fido2.domain.exception.Fido2Exception
import com.chimali.fido2.domain.model.AttestationObject
import com.chimali.fido2.domain.model.AttestationStatement
import com.chimali.fido2.domain.model.AuthenticatorData
import com.chimali.fido2.domain.model.MakeCredentialResult
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.model.SensitiveCredentialText
import com.chimali.fido2.domain.model.UserVerificationRequirement
import com.chimali.fido2.domain.service.CeremonyLock
import com.chimali.fido2.presentation.navigation.Fido2UiEvent
import com.chimali.fido2.presentation.navigation.Fido2UiEventBus
import com.chimali.fido2.testing.CapturedDiagnostics
import com.chimali.fido2.testing.stubSensitiveRequestCompatibility
import com.chimali.fido2.testing.testCredentialIdFromLabel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@Req("NFR-SEC-041", "TR-AUTH-031")
class Ctap2MakeCredentialHandlerValidationTest {
    private val codec = mockk<CborCodec>()
    private val reportParser = mockk<HidReportParser>()
    private val uiEventBus = mockk<Fido2UiEventBus>(relaxed = true)
    private val ceremonyLock = mockk<CeremonyLock>(relaxed = true)
    private val handler = Ctap2MakeCredentialHandler(codec, reportParser, uiEventBus, ceremonyLock)

    private val channelId = byteArrayOf(1, 2, 3, 4)

    init {
        codec.stubSensitiveRequestCompatibility()
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `partial request parsing erases fallback owners when later algorithm validation fails`() =
        runTest {
            val realCodec = CborCodec()
            val decodedOwners = mutableListOf<SensitiveCredentialText>()
            val parserOwners = mutableListOf<SensitiveCredentialText>()
            realCodec.onDecodedTextOwnerForTest = decodedOwners::add
            val realHandler =
                Ctap2MakeCredentialHandler(realCodec, reportParser, uiEventBus, ceremonyLock).apply {
                    onTemporaryRequestOwnerCreatedForTest = parserOwners::add
                }
            every { ceremonyLock.tryLock() } returns true
            every { reportParser.encodeResponse(any()) } answers {
                listOf(firstArg<CtapHidMessage>().payload)
            }
            val encoded =
                realCodec.encodeToFido2Format(
                    mapOf(
                        "1" to ByteArray(32),
                        "2" to mapOf("id" to "example.com"),
                        "3" to mapOf("id" to byteArrayOf(1)),
                        "4" to emptyList<Any>(),
                    ),
                )

            val response = realHandler.handle(CtapHidMessage(channelId, 0x10, byteArrayOf(0x01) + encoded))

            assertEquals(0x14.toByte(), response.single().first())
            assertEquals(3, parserOwners.size)
            assertTrue((decodedOwners + parserOwners).all { owner -> owner.copyCharacters().all { it == '\u0000' } })
            verify(exactly = 0) { uiEventBus.dispatch(any()) }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @ParameterizedTest
    @ValueSource(strings = ["success", "malformed", "failure", "cancelled"])
    fun `registration diagnostics exclude RP and account input on every terminal path`(mode: String) =
        runTest {
            val marker = "synthetic-account-marker"
            every { ceremonyLock.tryLock() } returns true
            val request =
                validRequest(listOf(-7L)) +
                    mapOf(
                        "2" to mapOf("id" to "$marker.example.com", "name" to marker),
                        "3" to mapOf("id" to byteArrayOf(1), "name" to marker, "displayName" to marker),
                    )
            every { codec.decodeFromFido2Format(any()) } answers {
                require(mode != "malformed") { marker }
                request
            }
            every { codec.encodeToFido2Format(any()) } returns byteArrayOf(0x55)
            every { reportParser.encodeResponse(any()) } answers { listOf(firstArg<CtapHidMessage>().payload) }
            val data = mockk<AuthenticatorData> { every { credentialId } returns byteArrayOf(9) }
            val statement =
                mockk<AttestationStatement> {
                    every { fmt } returns "none"
                    every { authData } returns byteArrayOf(1)
                }
            val attestation =
                mockk<AttestationObject> {
                    every { clientData } returns
                        com.chimali.fido2.domain.model.ClientData.create(
                            challenge = ByteArray(32),
                            origin = "https://synthetic.example",
                        )
                    every { fmt } returns "none"
                    every { authData } returns data
                    every { attStmt } returns statement
                }
            val credential = syntheticCredential()
            val result = MakeCredentialResult(attestation, credential)
            every { uiEventBus.dispatch(any()) } answers {
                val event = firstArg<Fido2UiEvent.RegistrationRequested>()
                assertEquals(marker, event.options.user.name)
                when (mode) {
                    "success" -> event.deferred.complete(Outcome.Success(result))
                    "failure" -> event.deferred.complete(Outcome.Error(DomainError.StorageError(marker)))
                    "cancelled" -> event.deferred.cancel(CancellationException(marker))
                }
                Unit
            }
            CapturedDiagnostics().use { logs ->
                if (mode == "cancelled") {
                    assertFailsWith<CancellationException> { handler.handle(requestMessage()) }
                } else {
                    val response = handler.handle(requestMessage())
                    val expected =
                        when (mode) {
                            "success" -> 0
                            "malformed" -> 0x36
                            else -> 0x28
                        }
                    assertEquals(expected.toByte(), response.single().first())
                }
                if (mode == "success") {
                    assertCredentialErased(credential)
                }
                assertTrue(logs.size > 0)
                logs.assertNoSensitiveValues(marker)
            }
            verify(exactly = 1) { ceremonyLock.unlock() }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `CTAP required UV is preserved in registration options`() =
        runTest {
            every { ceremonyLock.tryLock() } returns true
            every { codec.decodeFromFido2Format(any()) } returns
                validRequest(algorithms = listOf(-7L)) + ("options" to mapOf("uv" to true))
            every { codec.encodeToFido2Format(any()) } returns byteArrayOf(0x55)
            every { reportParser.encodeResponse(any()) } answers { listOf(firstArg<CtapHidMessage>().payload) }
            val authenticatorData = mockk<AuthenticatorData> { every { credentialId } returns byteArrayOf(9) }
            val statement =
                mockk<AttestationStatement> {
                    every { fmt } returns "none"
                    every { authData } returns byteArrayOf(1)
                }
            val attestation =
                mockk<AttestationObject> {
                    every { clientData } returns
                        com.chimali.fido2.domain.model.ClientData.create(
                            challenge = ByteArray(32),
                            origin = "https://synthetic.example",
                        )
                    every { fmt } returns "none"
                    every { authData } returns authenticatorData
                    every { attStmt } returns statement
                }
            val result = MakeCredentialResult(attestation, syntheticCredential())
            var verificationRequirement: UserVerificationRequirement? = null
            every { uiEventBus.dispatch(any()) } answers {
                val event = firstArg<Fido2UiEvent.RegistrationRequested>()
                verificationRequirement = event.options.authenticatorSelection?.userVerification
                event.deferred.complete(Outcome.Success(result))
                Unit
            }

            handler.handle(requestMessage())

            assertEquals(UserVerificationRequirement.REQUIRED, verificationRequirement)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `empty or wrong command returns invalid CBOR without acquiring ceremony lock`() =
        runTest {
            every { reportParser.encodeResponse(any()) } answers { listOf(firstArg<CtapHidMessage>().payload) }

            val emptyPayloadResponse = handler.handle(CtapHidMessage(channelId, 0x10, byteArrayOf()))
            val wrongCommandResponse = handler.handle(CtapHidMessage(channelId, 0x10, byteArrayOf(0x02)))

            assertEquals(0x12.toByte(), emptyPayloadResponse.single().first())
            assertEquals(0x12.toByte(), wrongCommandResponse.single().first())
            verify(exactly = 0) { ceremonyLock.tryLock() }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `active ceremony returns channel busy without decoding request`() =
        runTest {
            every { ceremonyLock.tryLock() } returns false
            every { reportParser.encodeResponse(any()) } answers { listOf(firstArg<CtapHidMessage>().payload) }

            val response = handler.handle(requestMessage())

            assertEquals(0x06.toByte(), response.single().first())
            verify(exactly = 0) { codec.decodeFromFido2Format(any()) }
            verify(exactly = 0) { ceremonyLock.unlock() }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `empty decoded map returns invalid CBOR and releases ceremony lock`() =
        runTest {
            every { ceremonyLock.tryLock() } returns true
            every { codec.decodeFromFido2Format(any()) } returns emptyMap()
            every { reportParser.encodeResponse(any()) } answers { listOf(firstArg<CtapHidMessage>().payload) }

            val response = handler.handle(requestMessage())

            assertEquals(0x12.toByte(), response.single().first())
            verify(exactly = 1) { ceremonyLock.unlock() }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `request without a valid algorithm returns missing parameter and releases lock`() =
        runTest {
            every { ceremonyLock.tryLock() } returns true
            every { codec.decodeFromFido2Format(any()) } returns validRequest(algorithms = emptyList<Any>())
            every { reportParser.encodeResponse(any()) } answers { listOf(firstArg<CtapHidMessage>().payload) }

            val response = handler.handle(requestMessage())

            assertEquals(0x14.toByte(), response.single().first())
            verify(exactly = 1) { ceremonyLock.unlock() }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `unsupported algorithm returns unsupported algorithm and releases lock`() =
        runTest {
            every { ceremonyLock.tryLock() } returns true
            every { codec.decodeFromFido2Format(any()) } returns validRequest(algorithms = listOf(-19L))
            every { reportParser.encodeResponse(any()) } answers { listOf(firstArg<CtapHidMessage>().payload) }

            val response = handler.handle(requestMessage())

            assertEquals(0x26.toByte(), response.single().first())
            verify(exactly = 1) { ceremonyLock.unlock() }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `invalid credProtect extension fails before registration dispatch`() =
        runTest {
            val invalidPolicies =
                listOf<Any?>(
                    0L,
                    -1L,
                    4L,
                    4_294_967_297L,
                    Long.MIN_VALUE,
                    Long.MAX_VALUE,
                    "3",
                    true,
                    null,
                )
            every { ceremonyLock.tryLock() } returns true
            every { codec.decodeFromFido2Format(any()) } returnsMany
                invalidPolicies.map { rawPolicy ->
                    validRequest(listOf(-7L)) + ("extensions" to mapOf("credProtect" to rawPolicy))
                }
            every { reportParser.encodeResponse(any()) } answers { listOf(firstArg<CtapHidMessage>().payload) }

            invalidPolicies.forEach {
                val response = handler.handle(requestMessage())
                assertEquals(0x36.toByte(), response.single().first())
            }

            verify(exactly = 0) { uiEventBus.dispatch(any()) }
            verify(exactly = invalidPolicies.size) { ceremonyLock.unlock() }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `actual CBOR MakeCredential key six delivers credProtect to registration`() =
        runTest {
            val realCodec = CborCodec()
            val decodedByteStrings = mutableListOf<ByteArray>()
            realCodec.onDecodedByteStringForTest = decodedByteStrings::add
            val realHandler =
                Ctap2MakeCredentialHandler(realCodec, reportParser, uiEventBus, ceremonyLock)
            val request =
                mapOf(
                    "1" to ByteArray(32),
                    "2" to mapOf("id" to "example.com", "name" to "Example"),
                    "3" to mapOf("id" to byteArrayOf(1), "name" to "user", "displayName" to "User"),
                    "4" to listOf(mapOf("alg" to -7L, "type" to "public-key")),
                    "6" to mapOf("credProtect" to 2L),
                )
            val cbor = realCodec.encodeToFido2Format(request)
            val message = CtapHidMessage(channelId, 0x10, byteArrayOf(0x01) + cbor)
            every { ceremonyLock.tryLock() } returns true
            every { reportParser.encodeResponse(any()) } answers { listOf(firstArg<CtapHidMessage>().payload) }
            var policy: Any? = null
            every { uiEventBus.dispatch(any()) } answers {
                val event = firstArg<Fido2UiEvent.RegistrationRequested>()
                policy = event.options.extensions?.get("credProtect")
                event.deferred.complete(Outcome.Error(DomainError.OperationDenied("synthetic stop")))
                Unit
            }

            realHandler.handle(message)

            assertEquals(2, policy)
            decodedByteStrings.forEach { bytes -> assertContentEquals(ByteArray(bytes.size), bytes) }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `actual CBOR rejects explicit null and undefined extension policy values before dispatch`() =
        runTest {
            val realCodec = CborCodec()
            val decodedOwners = mutableListOf<SensitiveCredentialText>()
            realCodec.onDecodedTextOwnerForTest = decodedOwners::add
            val realHandler =
                Ctap2MakeCredentialHandler(realCodec, reportParser, uiEventBus, ceremonyLock)
            every { ceremonyLock.tryLock() } returns true
            every { reportParser.encodeResponse(any()) } answers { listOf(firstArg<CtapHidMessage>().payload) }

            val malformedExtensions: List<Any> =
                listOf(
                    CborCodec.SimpleValue.NULL,
                    CborCodec.SimpleValue.UNDEFINED,
                    mapOf("credProtect" to CborCodec.SimpleValue.NULL),
                    mapOf("credProtect" to CborCodec.SimpleValue.UNDEFINED),
                )
            malformedExtensions.forEach { extensionValue ->
                val encoded =
                    realCodec.encodeToFido2Format(validRequest(listOf(-7L)) + ("6" to extensionValue))
                val response = realHandler.handle(CtapHidMessage(channelId, 0x10, byteArrayOf(0x01) + encoded))
                assertEquals(0x36.toByte(), response.single().first())
                assertDecodedOwnersErased(decodedOwners)
                decodedOwners.clear()
            }

            verify(exactly = 0) { uiEventBus.dispatch(any()) }

            // Absence remains the default and all supported explicit values pass protocol validation.
            val validExtensions: List<Map<String, Any>> =
                listOf(emptyMap(), mapOf("credProtect" to 1L), mapOf("credProtect" to 2L), mapOf("credProtect" to 3L))
            var dispatchCount = 0
            validExtensions.forEachIndexed { index, extensionMap ->
                val request =
                    validRequest(listOf(-7L)) +
                        if (extensionMap.isEmpty()) emptyMap() else mapOf("6" to extensionMap)
                val encoded = realCodec.encodeToFido2Format(request)
                every { uiEventBus.dispatch(any()) } answers {
                    dispatchCount += 1
                    val event = firstArg<Fido2UiEvent.RegistrationRequested>()
                    assertEquals(-7, event.options.selectedAlgId)
                    event.deferred.complete(
                        Outcome.Error(DomainError.OperationDenied("synthetic stop")),
                    )
                    Unit
                }
                val response = realHandler.handle(CtapHidMessage(channelId, 0x10, byteArrayOf(0x01) + encoded))
                assertEquals(0x27.toByte(), response.single().first())
                assertEquals(index + 1, dispatchCount, "each valid control must reach the registration dispatch")
            }
            assertDecodedOwnersErased(decodedOwners)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `actual CBOR rejects malformed options map and implemented boolean fields before dispatch`() =
        runTest {
            val realCodec = CborCodec()
            val decodedOwners = mutableListOf<SensitiveCredentialText>()
            realCodec.onDecodedTextOwnerForTest = decodedOwners::add
            val realHandler =
                Ctap2MakeCredentialHandler(realCodec, reportParser, uiEventBus, ceremonyLock)
            every { ceremonyLock.tryLock() } returns true
            every { reportParser.encodeResponse(any()) } answers { listOf(firstArg<CtapHidMessage>().payload) }

            val malformedOptions: List<Any> =
                listOf(
                    CborCodec.SimpleValue.NULL,
                    CborCodec.SimpleValue.UNDEFINED,
                    1L,
                    mapOf("uv" to "true"),
                    mapOf("uv" to CborCodec.SimpleValue.NULL),
                    mapOf("rk" to 1L),
                    mapOf("rk" to CborCodec.SimpleValue.UNDEFINED),
                )
            malformedOptions.forEach { optionValue ->
                val encoded = realCodec.encodeToFido2Format(validRequest(listOf(-7L)) + ("7" to optionValue))
                val response = realHandler.handle(CtapHidMessage(channelId, 0x10, byteArrayOf(0x01) + encoded))
                assertEquals(0x12.toByte(), response.single().first())
                assertDecodedOwnersErased(decodedOwners)
                decodedOwners.clear()
            }
            verify(exactly = 0) { uiEventBus.dispatch(any()) }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `COSE algorithm values wrapping to implemented identifiers fail before dispatch`() =
        runTest {
            val wrappingValues = listOf(4_294_967_289L, 4_294_967_288L, 4_294_967_247L, -4_294_967_289L)
            every { ceremonyLock.tryLock() } returns true
            every { codec.decodeFromFido2Format(any()) } returnsMany
                wrappingValues.map { value -> validRequest(listOf(value)) }
            every { reportParser.encodeResponse(any()) } answers { listOf(firstArg<CtapHidMessage>().payload) }

            wrappingValues.forEach { _ ->
                val response = handler.handle(requestMessage())
                assertEquals(0x36.toByte(), response.single().first())
            }

            verify(exactly = 0) { uiEventBus.dispatch(any()) }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `successful registration dispatch encodes the provided attestation bytes`() =
        runTest {
            every { ceremonyLock.tryLock() } returns true
            every { codec.decodeFromFido2Format(any()) } returns validRequest(algorithms = listOf(-7L))
            every { codec.encodeToFido2Format(any()) } returns byteArrayOf(0x55)
            every { reportParser.encodeResponse(any()) } answers { listOf(firstArg<CtapHidMessage>().payload) }
            val authenticatorData = mockk<AuthenticatorData> { every { credentialId } returns byteArrayOf(9) }
            val statement =
                mockk<AttestationStatement> {
                    every { fmt } returns "none"
                    every { authData } returns byteArrayOf(1, 2, 3)
                }
            val attestation =
                mockk<AttestationObject> {
                    every { clientData } returns
                        com.chimali.fido2.domain.model.ClientData.create(
                            challenge = ByteArray(32),
                            origin = "https://synthetic.example",
                        )
                    every { fmt } returns "none"
                    every { authData } returns authenticatorData
                    every { attStmt } returns statement
                }
            val credential = syntheticCredential()
            val result = MakeCredentialResult(attestation, credential)
            var selectedAlgorithm: Int? = null
            every { uiEventBus.dispatch(any()) } answers {
                val event = firstArg<Fido2UiEvent.RegistrationRequested>()
                selectedAlgorithm = event.options.selectedAlgId
                event.deferred.complete(Outcome.Success(result))
                Unit
            }

            val response = handler.handle(requestMessage())

            assertEquals(0, response.single()[0].toInt())
            assertEquals(0x55.toByte(), response.single()[1])
            assertEquals(-7, selectedAlgorithm)
            verify(exactly = 1) { ceremonyLock.unlock() }
            assertCredentialErased(credential)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `registration failures map storage authorization and other errors to CTAP statuses`() =
        runTest {
            every { codec.decodeFromFido2Format(any()) } returns validRequest(algorithms = listOf(-7L))
            every { reportParser.encodeResponse(any()) } answers { listOf(firstArg<CtapHidMessage>().payload) }
            var error: DomainError = DomainError.DatabaseError("database")
            every { uiEventBus.dispatch(any()) } answers {
                firstArg<Fido2UiEvent.RegistrationRequested>().deferred.complete(Outcome.Error(error))
                Unit
            }

            val cases =
                listOf(
                    DomainError.DatabaseError("database") to 0x28.toByte(),
                    DomainError.StorageError("storage") to 0x28.toByte(),
                    DomainError.OperationDenied("denied") to 0x27.toByte(),
                    DomainError.ValidationError("invalid") to 0x36.toByte(),
                )

            cases.forEach { (caseError, expectedStatus) ->
                error = caseError
                every { ceremonyLock.tryLock() } returns true

                val response = handler.handle(requestMessage())

                assertEquals(expectedStatus, response.single().first())
            }
            verify(exactly = cases.size) { ceremonyLock.unlock() }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `named request keys options and PRF extension forms decode before algorithm rejection`() =
        runTest {
            val validSalt = ByteArray(32) { it.toByte() }
            val extensionVariants =
                listOf(
                    mapOf("credProtect" to 3L),
                    mapOf("hmac-secret" to mapOf(2 to validSalt)),
                    mapOf("hmac-secret" to mapOf(1 to byteArrayOf(1))),
                    mapOf("hmac-secret" to mapOf(1L to validSalt)),
                    mapOf("hmac-secret" to mapOf(1 to validSalt, 2L to validSalt)),
                )
            every { ceremonyLock.tryLock() } returns true
            every { codec.decodeFromFido2Format(any()) } returnsMany
                extensionVariants.map { extensions ->
                    mapOf(
                        "clientDataHash" to ByteArray(32),
                        "rp" to mapOf("id" to "example.com"),
                        "user" to mapOf("id" to byteArrayOf(1), "name" to "user"),
                        "pubKeyCredParams" to listOf("invalid", mapOf("alg" to -19L)),
                        "options" to mapOf("uv" to true, "rk" to false),
                        "extensions" to extensions,
                    )
                }
            every { reportParser.encodeResponse(any()) } answers { listOf(firstArg<CtapHidMessage>().payload) }

            repeat(extensionVariants.size) {
                val response = handler.handle(requestMessage())
                assertEquals(0x26.toByte(), response.single().first())
            }
            verify(exactly = extensionVariants.size) { ceremonyLock.unlock() }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `malformed request shapes return protocol errors at each required field boundary`() =
        runTest {
            every { ceremonyLock.tryLock() } returns true
            every { codec.decodeFromFido2Format(any()) } returnsMany
                listOf(
                    mapOf("other" to true),
                    mapOf("1" to ByteArray(32)),
                    mapOf("1" to ByteArray(32), "2" to emptyMap<String, Any>()),
                    mapOf("1" to ByteArray(32), "2" to mapOf("id" to "example.com")),
                    mapOf(
                        "1" to ByteArray(32),
                        "2" to mapOf("id" to "example.com"),
                        "3" to emptyMap<String, Any>(),
                    ),
                    mapOf(
                        "1" to ByteArray(32),
                        "2" to mapOf("id" to "example.com"),
                        "3" to mapOf("id" to byteArrayOf(1)),
                    ),
                    mapOf(
                        "1" to ByteArray(32),
                        "2" to mapOf("id" to "example.com"),
                        "3" to mapOf("id" to byteArrayOf(1)),
                        "4" to "not-a-list",
                    ),
                )
            every { reportParser.encodeResponse(any()) } answers { listOf(firstArg<CtapHidMessage>().payload) }

            repeat(7) {
                val response = handler.handle(requestMessage())
                assertEquals(0x12.toByte(), response.single().first())
            }
            verify(exactly = 7) { ceremonyLock.unlock() }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `FIDO exception families map to CTAP statuses and always release the lock`() =
        runTest {
            every { ceremonyLock.tryLock() } returns true
            every { reportParser.encodeResponse(any()) } answers { listOf(firstArg<CtapHidMessage>().payload) }
            var failure: Fido2Exception = Fido2Exception.UserVerificationException("denied")
            every { codec.decodeFromFido2Format(any()) } answers { throw failure }
            val cases =
                listOf(
                    Fido2Exception.UserVerificationException("denied") to 0x27.toByte(),
                    Fido2Exception.TooManyCredentials(limit = 10) to 0x28.toByte(),
                    Fido2Exception.CredentialException("storage") to 0x28.toByte(),
                    Fido2Exception.InvalidParameter("unsupported") to 0x36.toByte(),
                )

            cases.forEach { (caseFailure, status) ->
                failure = caseFailure
                assertEquals(status, handler.handle(requestMessage()).single().first())
            }
            verify(exactly = cases.size) { ceremonyLock.unlock() }
        }

    private fun requestMessage() = CtapHidMessage(channelId, 0x10, byteArrayOf(0x01, 0x00))

    private fun syntheticCredential(): PasskeyCredential =
        PasskeyCredential.create(
            id = testCredentialIdFromLabel("ctap-registration-owner"),
            rpId = RpId("example.com"),
            userId = UserId("synthetic-user"),
            userName = "synthetic-name",
            userDisplayName = "Synthetic User",
            publicKey =
                java.security.KeyPairGenerator
                    .getInstance("EC")
                    .apply { initialize(256) }
                    .generateKeyPair()
                    .public,
            aaguid = ByteArray(16),
            credentialId = ByteArray(32) { 0x31 },
            derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
        )

    private fun assertCredentialErased(credential: PasskeyCredential) {
        listOf(credential.rpId, credential.userId, credential.userName, credential.userDisplayName).forEach { owner ->
            val copy = owner.copyCharacters()
            try {
                assertContentEquals(CharArray(copy.size), copy)
            } finally {
                copy.fill('\u0000')
            }
        }
    }

    private fun assertDecodedOwnersErased(owners: List<SensitiveCredentialText>) {
        owners.forEach { owner ->
            val copy = owner.copyCharacters()
            try {
                assertTrue(copy.all { it == '\u0000' })
            } finally {
                copy.fill('\u0000')
            }
        }
    }

    private fun validRequest(algorithms: List<Any>): Map<String, Any> =
        mapOf(
            "1" to ByteArray(32),
            "2" to mapOf("id" to "example.com", "name" to "Example"),
            "3" to mapOf("id" to byteArrayOf(1), "name" to "user", "displayName" to "User"),
            "4" to algorithms.map { mapOf("alg" to it) },
        )
}
