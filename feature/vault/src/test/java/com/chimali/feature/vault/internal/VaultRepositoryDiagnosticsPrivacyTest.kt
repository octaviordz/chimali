package com.chimali.feature.vault.internal

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import com.chimali.core.database.VaultDatabase
import com.chimali.core.domain.eventsourcing.AggregateService
import com.chimali.core.domain.eventsourcing.vault.VaultCommand
import com.chimali.core.domain.eventsourcing.vault.VaultState
import com.chimali.feature.vault.api.VaultItem
import com.chimali.feature.vault.api.VaultType
import io.mockk.coEvery
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class VaultRepositoryDiagnosticsPrivacyTest {
    private val originalWriters = Logger.config.logWriterList
    private val originalSeverity = Logger.config.minSeverity
    private val records = mutableListOf<String>()
    private lateinit var repository: VaultRepositoryImpl

    @Before
    fun setUp() {
        Logger.setMinSeverity(Severity.Verbose)
        Logger.setLogWriters(
            object : LogWriter() {
                override fun log(
                    severity: Severity,
                    message: String,
                    tag: String,
                    throwable: Throwable?,
                ) {
                    records += message
                    throwable?.let { records += it.stackTraceToString() }
                }
            },
        )
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        VaultDatabase.Schema.create(driver)
        val database = VaultDatabase(driver)
        val aggregate = mockk<AggregateService<VaultCommand, VaultState>>()
        coEvery { aggregate.execute(any(), any()) } returns
            Result.failure(IllegalStateException("SYNTHETIC_EXCEPTION_MARKER"))
        repository = VaultRepositoryImpl(database, aggregate)
    }

    @After
    fun tearDown() {
        Logger.setLogWriters(originalWriters)
        Logger.setMinSeverity(originalSeverity)
        records.clear()
    }

    @Test
    fun aggregateFailureDoesNotLogOrReturnItemIdOrExceptionContent() =
        runTest {
            val itemId = UUID.fromString("12345678-1234-5678-9abc-123456789abc")
            val item =
                VaultItem(
                    id = itemId,
                    type = VaultType.PASSWORD,
                    title = "synthetic title".toCharArray(),
                    payload = byteArrayOf(1, 2, 3),
                    crdtState = byteArrayOf(),
                    dateCreated = "2026-11-14T00:00:00Z",
                    dateModified = "2026-11-14T00:00:00Z",
                    lastBackedUpAt = null,
                    identityId = UUID.randomUUID(),
                )

            val result = repository.saveItem(item)

            val combined = records.joinToString("\n")
            assertFalse(itemId.toString() in combined)
            assertFalse("SYNTHETIC_EXCEPTION_MARKER" in combined)
            val error = (result as com.chimali.core.common.result.Outcome.Error).error
            assertFalse(itemId.toString() in error.message)
            assertFalse("SYNTHETIC_EXCEPTION_MARKER" in error.message)
            assertNull(error.cause)
        }
}
