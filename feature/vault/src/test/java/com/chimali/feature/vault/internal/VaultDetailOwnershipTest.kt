package com.chimali.feature.vault.internal

import androidx.lifecycle.ViewModelStore
import com.chimali.core.common.result.Outcome
import com.chimali.feature.vault.api.VaultIntent
import com.chimali.feature.vault.api.VaultItem
import com.chimali.feature.vault.api.VaultService
import com.chimali.feature.vault.api.VaultType
import com.chimali.feature.vault.internal.crypto.VaultCryptoService
import com.chimali.feature.vault.internal.crypto.VaultPayloadCodec
import io.mockk.coEvery
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** T060: actual field references must be erased when detail ownership ends or delivery is stale. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Parameterized::class)
class VaultDetailOwnershipTest(
    private val type: VaultType,
) {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val service = mockk<VaultService>()
    private val crypto = mockk<VaultCryptoService>()
    private lateinit var vm: VaultViewModel
    private val identity = UUID(0, 1)
    private val arrays = mutableListOf<Any>()
    private val codec = VaultPayloadCodec { arrays.add(it) }

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        vm = VaultViewModel(service, mockk(), crypto)
        store.put("vault", vm)
        val item = VaultItem(identity, type, "title", byteArrayOf(1), byteArrayOf(), "", "", null, identity)
        coEvery { service.getItems(null) } returns Outcome.Success(listOf(item))
        coEvery { service.getItemLabelIds(identity) } returns Outcome.Success(emptyList())
    }

    @After
    fun cleanup() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun arrange(gate: CompletableDeferred<Unit>) {
        fun bytes(name: String) =
            checkNotNull(javaClass.getResourceAsStream("/vault-legacy-v1/$name.json"))
                .use { it.readBytes() }
        when (type) {
            VaultType.PASSWORD ->
                coEvery { crypto.decryptPassword(any()) } coAnswers {
                    gate.await()
                    Outcome.Success(codec.decodePassword(bytes("password")))
                }
            VaultType.CREDIT_CARD ->
                coEvery { crypto.decryptCreditCard(any()) } coAnswers {
                    gate.await()
                    Outcome.Success(codec.decodeCreditCard(bytes("card")))
                }
            VaultType.NOTE ->
                coEvery { crypto.decryptSecureNote(any()) } coAnswers {
                    gate.await()
                    Outcome.Success(codec.decodeSecureNote(bytes("note")))
                }
        }
    }

    @Test
    fun lateDecodedDetailsAreErasedAfterDismissal() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            arrange(gate)
            vm.processIntent(VaultIntent.DecryptItem(identity))
            runCurrent()
            vm.processIntent(VaultIntent.ClearSelectedItem)
            gate.complete(Unit)
            advanceUntilIdle()
            assertNull(vm.state.value.selectedItem)
            assertErased()
        }

    @Test
    fun deliveredDetailsAreErasedOnClearAndViewModelDisposal() =
        runTest {
            val gate = CompletableDeferred(Unit)
            arrange(gate)
            repeat(2) { index ->
                vm.processIntent(VaultIntent.DecryptItem(identity))
                advanceUntilIdle()
                assertTrue(arrays.any { it is CharArray && it.any { c -> c != '\u0000' } })
                if (index == 0) vm.processIntent(VaultIntent.ClearSelectedItem) else store.clear()
                assertNull(vm.state.value.selectedItem)
                assertErased()
            }
        }

    private fun assertErased() {
        assertTrue(arrays.isNotEmpty())
        arrays.forEach {
            when (it) {
                is CharArray -> assertTrue(it.all { c -> c == '\u0000' })
                is ByteArray -> assertTrue(it.all { b -> b == 0.toByte() })
                else -> error("Unexpected allocation")
            }
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun types(): List<Array<VaultType>> = VaultType.entries.map { arrayOf(it) }
    }
}
