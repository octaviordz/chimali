package com.chimali.feature.vault.internal.crypto

import com.chimali.core.common.result.Outcome
import com.chimali.core.security.api.EventStoreKeyProvider
import com.chimali.core.security.impl.AesEncryptionManager
import com.chimali.feature.vault.api.VaultItem
import com.chimali.feature.vault.api.VaultType
import com.chimali.feature.vault.internal.payload.SecureNotePayload
import io.mockk.coEvery
import io.mockk.mockk
import java.nio.charset.CoderResult
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** FR-VAULT-026/035, SC-VAULT-011/012: fixed old-writer fixtures and actual owned buffers. */
class VaultPayloadCodecTest {
    private val codec = VaultPayloadCodec()

    @Test
    fun missingRequiredFieldsAndTruncatedTokensEraseAllPartialData() {
        val invalid =
            listOf(
                "{}",
                """{"title":"secret"}""",
                """{"title":"secret","content":"body","customFields": """,
                """{"title":"secret","content":"body","customFields":[{}]}""",
                """{"title":"secret","content":"body","customFields":[{"name":"n","isConcealed":true}]}""",
                """{"title":"secret","content":"body","customFields":[{"name":"n","value":"v","isConcealed":""",
                """{"title":"secret","content":"body","future":""",
                """{"title":"secret","content":"body","future":tru""",
                """{"title":"secret","content":"body","future":-""",
                """{"title":"secret","content":"body","future":1""",
                """{"title":"secret","content":"body","future":1e""",
                """{"title":"secret","content":"body","future":1e+""",
                """{"title":"secret","content":"body","future":1.""",
                """{"title":"secret","content":"body","future":-x}""",
            )
        invalid.forEach { json ->
            val arrays = mutableListOf<Any>()
            assertThrows(IllegalArgumentException::class.java) {
                VaultPayloadCodec { arrays.add(it) }.decodeSecureNote(json.toByteArray())
            }
            arrays.forEach { assertErased(it) }
        }
    }

    @Test
    fun oversizedExpansionIsRejectedBeforeScratchAllocation() {
        // Aliased synthetic fields exercise the size bound without allocating an enormous input.
        val text = CharArray(1_000_000) { 's' }
        val field =
            com.chimali.feature.vault.internal.payload
                .CustomField(text, text, false)
        val payload = SecureNotePayload("title", charArrayOf(), List(64) { field })
        val allocations = mutableListOf<Any>()
        try {
            assertThrows(IllegalArgumentException::class.java) {
                VaultPayloadCodec { allocations.add(it) }.encode(payload)
            }
            assertTrue(allocations.isEmpty())
            assertEquals('s', text[0]) // Codec borrows the payload; its owner performs terminal cleanup.
        } finally {
            payload.clearMemory()
        }
        assertTrue(text.all { it == '\u0000' })
    }

    @Test
    fun multipleCustomFieldsAndTruncatedContainersCleanEveryPartialField() {
        val prefix = """{"title":"title","content":"body","customFields":["""
        val first = """{"name":"first","value":"one","isConcealed":false}"""
        val second = """{"name":"second","value":"two","isConcealed":true}"""
        val valid = "$prefix$first,$second]}"
        val payload = codec.decodeSecureNote(valid.toByteArray())
        try {
            assertEquals(2, payload.customFields!!.size)
            val encoded = codec.encode(payload)
            try {
                assertEquals(Json.parseToJsonElement(valid), Json.parseToJsonElement(String(encoded)))
            } finally {
                encoded.fill(0)
            }
        } finally {
            payload.clearMemory()
        }
        for (truncated in listOf("$prefix$first,", """{"title":"secret",""")) {
            val arrays = mutableListOf<Any>()
            assertThrows(IllegalArgumentException::class.java) {
                VaultPayloadCodec { arrays.add(it) }.decodeSecureNote(truncated.toByteArray())
            }
            assertTrue(arrays.isNotEmpty())
            arrays.forEach { assertErased(it) }
        }
    }

    @Test
    fun nullEmptyDuplicateAndNestedFutureFieldsPreserveLegacySemantics() {
        val cases =
            listOf(
                """{"title":"t","content":"body","customFields":[]} """,
                """{"title":"old","title":"t","content":"body","customFields":null}""",
                """{"title":"t","content":"body","customFields":[],"customFields":null}""",
                """{"title":"t","content":"body","future":[{},[],{"a":0,"b":["x",true,false,null,1.2e-3]}]}""",
            )
        cases.forEach { json ->
            val p = codec.decodeSecureNote(json.toByteArray())
            try {
                assertEquals("t", String(p.title))
                assertEquals("body", String(p.content))
                val encoded = codec.encode(p)
                try {
                    assertEquals(
                        "body",
                        (Json.parseToJsonElement(String(encoded)) as JsonObject)["content"]?.let {
                            (it as JsonPrimitive).content
                        },
                    )
                } finally {
                    encoded.fill(0)
                }
            } finally {
                p.clearMemory()
            }
        }
        val duplicate =
            """{
            "title":"t","content":"body",
            "customFields":[{"name":"old","name":"new","value":"old","value":"new","isConcealed":false,"future":{}}]
        }""".toByteArray()
        val arrays = mutableListOf<Any>()
        val p = VaultPayloadCodec { arrays.add(it) }.decodeSecureNote(duplicate)
        try {
            assertEquals("new", String(p.customFields!!.single().name))
            assertEquals("new", String(p.customFields.single().value))
            val encoded = codec.encode(p)
            encoded.fill(0)
        } finally {
            p.clearMemory()
        }
        arrays.forEach { assertErased(it) }
    }

    @Test
    fun controlCharactersAndUnpairedSurrogatesMatchLegacyUtf8Replacement() {
        val p = SecureNotePayload("title", CharArray(32) { it.toChar() })
        val encoded = codec.encode(p)
        val reopened = codec.decodeSecureNote(encoded)
        try {
            assertArrayEquals(p.content, reopened.content)
        } finally {
            encoded.fill(0)
            reopened.clearMemory()
            p.clearMemory()
        }
        val unpaired = SecureNotePayload("title", charArrayOf('\ud800'))
        val bytes = codec.encode(unpaired)
        val read = codec.decodeSecureNote(bytes)
        try {
            assertEquals("?", String(read.content))
        } finally {
            bytes.fill(0)
            read.clearMemory()
            unpaired.clearMemory()
        }
    }

    @Test
    fun malformedJsonGrammarFailsWithoutRetainingPartialSecrets() {
        val invalidValues =
            listOf(
                "",
                "[",
                "{",
                "tru",
                "fals",
                "nul",
                "-",
                "- 1",
                "01",
                ".1",
                "1.",
                "1e",
                "1e+",
                "[1,]",
                "{\"a\":}",
                "{\"a\":1,}",
                "{\"a\" 1}",
                "\"\\uXYZW\"",
                "\"\\u0\"",
                "\"\\x\"",
                "\"raw\nnewline\"",
                "\"unterminated",
            )
        invalidValues.forEach { invalid ->
            val arrays = mutableListOf<Any>()
            val json = """{"title":"secret","content":"body","future":$invalid}"""
            assertThrows(IllegalArgumentException::class.java) {
                VaultPayloadCodec { arrays.add(it) }.decodeSecureNote(json.toByteArray())
            }
            arrays.forEach { assertErased(it) }
        }
    }

    private fun fixture(name: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/vault-legacy-v1/$name")).use {
            it.readBytes()
        }

    @Test
    fun fixedLegacyPayloadsReadAndWriteTheSameJsonContract() {
        val password = codec.decodePassword(fixture("password.json"))
        val card = codec.decodeCreditCard(fixture("card.json"))
        val note = codec.decodeSecureNote(fixture("note.json"))
        try {
            assertEquals("secret\t雪🙂", String(password.password))
            assertEquals("quote\"\\\n雪🙂", String(password.customFields!!.single().value))
            assertArrayEquals(charArrayOf(), card.notes)
            assertEquals("line1\nline2\u0000雪🙂", String(note.content))
            listOf(
                "password" to codec.encode(password),
                "card" to codec.encode(card),
                "note" to codec.encode(note),
            ).forEach { (name, encoded) ->
                try {
                    // Frozen old-writer bytes plus an independent JSON parser; not just a new-code round trip.
                    assertArrayEquals(fixture("$name.json"), encoded)
                    assertEquals(
                        Json.parseToJsonElement(String(fixture("$name.json"))),
                        Json.parseToJsonElement(String(encoded)),
                    )
                } finally {
                    encoded.fill(0)
                }
            }
        } finally {
            password.clearMemory()
            card.clearMemory()
            note.clearMemory()
        }
    }

    @Test
    fun realAesReadsCapturedOldCiphertextAndSupportsEditReopen() =
        runBlocking {
            val provider = mockk<EventStoreKeyProvider>()
            coEvery { provider.getEventStoreKey(any()) } answers { ByteArray(32) { it.toByte() } }
            val service = VaultCryptoServiceImpl(AesEncryptionManager(), provider)
            for ((name, type) in listOf(
                "password" to VaultType.PASSWORD,
                "card" to VaultType.CREDIT_CARD,
                "note" to VaultType.NOTE,
            )) {
                val ciphertext = Base64.getDecoder().decode(fixture("$name.aes.base64"))
                val item = VaultItem(UUID(0, 1), type, "title", ciphertext, byteArrayOf(), "", "", null, UUID(0, 2))
                when (type) {
                    VaultType.PASSWORD -> {
                        val p = (service.decryptPassword(item) as Outcome.Success).data
                        p.password[0] = 'X'
                        val saved = (service.encryptPassword(item.id, p, item.identityId) as Outcome.Success).data
                        val reopened = (service.decryptPassword(saved) as Outcome.Success).data
                        try {
                            assertEquals('X', reopened.password[0])
                        } finally {
                            reopened.clearMemory()
                        }
                    }
                    VaultType.CREDIT_CARD -> {
                        val p = (service.decryptCreditCard(item) as Outcome.Success).data
                        p.cvv[0] = '9'
                        val saved = (service.encryptCreditCard(item.id, p, item.identityId) as Outcome.Success).data
                        val reopened = (service.decryptCreditCard(saved) as Outcome.Success).data
                        try {
                            assertEquals('9', reopened.cvv[0])
                        } finally {
                            reopened.clearMemory()
                        }
                    }
                    VaultType.NOTE -> {
                        val p = (service.decryptSecureNote(item) as Outcome.Success).data
                        p.content[0] = 'X'
                        val saved = (service.encryptSecureNote(item.id, p, item.identityId) as Outcome.Success).data
                        val reopened = (service.decryptSecureNote(saved) as Outcome.Success).data
                        try {
                            assertEquals('X', reopened.content[0])
                        } finally {
                            reopened.clearMemory()
                        }
                    }
                }
            }
        }

    @Test
    fun optionalAndUnknownFieldsAndEscapesAreCompatible() {
        val bytes =
            """{
                "title":"","content":"\b\f\r\t\/\\\"\u0041",
                "future":{"a":[true,false,null,-12.3e+4,{}]},"customFields":null
            }""".toByteArray()
        val p = codec.decodeSecureNote(bytes)
        try {
            assertEquals("\b\u000c\r\t/\\\"A", String(p.content))
        } finally {
            p.clearMemory()
        }
        val minimal = codec.decodeSecureNote("""{"title":"t","content":""}""".toByteArray())
        try {
            assertEquals(null, minimal.customFields)
        } finally {
            minimal.clearMemory()
        }
    }

    @Test
    fun malformedAndPartialObjectsEraseEveryAllocatedArray() {
        val malformed =
            listOf(
                """{"title":"secret","content":"body","customFields":[{"name":"secret","value":"value"}]}""",
                """{"title":"secret","content":"body"} trailing""",
                """{"title":"secret","content":"\q"}""",
                """{"title":"secret","content":null}""",
                """{"title":"secret","content":"body","future":01}""",
                """{"title":"secret","content":"body","future":1e+}""",
                """{"title":"secret","content":"body","future":1.}""",
                """{"title":"secret","content":"body","future":-x}""",
            )
        malformed.forEach { json ->
            val arrays = mutableListOf<Any>()
            val reader = VaultPayloadCodec { arrays.add(it) }
            org.junit.Assert.assertThrows(
                IllegalArgumentException::class.java,
            ) { reader.decodeSecureNote(json.toByteArray()) }
            assertTrue(arrays.isNotEmpty())
            arrays.forEach { assertErased(it) }
        }
    }

    @Test
    fun allocationsAreErasedEvenIfEncodingOrDecodingFailsPartway() {
        val p = codec.decodeSecureNote(fixture("note.json"))
        try {
            val observed = mutableListOf<Any>()
            val encoded = VaultPayloadCodec { observed.add(it) }.encode(p)
            encoded.fill(0)
            observed.forEach { assertErased(it) }
            for (failureIndex in observed.indices) {
                val arrays = mutableListOf<Any>()
                val failing =
                    VaultPayloadCodec {
                        arrays.add(it)
                        if (arrays.lastIndex == failureIndex) error("injected failure")
                    }
                org.junit.Assert.assertThrows(IllegalStateException::class.java) { failing.encode(p) }
                arrays.forEach { assertErased(it) }
            }
            val decodedArrays = mutableListOf<Any>()
            VaultPayloadCodec { decodedArrays.add(it) }.decodeSecureNote(fixture("note.json")).clearMemory()
            decodedArrays.forEach { assertErased(it) }
            for (failureIndex in decodedArrays.indices) {
                val arrays = mutableListOf<Any>()
                val failing =
                    VaultPayloadCodec {
                        arrays.add(it)
                        if (arrays.lastIndex == failureIndex) error("injected failure")
                    }
                org.junit.Assert.assertThrows(
                    IllegalStateException::class.java,
                ) { failing.decodeSecureNote(fixture("note.json")) }
                arrays.forEach { assertErased(it) }
            }
        } finally {
            p.clearMemory()
        }
    }

    @Test
    fun malformedCoderResultIsSurfaced() {
        throwIfCoderError(CoderResult.UNDERFLOW)
        org.junit.Assert.assertThrows(java.nio.charset.MalformedInputException::class.java) {
            throwIfCoderError(CoderResult.malformedForLength(1))
        }
    }

    private fun assertErased(value: Any) {
        when (value) {
            is CharArray -> assertTrue(value.all { it == '\u0000' })
            is ByteArray -> assertTrue(value.all { it == 0.toByte() })
            else -> error("Unexpected allocation")
        }
    }
}
