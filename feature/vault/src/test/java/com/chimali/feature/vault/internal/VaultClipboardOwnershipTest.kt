package com.chimali.feature.vault.internal

import androidx.lifecycle.ViewModelStore
import com.chimali.core.clipboard.ClipboardManagerService
import com.chimali.feature.vault.api.VaultIntent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** FR-VAULT-026/027: a copy request owns a mutable operation copy until the platform adapter. */
@OptIn(ExperimentalCoroutinesApi::class)
class VaultClipboardOwnershipTest {
    private val dispatcher = StandardTestDispatcher()
    private val clipboard = mockk<ClipboardManagerService>()
    private val store = ViewModelStore()
    private lateinit var vm: VaultViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        vm = VaultViewModel(mockk(), clipboard, mockk())
        store.put("vault", vm)
    }

    @After
    fun cleanup() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun completionAndFailureEraseCopyWithoutErasingDetails() =
        runTest {
            for (fail in listOf(false, true)) {
                coEvery { clipboard.copySensitiveData("Password", "synthetic") } returns
                    if (fail) Result.failure(IllegalStateException("synthetic failure")) else Result.success(Unit)
                val detail = "synthetic".toCharArray()
                val copy = detail.copyOf()
                val intent = VaultIntent.CopyPassword(copy)
                assertFalse(intent.toString().contains("synthetic"))
                vm.processIntent(intent)
                advanceUntilIdle()
                assertTrue(copy.all { it == '\u0000' })
                assertTrue(detail.any { it != '\u0000' })
                detail.fill('\u0000')
            }
        }

    @Test
    fun neverStartedCopyIsErasedWithoutCallingClipboard() =
        runTest {
            val copy = "synthetic".toCharArray()
            vm.processIntent(VaultIntent.CopyPassword(copy))
            store.clear()
            advanceUntilIdle()
            assertTrue(copy.all { it == '\u0000' })
            coVerify(exactly = 0) { clipboard.copySensitiveData(any(), any(), any()) }
        }

    @Test
    fun cancelledPlatformCopyErasesItsOperationBuffer() =
        runTest {
            coEvery { clipboard.copySensitiveData(any(), any(), any()) } throws CancellationException()
            val copy = "synthetic".toCharArray()
            vm.processIntent(VaultIntent.CopyPassword(copy))
            advanceUntilIdle()
            assertTrue(copy.all { it == '\u0000' })
        }
}
