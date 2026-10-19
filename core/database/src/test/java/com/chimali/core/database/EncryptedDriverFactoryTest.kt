package com.chimali.core.database

import android.content.Context
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import com.chimali.core.common.annotation.Req
import com.chimali.core.security.api.MasterSeedProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.io.File
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir

@Req("NFR-SEC-040")
class EncryptedDriverFactoryTest {
    private val context: Context = mockk(relaxed = true)
    private val masterSeedProvider: MasterSeedProvider = mockk()
    private lateinit var factory: EncryptedDriverFactory

    private fun stubSessionOwner() {
        coEvery {
            masterSeedProvider.withSessionOwner(any(), any(), any<suspend (Long) -> SqlDriver>())
        } coAnswers {
            thirdArg<suspend (Long) -> SqlDriver>().invoke(1L)
        }
    }

    @BeforeEach
    fun setUp() {
        factory = EncryptedDriverFactory(context, masterSeedProvider)
    }

    @Test
    fun `deriveDatabaseKey derives deterministic 256-bit key from seed`() {
        val seed1 = ByteArray(32) { it.toByte() }
        val seed2 = ByteArray(32) { it.toByte() }

        val key1 = factory.deriveDatabaseKey(seed1)
        val key2 = factory.deriveDatabaseKey(seed2)

        assertNotNull(key1)
        assertEquals(32, key1.size) // 256 bits = 32 bytes
        assertTrue(key1.contentEquals(key2), "Key derivation must be deterministic")
        assertFalse(key1.contentEquals(seed1), "Derived key must not be identical to the raw seed")
        assertTrue(
            key1.contentEquals(referenceDatabaseKey(seed1)),
            "The existing database KDF output must remain stable",
        )
        assertTrue(seed1.any { it != 0.toByte() }, "deriveDatabaseKey borrows and must preserve its input")
    }

    @Test
    fun `deriveDatabaseKey throws on empty seed`() {
        assertThrows<IllegalArgumentException> {
            factory.deriveDatabaseKey(ByteArray(0))
        }
    }

    @Test
    fun `createDriver throws IllegalStateException when master seed is null`() {
        stubSessionOwner()
        coEvery { masterSeedProvider.getMasterSeed() } returns null

        val exception =
            assertThrows<IllegalStateException> {
                factory.createDriver(mockk(relaxed = true), "test.db")
            }
        assertTrue(exception.message!!.contains("Master seed not initialized"))
    }

    @Test
    fun `createDriver initializes a clean database and reopens it normally`(
        @TempDir tempDir: File,
    ) {
        every { context.applicationContext } returns context
        every { context.getDatabasePath("d") } returns File(tempDir, "d")
        stubSessionOwner()
        coEvery { masterSeedProvider.getMasterSeed() } answers { ByteArray(32) { (it + 1).toByte() } }
        val databaseName = "clean-current-profile.db"

        val initial = factory.createDriver(VaultDatabase.Schema, databaseName)
        initial.close()
        assertTrue(File(tempDir, databaseName).exists())

        val reopened = factory.createDriver(VaultDatabase.Schema, databaseName)
        try {
            val integrity =
                reopened
                    .executeQuery(
                        identifier = null,
                        sql = "PRAGMA integrity_check",
                        mapper = { cursor ->
                            val hasRow = cursor.next().value
                            QueryResult.Value(if (hasRow) cursor.getString(0) else null)
                        },
                        parameters = 0,
                    ).value
            assertEquals("ok", integrity?.lowercase())
        } finally {
            reopened.close()
        }
    }

    @Test
    fun `createDriver erases its owned root copy after driver creation fails`() {
        val rootCopy = ByteArray(32) { 0x5A.toByte() }
        stubSessionOwner()
        coEvery { masterSeedProvider.getMasterSeed() } returns rootCopy
        val schema = mockk<SqlSchema<QueryResult.Value<Unit>>>(relaxed = true)
        every { schema.version } throws IllegalStateException("synthetic schema failure")

        assertThrows<Exception> {
            factory.createDriver(schema, "hdk-cleanup-test.db")
        }

        assertTrue(rootCopy.all { it == 0.toByte() }, "The owned root copy must be erased on failure")
    }

    @Test
    fun `createDriver preserves database and sidecars and erases root copy on open failure`(
        @TempDir tempDir: File,
    ) {
        every { context.applicationContext } returns context
        every { context.getDatabasePath("d") } returns File(tempDir, "d")
        val databaseName = "incompatible-current-profile.db"
        val databaseFile = File(tempDir, databaseName)
        assertTrue(databaseFile.mkdir())
        val databaseMarker = File(databaseFile, "preserve.marker")
        val databaseContents = "synthetic database data".encodeToByteArray()
        databaseMarker.writeBytes(databaseContents)
        val journalFile = File(tempDir, "$databaseName-journal")
        val walFile = File(tempDir, "$databaseName-wal")
        val shmFile = File(tempDir, "$databaseName-shm")
        val journalContents = byteArrayOf(11, 12, 13)
        val walContents = byteArrayOf(21, 22, 23)
        val shmContents = byteArrayOf(31, 32, 33)
        journalFile.writeBytes(journalContents)
        walFile.writeBytes(walContents)
        shmFile.writeBytes(shmContents)
        val rootCopy = ByteArray(32) { 0x5A.toByte() }
        stubSessionOwner()
        coEvery { masterSeedProvider.getMasterSeed() } returns rootCopy
        val schema = mockk<SqlSchema<QueryResult.Value<Unit>>>(relaxed = true)
        every { schema.version } throws IllegalStateException("synthetic schema-open failure")

        assertThrows<Exception> {
            factory.createDriver(schema, databaseName)
        }

        assertTrue(databaseFile.isDirectory)
        assertTrue(databaseMarker.readBytes().contentEquals(databaseContents))
        assertTrue(journalFile.readBytes().contentEquals(journalContents))
        assertTrue(walFile.readBytes().contentEquals(walContents))
        assertTrue(shmFile.readBytes().contentEquals(shmContents))
        assertTrue(rootCopy.all { it == 0.toByte() }, "The owned root copy must be erased on open failure")
    }

    @Test
    fun `verifyIntegrity returns false when database file does not exist`() {
        every { context.getDatabasePath("non_existent.db") } returns
            mockk(relaxed = true) {
                every { exists() } returns false
            }

        val result = factory.verifyIntegrity("non_existent.db")
        assertEquals(false, result)
    }

    @Test
    fun `verifyIntegrity fails closed when an existing database cannot be opened`() {
        val databaseFile = File.createTempFile("encrypted-integrity-test", ".db")
        try {
            every { context.getDatabasePath("unavailable-seed.db") } returns databaseFile
            stubSessionOwner()
            coEvery { masterSeedProvider.getMasterSeed() } returns null

            assertFalse(factory.verifyIntegrity("unavailable-seed.db"), "An unreadable database is not trusted")
        } finally {
            databaseFile.delete()
        }
    }

    private fun assertFalse(
        condition: Boolean,
        message: String,
    ) {
        assertTrue(!condition, message)
    }

    private fun referenceDatabaseKey(seed: ByteArray): ByteArray {
        val hexPassword = seed.joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
        val spec = PBEKeySpec(hexPassword.toCharArray(), "chimali_db_salt".toByteArray(), 2048, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
