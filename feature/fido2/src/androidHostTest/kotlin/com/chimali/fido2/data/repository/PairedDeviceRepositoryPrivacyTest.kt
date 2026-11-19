package com.chimali.fido2.data.repository

import android.database.SQLException
import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.fido2.data.database.Fido2Database
import com.chimali.fido2.data.database.PairedDeviceQueries
import com.chimali.fido2.testing.CapturedDiagnostics
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class PairedDeviceRepositoryPrivacyTest {
    @Test
    fun `paired device update and delete diagnostics omit address and database exception`() =
        runTest {
            val addressMarker = "AA:BB:CC:DD:EE:FF"
            val exceptionMarker = "synthetic-private-database-error"
            val queries = mockk<PairedDeviceQueries>()
            val database = mockk<Fido2Database>()
            every { database.pairedDeviceQueries } returns queries
            every { queries.update_alias(any(), any()) } throws SQLException(exceptionMarker)
            every { queries.delete_by_address(any()) } throws SQLException(exceptionMarker)
            val repository = PairedDeviceRepositoryImpl(database)
            val diagnostics = CapturedDiagnostics()
            try {
                val updateResult = assertIs<Outcome.Error<*>>(repository.updateAlias(addressMarker, "test alias"))
                val deleteResult = assertIs<Outcome.Error<*>>(repository.deleteDevice(addressMarker))
                assertIs<DomainError.DatabaseError>(updateResult.error).also { assertEquals(null, it.cause) }
                assertIs<DomainError.DatabaseError>(deleteResult.error).also { assertEquals(null, it.cause) }
                diagnostics.assertNoSensitiveValues(addressMarker, exceptionMarker)
            } finally {
                diagnostics.close()
            }
        }
}
