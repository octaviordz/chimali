package com.chimali.fido2.data.eventsourcing

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.chimali.core.domain.eventsourcing.EventKind
import com.chimali.core.domain.eventsourcing.Snapshot
import com.chimali.core.domain.eventsourcing.passkey.PasskeyEvent
import com.chimali.core.domain.eventsourcing.passkey.PasskeyState
import com.chimali.core.security.api.EncryptionManager
import com.chimali.core.security.api.EventStoreKeyProvider
import com.chimali.fido2.data.database.Fido2Database
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayInputStream
import java.nio.charset.MalformedInputException
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.json.jsonObject

@OptIn(ExperimentalSerializationApi::class)
class PasskeyEventStorageOwnershipTest {
    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `mutable event JSON preserves escaped Unicode and long string fields`() {
        val rpId = "synthetic.example"
        val userId = "user-\"\\-雪-🗝"
        val userName = "line\none\tand\u0001-control"
        val userDisplayName = "長い表示名-".repeat(80)
        val event =
            PasskeyEvent.Registered(
                aggregateId = "mutable-json-wire",
                sequenceNumber = 7,
                timestamp = Clock.System.now(),
                rpId = rpId,
                userId = userId,
                userName = userName,
                userDisplayName = userDisplayName,
                credentialId = ByteArray(16) { it.toByte() },
                publicKey = "synthetic-public-key",
                aaguid = "synthetic-aaguid",
                signCount = 3,
                coseAlgorithm = -7,
                derivationProfile = "chimali-local-hdk-p256-v1",
            )
        val sensitiveFields =
            linkedMapOf(
                "rpId" to event.rpId,
                "userId" to event.userId,
                "userName" to event.userName,
                "userDisplayName" to event.userDisplayName,
            )
        val encoded =
            SensitiveJsonFields.encode(sensitiveFields) { output ->
                Json.encodeToStream(
                    PasskeyEvent.serializer(),
                    event.copy(
                        rpId = charArrayOf(),
                        userId = charArrayOf(),
                        userName = charArrayOf(),
                        userDisplayName = charArrayOf(),
                    ),
                    output,
                )
            }
        var restored: PasskeyEvent.Registered? = null
        try {
            val expected = Json.encodeToString(PasskeyEvent.serializer(), event)
            val restoredEvent =
                SensitiveJsonFields.decode(
                    input = encoded,
                    fieldNames = setOf("rpId", "userId", "userName", "userDisplayName"),
                    deserializeSanitized = { bytes ->
                        Json.decodeFromStream(PasskeyEvent.serializer(), ByteArrayInputStream(bytes))
                    },
                    attachFields = { decoded, fields ->
                        (decoded as PasskeyEvent.Registered)
                            .copy(
                                rpId = requireNotNull(fields["rpId"]),
                                userId = requireNotNull(fields["userId"]),
                                userName = requireNotNull(fields["userName"]),
                                userDisplayName = requireNotNull(fields["userDisplayName"]),
                            ).also { restored = it }
                    },
                )
            assertEquals(expected, encoded.decodeToString())
            assertEquals(event, restoredEvent)
        } finally {
            encoded.fill(0)
            event.eraseSensitiveMetadata()
            restored?.eraseSensitiveMetadata()
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `mutable JSON decode erases extracted fields when reconstruction fails`() {
        val marker = "synthetic-sensitive-parse-marker-雪"
        val json = """{"rpId":"$marker","userId":"synthetic-user","userName":"","userDisplayName":""}"""
        var retainedOwners: List<CharArray> = emptyList()

        assertFailsWith<IllegalStateException> {
            SensitiveJsonFields.decode(
                input = json.encodeToByteArray(),
                fieldNames = setOf("rpId", "userId", "userName", "userDisplayName"),
                deserializeSanitized = { sanitized ->
                    assertFalse(sanitized.decodeToString().contains(marker))
                    sanitized.copyOf()
                },
                attachFields = { decoded, fields ->
                    retainedOwners = fields.values.toList()
                    decoded.fill(0)
                    error("synthetic reconstruction failure")
                },
            )
        }
        assertEquals(4, retainedOwners.size)
        retainedOwners.forEach { owner -> assertContentEquals(CharArray(owner.size), owner) }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `mutable JSON extraction is limited to the exact state path`() {
        val input =
            """{"extension":{"rpId":"attacker-before"},
                "state":{"rpId":"real.example","userId":"user","userName":"name","userDisplayName":"display"},
                "tail":{"rpId":"attacker-after"}}""".encodeToByteArray()
        val decoded =
            SensitiveJsonFields.decode(
                input = input,
                fieldNames = setOf("rpId", "userId", "userName", "userDisplayName"),
                fieldObjectPath = listOf("state"),
                deserializeSanitized = { sanitized ->
                    val sanitizedText = sanitized.decodeToString()
                    assertTrue(!sanitizedText.contains("real.example"))
                    assertTrue(sanitizedText.contains("attacker-before"))
                    assertTrue(sanitizedText.contains("attacker-after"))
                    emptyMap<String, CharArray>()
                },
                attachFields = { _, fields -> fields.toMap() },
            )

        try {
            assertEquals("real.example", decoded.getValue("rpId").concatToString())
            assertEquals("user", decoded.getValue("userId").concatToString())
            assertTrue(decoded.getValue("rpId").concatToString() != "attacker-before")
            assertTrue(decoded.getValue("rpId").concatToString() != "attacker-after")
        } finally {
            decoded.values.forEach { it.fill('\u0000') }
            input.fill(0)
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `snapshot repository ignores unknown nested sensitive names before and after state fields`() {
        val state =
            PasskeyState(
                id = "snapshot-path",
                rpId = "real.example".toCharArray(),
                userId = "synthetic-user-id".toCharArray(),
                userName = "synthetic-user-name".toCharArray(),
                userDisplayName = "Synthetic User".toCharArray(),
                credentialId = ByteArray(16) { it.toByte() },
                publicKey = "synthetic-public-key",
                aaguid = "synthetic-aaguid",
                sequenceNumber = 7,
                coseAlgorithm = -7,
                derivationProfile = "chimali-local-hdk-p256-v1",
            )
        val snapshot = Snapshot("snapshot-path", 7, state, Clock.System.now())
        val encoded = Json.encodeToString(Snapshot.serializer(PasskeyState.serializer()), snapshot)
        val snapshotObject = Json.parseToJsonElement(encoded).jsonObject
        val stateObject = snapshotObject.getValue("state").jsonObject
        val stateWithNestedUnknowns =
            linkedMapOf<String, JsonElement>(
                "extensionBefore" to JsonObject(mapOf("rpId" to JsonPrimitive("attacker-before"))),
            ).apply {
                putAll(stateObject)
                put("extensionAfter", JsonObject(mapOf("rpId" to JsonPrimitive("attacker-after"))))
            }
        val tamperedSnapshot =
            JsonObject(snapshotObject.toMutableMap().apply { put("state", JsonObject(stateWithNestedUnknowns)) })
                .toString()
                .encodeToByteArray()
        val missingFieldSnapshot =
            JsonObject(
                snapshotObject.toMutableMap().apply {
                    put("state", JsonObject(stateObject.toMutableMap().apply { remove("rpId") }))
                },
            ).toString().encodeToByteArray()
        val repository = PasskeySnapshotRepositoryImpl(mockk(relaxed = true), mockk(), mockk())

        val decoded: Snapshot<PasskeyState> = repository.decodePasskeySnapshot(EventKind.PASSKEY, tamperedSnapshot)

        try {
            assertContentEquals("real.example".toCharArray(), decoded.state.rpId)
            assertContentEquals("synthetic-user-id".toCharArray(), decoded.state.userId)
            assertContentEquals("synthetic-user-name".toCharArray(), decoded.state.userName)
            assertContentEquals("Synthetic User".toCharArray(), decoded.state.userDisplayName)
            assertFailsWith<IllegalArgumentException> {
                repository.decodePasskeySnapshot<PasskeyState>(EventKind.PASSKEY, missingFieldSnapshot)
            }
        } finally {
            decoded.state.eraseSensitiveMetadata()
            state.eraseSensitiveMetadata()
            encoded.encodeToByteArray().fill(0)
            tamperedSnapshot.fill(0)
            missingFieldSnapshot.fill(0)
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `mutable JSON rejects duplicate selected fields and selected fields with non-string values`() {
        val duplicate = """{"rpId":"first","rpId":"second"}""".encodeToByteArray()
        val wrongType = """{"rpId":{"nested":"value"}}""".encodeToByteArray()

        assertFailsWith<IllegalArgumentException> {
            decodeSensitiveJson(duplicate, emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            decodeSensitiveJson(wrongType, emptyList())
        }
        assertFailsWith<MalformedInputException> {
            decodeSensitiveJson(byteArrayOf(0x80.toByte()), emptyList())
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `mutable JSON parser rejects excessive nesting and oversized documents`() {
        val nested = ("{".repeat(66) + "\"value\":null" + "}".repeat(66)).encodeToByteArray()
        val oversized = ByteArray(1_048_577)

        assertFailsWith<IllegalArgumentException> { decodeSensitiveJson(nested, emptyList()) }
        assertFailsWith<IllegalArgumentException> { decodeSensitiveJson(oversized, emptyList()) }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `storage result contains malformed UTF-8 failures and preserves cancellation`() =
        runTest {
            val malformedUtf8 = MalformedInputException(1)
            val contained = captureStorageResult<Unit> { throw malformedUtf8 }
            assertSame(malformedUtf8, contained.exceptionOrNull())

            val cancellation = CancellationException("synthetic cancellation")
            assertSame(
                cancellation,
                assertFailsWith<CancellationException> { captureStorageResult<Unit> { throw cancellation } },
            )
        }

    private fun decodeSensitiveJson(
        input: ByteArray,
        path: List<String>,
    ): Map<String, CharArray> =
        SensitiveJsonFields.decode(
            input = input,
            fieldNames = setOf("rpId"),
            fieldObjectPath = path,
            deserializeSanitized = { emptyMap<String, CharArray>() },
            attachFields = { _, fields -> fields.toMap() },
        )

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `mutable JSON decode erases extracted fields when ownership transfer is cancelled`() {
        val marker = "synthetic-cancelled-owner-marker"
        val json = """{"rpId":"$marker","userId":"synthetic-user","userName":"","userDisplayName":""}"""
        val cancellation = CancellationException("synthetic decode cancellation")
        var retainedOwners: List<CharArray> = emptyList()

        assertFailsWith<CancellationException> {
            SensitiveJsonFields.decode(
                input = json.encodeToByteArray(),
                fieldNames = setOf("rpId", "userId", "userName", "userDisplayName"),
                deserializeSanitized = { it.copyOf() },
                attachFields = { decoded, fields ->
                    retainedOwners = fields.values.toList()
                    decoded.fill(0)
                    throw cancellation
                },
            )
        }
        assertEquals(4, retainedOwners.size)
        retainedOwners.forEach { owner -> assertContentEquals(CharArray(owner.size), owner) }
    }

    /** @see NFR-SEC-041 */
    @Test
    fun `sensitive event owners preserve the established JSON string wire fields`() {
        val event = testEvent("wire-format-aggregate")

        val json = Json.encodeToString(PasskeyEvent.serializer(), event)

        assertContains(json, "\"rpId\":\"example.com\"")
        assertContains(json, "\"userId\":\"user-1\"")
        assertContains(json, "\"userName\":\"user\"")
        assertContains(json, "\"userDisplayName\":\"User\"")
        val restored = Json.decodeFromString(PasskeyEvent.serializer(), json) as PasskeyEvent.Registered
        assertContentEquals(event.rpId, restored.rpId)
        assertContentEquals(event.userId, restored.userId)
        assertContentEquals(event.userName, restored.userName)
        assertContentEquals(event.userDisplayName, restored.userDisplayName)
        assertTrue(event.toString().contains("rpId=[redacted]"))
        assertTrue(!event.toString().contains("example.com"))
        event.eraseSensitiveMetadata()
        restored.eraseSensitiveMetadata()
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `serializer growth erases every replaced plaintext buffer`() {
        val buffer = ErasableJsonBuffer(initialCapacity = 4)
        // Retain the real owned arrays to verify replacement, not just the final owner state.
        val field = ErasableJsonBuffer::class.java.getDeclaredField("bytes").apply { isAccessible = true }
        buffer.write(byteArrayOf(1, 2, 3))
        val first = field.get(buffer) as ByteArray
        buffer.write(ByteArray(300) { 7 })
        val second = field.get(buffer) as ByteArray
        buffer.write(ByteArray(900) { 9 })
        assertContentEquals(ByteArray(first.size), first)
        assertContentEquals(ByteArray(second.size), second)
        assertEquals(1203, buffer.copyToOwnedBytes().size)
        buffer.erase()
        assertTrue(buffer.isErased())
    }

    /** @see NFR-SEC-041 */
    @Test
    fun `erasable JSON serializer buffer is wiped after success and serialization failure`() {
        lateinit var successfulBuffer: ErasableJsonBuffer
        val encoded =
            encodeToErasableJsonBytes { output ->
                successfulBuffer = output as ErasableJsonBuffer
                output.write("synthetic-sensitive-json".encodeToByteArray())
            }
        assertContentEquals("synthetic-sensitive-json".encodeToByteArray(), encoded)
        assertTrue(successfulBuffer.isErased())

        lateinit var failedBuffer: ErasableJsonBuffer
        assertFailsWith<IllegalStateException> {
            encodeToErasableJsonBytes { output ->
                failedBuffer = output as ErasableJsonBuffer
                output.write("partial-sensitive-json".encodeToByteArray())
                error("synthetic serializer failure")
            }
        }
        assertTrue(failedBuffer.isErased())
        encoded.fill(0)
    }

    /** @see NFR-SEC-041 */
    @Test
    fun `event append failure erases owned key and plaintext`() =
        runTest {
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            try {
                Fido2Database.Schema.create(driver)
                val database = Fido2Database(driver)
                val provider =
                    mockk<EventStoreKeyProvider> {
                        coEvery { getEventStoreKey(any()) } returns ByteArray(32) { 7 }
                    }
                val encryptionManager = mockk<EncryptionManager>()
                lateinit var plaintext: ByteArray
                lateinit var key: ByteArray
                every { encryptionManager.encrypt(any(), any()) } answers {
                    plaintext = firstArg()
                    key = secondArg()
                    error("synthetic encrypt failure")
                }
                val repository = PasskeyEventStoreRepositoryImpl(database, encryptionManager, provider)

                val result = repository.append(EventKind.PASSKEY, listOf(testEvent("failure-aggregate")))

                assertTrue(result.isFailure)
                assertContentEquals(ByteArray(plaintext.size), plaintext)
                assertContentEquals(ByteArray(32), key)
                assertTrue(
                    database.fido2DatabaseQueries
                        .get_events("failure-aggregate", "9999-12-31T23:59:59Z")
                        .executeAsList()
                        .isEmpty(),
                )
            } finally {
                driver.close()
            }
        }

    /** @see NFR-SEC-041 */
    @Test
    fun `cancelled event append rolls back and erases owned key and buffers`() =
        runTest {
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            try {
                Fido2Database.Schema.create(driver)
                val database = Fido2Database(driver)
                val provider =
                    mockk<EventStoreKeyProvider> {
                        coEvery { getEventStoreKey(any()) } returns ByteArray(32) { 9 }
                    }
                val encryptionManager = mockk<EncryptionManager>()
                lateinit var operationJob: Job
                lateinit var plaintext: ByteArray
                lateinit var key: ByteArray
                every { encryptionManager.encrypt(any(), any()) } answers {
                    plaintext = firstArg()
                    key = secondArg()
                    operationJob.cancel()
                    plaintext.copyOf()
                }
                val repository = PasskeyEventStoreRepositoryImpl(database, encryptionManager, provider)
                val operation =
                    launch {
                        operationJob = currentCoroutineContext()[Job]!!
                        repository.append(EventKind.PASSKEY, listOf(testEvent("cancelled-aggregate")))
                    }

                operation.join()

                assertTrue(operation.isCancelled)
                assertContentEquals(ByteArray(plaintext.size), plaintext)
                assertContentEquals(ByteArray(32), key)
                assertTrue(
                    database.fido2DatabaseQueries
                        .get_events("cancelled-aggregate", "9999-12-31T23:59:59Z")
                        .executeAsList()
                        .isEmpty(),
                )
            } finally {
                driver.close()
            }
        }

    /** @see NFR-SEC-041 */
    @Test
    fun `event append and replay erase owned key and plaintext buffers`() =
        runTest {
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            try {
                Fido2Database.Schema.create(driver)
                val database = Fido2Database(driver)
                val providerKey = ByteArray(32) { it.toByte() }
                val provider =
                    mockk<EventStoreKeyProvider> {
                        coEvery { getEventStoreKey(any()) } answers { providerKey.copyOf() }
                    }
                val encryptionManager = mockk<EncryptionManager>()
                val encryptInputs = mutableListOf<ByteArray>()
                val serializedSnapshots = mutableListOf<ByteArray>()
                val encryptKeys = mutableListOf<ByteArray>()
                val decryptOutputs = mutableListOf<ByteArray>()
                val decryptKeys = mutableListOf<ByteArray>()
                every { encryptionManager.encrypt(any(), any()) } answers {
                    val plaintext = firstArg<ByteArray>()
                    encryptInputs += plaintext
                    serializedSnapshots += plaintext.copyOf()
                    plaintext.copyOf().also { encryptKeys += secondArg<ByteArray>() }
                }
                every { encryptionManager.decrypt(any(), any()) } answers {
                    firstArg<ByteArray>().copyOf().also(decryptOutputs::add).also {
                        decryptKeys +=
                            secondArg<ByteArray>()
                    }
                }
                val repository = PasskeyEventStoreRepositoryImpl(database, encryptionManager, provider)
                val event =
                    PasskeyEvent.Registered(
                        aggregateId = "credential-aggregate",
                        sequenceNumber = 1,
                        timestamp = Clock.System.now(),
                        rpId = "example.com",
                        userId = "user-1",
                        userName = "user",
                        userDisplayName = "User",
                        credentialId = ByteArray(16) { 0x42 },
                        publicKey = Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3)),
                        aaguid = "synthetic-aaguid",
                        signCount = 0,
                        coseAlgorithm = -7,
                        derivationProfile = "chimali-local-hdk-p256-v1",
                    )
                val secondEvent = event.copy(sequenceNumber = 2, userName = "second-user".toCharArray())

                assertTrue(repository.append(EventKind.PASSKEY, listOf(event, secondEvent)).isSuccess)
                assertEquals(2, serializedSnapshots.size)
                serializedSnapshots.forEach { assertContains(it.decodeToString(), "\"type\":") }
                assertContentEquals(providerKey, ByteArray(32) { it.toByte() })
                encryptInputs.forEach { assertContentEquals(ByteArray(it.size), it) }
                encryptKeys.forEach { assertContentEquals(ByteArray(32), it) }

                val replayed = repository.getEvents(EventKind.PASSKEY, event.aggregateId).getOrThrow()

                assertEquals(listOf(event, secondEvent), replayed)
                decryptOutputs.forEach { assertContentEquals(ByteArray(it.size), it) }
                decryptKeys.forEach { assertContentEquals(ByteArray(32), it) }
                serializedSnapshots.forEach { it.fill(0) }
                secondEvent.eraseSensitiveMetadata()
            } finally {
                driver.close()
            }
        }

    /** @see NFR-SEC-041 */
    @Test
    fun `snapshot save and restore erase owned key and plaintext buffers`() =
        runTest {
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            try {
                Fido2Database.Schema.create(driver)
                val database = Fido2Database(driver)
                val providerKey = ByteArray(32) { (it + 1).toByte() }
                val provider =
                    mockk<EventStoreKeyProvider> {
                        coEvery { getEventStoreKey(any()) } answers { providerKey.copyOf() }
                    }
                val encryptionManager = mockk<EncryptionManager>()
                val encryptInputs = mutableListOf<ByteArray>()
                val encryptKeys = mutableListOf<ByteArray>()
                val decryptOutputs = mutableListOf<ByteArray>()
                val decryptKeys = mutableListOf<ByteArray>()
                every { encryptionManager.encrypt(any(), any()) } answers {
                    firstArg<ByteArray>()
                        .also(
                            encryptInputs::add,
                        ).copyOf()
                        .also { encryptKeys += secondArg<ByteArray>() }
                }
                every { encryptionManager.decrypt(any(), any()) } answers {
                    firstArg<ByteArray>().copyOf().also(decryptOutputs::add).also {
                        decryptKeys +=
                            secondArg<ByteArray>()
                    }
                }
                val repository = PasskeySnapshotRepositoryImpl(database, encryptionManager, provider)
                val snapshot =
                    Snapshot(
                        aggregateId = "credential-aggregate",
                        sequenceNumber = 5,
                        state =
                            PasskeyState(
                                id = "credential-aggregate",
                                rpId = "snapshot.example".toCharArray(),
                                userId = "snapshot-user-雪".toCharArray(),
                                userName = "snapshot-\"name\"".toCharArray(),
                                userDisplayName = "Snapshot User".toCharArray(),
                                publicKey = "synthetic-public-key",
                                coseAlgorithm = -7,
                                derivationProfile = "chimali-local-hdk-p256-v1",
                            ),
                        timestamp = Clock.System.now(),
                    )

                assertTrue(repository.save(EventKind.PASSKEY, snapshot).isSuccess)
                encryptInputs.forEach { assertContentEquals(ByteArray(it.size), it) }
                encryptKeys.forEach { assertContentEquals(ByteArray(32), it) }

                val restored = repository.getLatest<PasskeyState>(EventKind.PASSKEY, snapshot.aggregateId).getOrThrow()

                assertEquals(snapshot, restored)
                decryptOutputs.forEach { assertContentEquals(ByteArray(it.size), it) }
                decryptKeys.forEach { assertContentEquals(ByteArray(32), it) }
                snapshot.state.eraseSensitiveMetadata()
                restored?.state?.eraseSensitiveMetadata()
            } finally {
                driver.close()
            }
        }

    private fun testEvent(aggregateId: String): PasskeyEvent.Registered =
        PasskeyEvent.Registered(
            aggregateId = aggregateId,
            sequenceNumber = 1,
            timestamp = Clock.System.now(),
            rpId = "example.com",
            userId = "user-1",
            userName = "user",
            userDisplayName = "User",
            credentialId = ByteArray(16) { 0x42 },
            publicKey = Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3)),
            aaguid = "synthetic-aaguid",
            signCount = 0,
            coseAlgorithm = -7,
            derivationProfile = "chimali-local-hdk-p256-v1",
        )
}
