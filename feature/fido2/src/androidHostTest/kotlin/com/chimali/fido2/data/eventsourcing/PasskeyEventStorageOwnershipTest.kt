package com.chimali.fido2.data.eventsourcing

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.chimali.core.domain.eventsourcing.DomainEvent
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
import kotlin.test.assertNull
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
@Suppress("LargeClass")
class PasskeyEventStorageOwnershipTest {
    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `sensitive JSON capacity overflow erases the sanitized serialization copy`() {
        val sanitized = "{\"credentialId\":\"synthetic-owner\"}".encodeToByteArray()
        val repeatedValues =
            object : AbstractCollection<CharArray>() {
                override val size: Int = 360_000

                override fun iterator(): Iterator<CharArray> =
                    object : Iterator<CharArray> {
                        private var remaining = size
                        private val repeatedOwner = CharArray(1_000)

                        override fun hasNext(): Boolean = remaining > 0

                        override fun next(): CharArray {
                            if (!hasNext()) throw NoSuchElementException()
                            remaining--
                            return repeatedOwner
                        }
                    }
            }
        val fields =
            object : Map<String, CharArray> {
                override val size: Int get() = 0

                override val keys: Set<String> get() = emptySet()

                override val values: Collection<CharArray> get() = repeatedValues

                override val entries: Set<Map.Entry<String, CharArray>> get() = emptySet()

                override fun isEmpty(): Boolean = true

                override fun containsKey(key: String): Boolean = false

                override fun containsValue(value: CharArray): Boolean = false

                override fun get(key: String): CharArray? = null
            }

        assertFailsWith<ArithmeticException> {
            SensitiveJsonFields.encodeSanitized(fields, sanitized)
        }
        assertContentEquals(ByteArray(sanitized.size), sanitized)
    }

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
    fun `passkey decoder erases decoded credential ID when sensitive field attachment fails`() {
        val decodedEvent = testEvent("decode-attach-failure")
        val repository =
            PasskeyEventStoreRepositoryImpl(
                mockk<Fido2Database>(),
                mockk<EncryptionManager>(),
                mockk<EventStoreKeyProvider>(),
            )
        val plaintext = """{"rpId":"rp","userId":"user","userDisplayName":"display"}""".encodeToByteArray()

        try {
            assertFailsWith<IllegalArgumentException> {
                repository.decodePasskeyEventWithDecoder(plaintext) { decodedEvent }
            }

            assertContentEquals(ByteArray(decodedEvent.credentialId.size), decodedEvent.credentialId)
            listOf(decodedEvent.rpId, decodedEvent.userId, decodedEvent.userName, decodedEvent.userDisplayName)
                .forEach { assertContentEquals(CharArray(it.size), it) }
        } finally {
            plaintext.fill(0)
            decodedEvent.eraseSensitiveMetadata()
        }
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
    fun `mutable JSON extraction follows every nested path component`() {
        val input =
            """{"root":{"other":{"rpId":"attacker-before"},
                "state":{"rpId":"real.example"}},
                "tail":{"rpId":"attacker-after"}}""".encodeToByteArray()
        val decoded = decodeSensitiveJson(input, listOf("root", "state"))

        try {
            assertEquals("real.example", decoded.getValue("rpId").concatToString())
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
    fun `snapshot decoder erases decoded credential ID when sensitive field attachment fails`() {
        val decodedState =
            PasskeyState(
                id = "snapshot-decode-attach-failure",
                rpId = "decoded.example".toCharArray(),
                userId = "decoded-user".toCharArray(),
                userName = "decoded-name".toCharArray(),
                userDisplayName = "Decoded User".toCharArray(),
                credentialId = ByteArray(16) { (it + 1).toByte() },
                publicKey = "synthetic-key",
                sequenceNumber = 1,
                coseAlgorithm = -7,
                derivationProfile = "chimali-local-hdk-p256-v1",
            )
        val decodedSnapshot = Snapshot("snapshot-decode-attach-failure", 1, decodedState, Clock.System.now())
        val repository = PasskeySnapshotRepositoryImpl(mockk(relaxed = true), mockk(), mockk())
        val plaintext =
            """{"state":{"rpId":"rp","userId":"user","userDisplayName":"display"}}"""
                .encodeToByteArray()

        try {
            assertFailsWith<IllegalArgumentException> {
                repository.decodePasskeySnapshotWithDecoder(plaintext) { decodedSnapshot }
            }

            assertContentEquals(ByteArray(decodedState.credentialId.size), decodedState.credentialId)
            listOf(decodedState.rpId, decodedState.userId, decodedState.userName, decodedState.userDisplayName)
                .forEach { assertContentEquals(CharArray(it.size), it) }
        } finally {
            plaintext.fill(0)
            decodedState.eraseSensitiveMetadata()
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `storage result sanitizes malformed UTF-8 failures and preserves cancellation`() =
        runTest {
            val markerFailure = IllegalStateException("synthetic-storage-failure-marker")
            val contained = captureStorageResult<Unit> { throw markerFailure }
            assertEquals("Passkey storage operation failed", contained.exceptionOrNull()?.message)
            assertNull(contained.exceptionOrNull()?.cause)
            assertFalse(contained.exceptionOrNull().toString().contains("synthetic-storage-failure-marker"))

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
                val eventRowPayloads = mutableListOf<ByteArray>()
                repository.afterEventRowsReadForTest = { eventRowPayloads.addAll(it) }
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
                assertEquals(2, eventRowPayloads.size)
                eventRowPayloads.forEach { assertContentEquals(ByteArray(it.size), it) }
                assertEquals(
                    2,
                    database.fido2DatabaseQueries
                        .get_events(event.aggregateId, "9999-12-31T23:59:59Z")
                        .executeAsList()
                        .size,
                )
                decryptOutputs.forEach { assertContentEquals(ByteArray(it.size), it) }
                decryptKeys.forEach { assertContentEquals(ByteArray(32), it) }
                replayed
                    .filterIsInstance<PasskeyEvent.Registered>()
                    .forEach(PasskeyEvent.Registered::eraseSensitiveMetadata)

                eventRowPayloads.clear()
                val tail = repository.getEventsFrom(EventKind.PASSKEY, event.aggregateId, 1).getOrThrow()
                assertEquals(listOf(secondEvent), tail)
                assertEquals(1, eventRowPayloads.size)
                eventRowPayloads.forEach { assertContentEquals(ByteArray(it.size), it) }
                tail
                    .filterIsInstance<PasskeyEvent.Registered>()
                    .forEach(PasskeyEvent.Registered::eraseSensitiveMetadata)

                val stagedFullReplayOwners = mutableListOf<DomainEvent>()
                repository.beforeEventBatchTransferForTest = { staged ->
                    stagedFullReplayOwners.addAll(staged)
                    throw CancellationException("synthetic full-replay cancellation")
                }
                eventRowPayloads.clear()
                assertFailsWith<CancellationException> {
                    repository.getEvents(EventKind.PASSKEY, event.aggregateId)
                }
                assertEquals(2, eventRowPayloads.size)
                eventRowPayloads.forEach { assertContentEquals(ByteArray(it.size), it) }
                stagedFullReplayOwners.filterIsInstance<PasskeyEvent.Registered>().forEach { staged ->
                    assertContentEquals(CharArray(staged.userName.size), staged.userName)
                    staged.eraseSensitiveMetadata()
                }

                val stagedTailOwners = mutableListOf<DomainEvent>()
                repository.beforeEventBatchTransferForTest = { staged ->
                    stagedTailOwners.addAll(staged)
                    throw CancellationException("synthetic snapshot-tail cancellation")
                }
                eventRowPayloads.clear()
                assertFailsWith<CancellationException> {
                    repository.getEventsFrom(EventKind.PASSKEY, event.aggregateId, 1)
                }
                assertEquals(1, eventRowPayloads.size)
                eventRowPayloads.forEach { assertContentEquals(ByteArray(it.size), it) }
                stagedTailOwners.filterIsInstance<PasskeyEvent.Registered>().forEach { staged ->
                    assertContentEquals(CharArray(staged.userName.size), staged.userName)
                    staged.eraseSensitiveMetadata()
                }
                repository.beforeEventBatchTransferForTest = null
                eventRowPayloads.clear()
                val persistedReplay = repository.getEvents(EventKind.PASSKEY, event.aggregateId).getOrThrow()
                assertEquals(listOf(event, secondEvent), persistedReplay)
                assertEquals(2, eventRowPayloads.size)
                eventRowPayloads.forEach { assertContentEquals(ByteArray(it.size), it) }
                persistedReplay
                    .filterIsInstance<PasskeyEvent.Registered>()
                    .forEach(PasskeyEvent.Registered::eraseSensitiveMetadata)
                assertEquals(
                    2,
                    database.fido2DatabaseQueries
                        .get_events(event.aggregateId, "9999-12-31T23:59:59Z")
                        .executeAsList()
                        .size,
                )
                serializedSnapshots.forEach { it.fill(0) }
                event.eraseSensitiveMetadata()
                secondEvent.eraseSensitiveMetadata()
            } finally {
                driver.close()
            }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `event replay erases staged sensitive events when batch transfer fails`() =
        runTest {
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            val key = ByteArray(32) { 0x31.toByte() }
            try {
                Fido2Database.Schema.create(driver)
                val database = Fido2Database(driver)
                val provider =
                    mockk<EventStoreKeyProvider> {
                        coEvery { getEventStoreKey(any()) } returns key.copyOf()
                    }
                val encryptionManager = mockk<EncryptionManager>()
                val decryptedRowPayloads = mutableListOf<ByteArray>()
                every { encryptionManager.encrypt(any(), any()) } answers { firstArg<ByteArray>().copyOf() }
                every { encryptionManager.decrypt(any(), any()) } answers {
                    firstArg<ByteArray>().also(decryptedRowPayloads::add).copyOf()
                }
                val repository = PasskeyEventStoreRepositoryImpl(database, encryptionManager, provider)
                val aggregateId = "event-batch-transfer-failure"
                val stored = testEvent(aggregateId)
                val staged = testEvent(aggregateId)
                try {
                    assertTrue(repository.append(EventKind.PASSKEY, listOf(stored)).isSuccess)
                    repository.beforeEventBatchTransferForTest = { events ->
                        assertEquals(1, events.size)
                        assertSame(staged, events.single())
                        error("synthetic event batch transfer failure")
                    }

                    val result = repository.getEventsWithDecoder(EventKind.PASSKEY, aggregateId, null) { staged }

                    assertTrue(result.isFailure)
                    assertContentEquals(ByteArray(staged.credentialId.size), staged.credentialId)
                    listOf(staged.rpId, staged.userId, staged.userName, staged.userDisplayName).forEach { owner ->
                        assertContentEquals(CharArray(owner.size), owner)
                    }
                    decryptedRowPayloads.forEach { payload -> assertContentEquals(ByteArray(payload.size), payload) }
                    val persistedRows =
                        database.fido2DatabaseQueries
                            .get_events(aggregate_id = aggregateId, timestamp = "9999-12-31T23:59:59Z")
                            .executeAsList()
                    try {
                        assertTrue(persistedRows.single().payload.any { it != 0.toByte() })
                    } finally {
                        persistedRows.forEach { it.payload.fill(0) }
                    }
                } finally {
                    stored.eraseSensitiveMetadata()
                    staged.eraseSensitiveMetadata()
                }
            } finally {
                key.fill(0)
                driver.close()
            }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `event replay from snapshot erases staged sensitive events when batch transfer fails`() =
        runTest {
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            val key = ByteArray(32) { 0x32.toByte() }
            var stored: PasskeyEvent.Registered? = null
            var replayed: PasskeyEvent.Registered? = null
            try {
                Fido2Database.Schema.create(driver)
                val database = Fido2Database(driver)
                val provider =
                    mockk<EventStoreKeyProvider> {
                        coEvery { getEventStoreKey(any()) } returns key.copyOf()
                    }
                val encryptionManager = mockk<EncryptionManager>()
                val decryptedRowPayloads = mutableListOf<ByteArray>()
                every { encryptionManager.encrypt(any(), any()) } answers { firstArg<ByteArray>().copyOf() }
                every { encryptionManager.decrypt(any(), any()) } answers {
                    firstArg<ByteArray>().also(decryptedRowPayloads::add).copyOf()
                }
                val repository = PasskeyEventStoreRepositoryImpl(database, encryptionManager, provider)
                val aggregateId = "event-from-snapshot-transfer-failure"
                stored = testEvent(aggregateId)
                assertTrue(repository.append(EventKind.PASSKEY, listOf(checkNotNull(stored))).isSuccess)
                repository.beforeEventBatchTransferForTest = { events ->
                    assertEquals(1, events.size)
                    replayed = events.single() as PasskeyEvent.Registered
                    error("synthetic snapshot-tail batch transfer failure")
                }

                val result = repository.getEventsFrom(EventKind.PASSKEY, aggregateId, 0)

                assertTrue(result.isFailure)
                val failedTransfer = checkNotNull(replayed)
                assertContentEquals(ByteArray(failedTransfer.credentialId.size), failedTransfer.credentialId)
                listOf(
                    failedTransfer.rpId,
                    failedTransfer.userId,
                    failedTransfer.userName,
                    failedTransfer.userDisplayName,
                ).forEach { owner -> assertContentEquals(CharArray(owner.size), owner) }
                decryptedRowPayloads.forEach { payload -> assertContentEquals(ByteArray(payload.size), payload) }
            } finally {
                stored?.eraseSensitiveMetadata()
                replayed?.eraseSensitiveMetadata()
                key.fill(0)
                driver.close()
            }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `event replay erases every materialized row payload when the first row fails`() =
        runTest {
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            val key = ByteArray(32) { 0x33.toByte() }
            val firstStoredEvent = testEvent("event-row-batch-failure")
            val secondStoredEvent = firstStoredEvent.copy(sequenceNumber = 2)
            val storedEvents = listOf(firstStoredEvent, secondStoredEvent)
            try {
                Fido2Database.Schema.create(driver)
                val database = Fido2Database(driver)
                val provider =
                    mockk<EventStoreKeyProvider> {
                        coEvery { getEventStoreKey(any()) } returns key.copyOf()
                    }
                val encryptionManager = mockk<EncryptionManager>()
                every { encryptionManager.encrypt(any(), any()) } answers { firstArg<ByteArray>().copyOf() }
                every { encryptionManager.decrypt(any(), any()) } answers { firstArg<ByteArray>().copyOf() }
                val repository = PasskeyEventStoreRepositoryImpl(database, encryptionManager, provider)
                val materializedRowPayloads = mutableListOf<ByteArray>()
                repository.afterEventRowsReadForTest = { rows -> materializedRowPayloads.addAll(rows) }
                assertTrue(repository.append(EventKind.PASSKEY, storedEvents).isSuccess)

                val result =
                    repository.getEventsWithDecoder(EventKind.PASSKEY, "event-row-batch-failure", null) {
                        error("synthetic first-row decode failure")
                    }

                assertTrue(result.isFailure)
                assertEquals(2, materializedRowPayloads.size)
                materializedRowPayloads.forEach { payload -> assertContentEquals(ByteArray(payload.size), payload) }
            } finally {
                storedEvents.forEach(PasskeyEvent.Registered::eraseSensitiveMetadata)
                key.fill(0)
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
                lateinit var snapshotRowPayload: ByteArray
                repository.afterSnapshotRowReadForTest = { snapshotRowPayload = it }
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
                assertContentEquals(ByteArray(snapshotRowPayload.size), snapshotRowPayload)
                decryptOutputs.forEach { assertContentEquals(ByteArray(it.size), it) }
                decryptKeys.forEach { assertContentEquals(ByteArray(32), it) }
                snapshot.state.eraseSensitiveMetadata()
                restored?.state?.eraseSensitiveMetadata()
            } finally {
                driver.close()
            }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `snapshot read erases query payload when key acquisition fails`() =
        runTest {
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            try {
                Fido2Database.Schema.create(driver)
                val database = Fido2Database(driver)
                var failKeyAcquisition = false
                val provider =
                    mockk<EventStoreKeyProvider> {
                        coEvery { getEventStoreKey(any()) } answers {
                            if (failKeyAcquisition) error("synthetic key-provider failure")
                            ByteArray(32) { 3 }
                        }
                    }
                val encryptionManager = mockk<EncryptionManager>()
                every { encryptionManager.encrypt(any(), any()) } answers { firstArg<ByteArray>().copyOf() }
                every { encryptionManager.decrypt(any(), any()) } answers { firstArg<ByteArray>().copyOf() }
                val repository = PasskeySnapshotRepositoryImpl(database, encryptionManager, provider)
                val snapshot =
                    Snapshot(
                        aggregateId = "snapshot-payload-cleanup",
                        sequenceNumber = 1,
                        state =
                            PasskeyState(
                                id = "snapshot-payload-cleanup",
                                rpId = "synthetic.example".toCharArray(),
                                userId = "synthetic-user".toCharArray(),
                                userName = "synthetic-name".toCharArray(),
                                userDisplayName = "Synthetic Name".toCharArray(),
                                publicKey = "synthetic-public-key",
                                coseAlgorithm = -7,
                                derivationProfile = "chimali-local-hdk-p256-v1",
                            ),
                        timestamp = Clock.System.now(),
                    )
                assertTrue(repository.save(EventKind.PASSKEY, snapshot).isSuccess)

                lateinit var queryPayload: ByteArray
                repository.afterSnapshotRowReadForTest = { queryPayload = it }
                failKeyAcquisition = true
                val result = repository.getLatest<PasskeyState>(EventKind.PASSKEY, snapshot.aggregateId)

                assertTrue(result.isFailure)
                assertContentEquals(ByteArray(queryPayload.size), queryPayload)

                failKeyAcquisition = false
                lateinit var decodeFailurePayload: ByteArray
                repository.afterSnapshotRowReadForTest = { decodeFailurePayload = it }
                val decodeFailure =
                    repository.getLatestWithDecoder<PasskeyState>(EventKind.PASSKEY, snapshot.aggregateId) {
                        error("synthetic snapshot decode failure")
                    }

                assertTrue(decodeFailure.isFailure)
                assertContentEquals(ByteArray(decodeFailurePayload.size), decodeFailurePayload)

                lateinit var cancelledReadPayload: ByteArray
                var cancelledDecodedSnapshot: Snapshot<PasskeyState>? = null
                lateinit var cancellationJob: Job
                repository.afterSnapshotRowReadForTest = { cancelledReadPayload = it }
                val cancelledRead =
                    launch {
                        cancellationJob = currentCoroutineContext()[Job]!!
                        repository.getLatestWithDecoder<PasskeyState>(EventKind.PASSKEY, snapshot.aggregateId) {
                            Snapshot(
                                aggregateId = snapshot.aggregateId,
                                sequenceNumber = snapshot.sequenceNumber,
                                state =
                                    PasskeyState(
                                        id = snapshot.aggregateId,
                                        rpId = "cancelled.example".toCharArray(),
                                        userId = "cancelled-user".toCharArray(),
                                        userName = "cancelled-name".toCharArray(),
                                        userDisplayName = "Cancelled Name".toCharArray(),
                                        publicKey = "synthetic-public-key",
                                        coseAlgorithm = -7,
                                        derivationProfile = "chimali-local-hdk-p256-v1",
                                    ),
                                timestamp = snapshot.timestamp,
                            ).also { decoded ->
                                cancelledDecodedSnapshot = decoded
                                cancellationJob.cancel(
                                    CancellationException("synthetic snapshot-read cancellation"),
                                )
                            }
                        }
                    }

                cancelledRead.join()
                assertTrue(cancelledRead.isCancelled)
                assertContentEquals(ByteArray(cancelledReadPayload.size), cancelledReadPayload)
                assertContentEquals(
                    CharArray(requireNotNull(cancelledDecodedSnapshot).state.rpId.size),
                    requireNotNull(cancelledDecodedSnapshot).state.rpId,
                )
                snapshot.state.eraseSensitiveMetadata()
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
