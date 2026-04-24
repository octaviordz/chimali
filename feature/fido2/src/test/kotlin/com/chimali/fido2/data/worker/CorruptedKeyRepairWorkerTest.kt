package com.chimali.fido2.data.worker

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlinx.coroutines.test.runTest

class CorruptedKeyRepairWorkerTest {
    private val worker = mockk<CorruptedKeyRepairWorker>(relaxed = true)

    @Test
    fun `doWork successfully processes a list of corrupted IDs`() =
        runTest {
            val corruptedIds = listOf("cred-1", "cred-2")

            // Mock successful execution
            coEvery { worker.doWork(corruptedIds) } returns Result.success(Unit)

            val result = worker.doWork(corruptedIds)

            assertTrue(result.isSuccess)
            coVerify(exactly = 1) { worker.doWork(corruptedIds) }
        }

    @Test
    fun `doWork returns failure when repository update fails`() =
        runTest {
            val corruptedIds = listOf("cred-1")
            val error = RuntimeException("Database error")

            // Mock failure execution
            coEvery { worker.doWork(corruptedIds) } returns Result.failure(error)

            val result = worker.doWork(corruptedIds)

            assertTrue(result.isFailure)
            coVerify(exactly = 1) { worker.doWork(corruptedIds) }
        }
}
