package com.chimali.fido2.presentation.viewmodel

import androidx.lifecycle.ViewModelStore
import com.chimali.core.common.result.Outcome
import com.chimali.fido2.domain.model.PairedDevice
import com.chimali.fido2.domain.repository.PairedDeviceRepository
import com.chimali.fido2.domain.usecase.UpdateDeviceAliasUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class PairedDevicesViewModelOwnershipTest {
    @AfterTest
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun pairedDeviceReplayCacheExpiresAfterUiUnsubscribes() =
        runTest {
            Dispatchers.setMain(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))
            val devices = MutableStateFlow<List<PairedDevice>>(emptyList())
            val repository = mockk<PairedDeviceRepository>()
            every { repository.getAllPairedDevices() } returns devices
            val viewModel = PairedDevicesViewModel(repository, mockk<UpdateDeviceAliasUseCase>())
            val store = ViewModelStore().apply { put("paired-devices", viewModel) }
            val collector = backgroundScope.launch { viewModel.pairedDevices.collect { } }
            val device = PairedDevice("02:00:00:00:00:01", "synthetic-device", 1, "synthetic-alias", 1L, 1L)

            try {
                runCurrent()
                devices.value = listOf(device)
                runCurrent()
                assertEquals(device, viewModel.pairedDevices.value.single())

                collector.cancel()
                runCurrent()
                advanceTimeBy(5_000L)
                runCurrent()

                assertTrue(viewModel.pairedDevices.value.isEmpty())
            } finally {
                store.clear()
            }
        }

    @Test
    fun viewModelClearDiscardsBufferedRemovalAddress() =
        runTest {
            Dispatchers.setMain(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))
            val repository = mockk<PairedDeviceRepository>()
            every { repository.getAllPairedDevices() } returns MutableStateFlow<List<PairedDevice>>(emptyList())
            val viewModel = PairedDevicesViewModel(repository, mockk<UpdateDeviceAliasUseCase>())
            val store = ViewModelStore().apply { put("paired-devices", viewModel) }
            val device = PairedDevice("02:00:00:00:00:02", "synthetic-device", 1, "synthetic-alias", 1L, 1L)

            viewModel.pendingRemove(device)
            runCurrent()
            store.clear()

            var cancelled = false
            try {
                viewModel.removalEvents.first()
            } catch (_: CancellationException) {
                cancelled = true
            }
            assertTrue(cancelled)
        }

    @Test
    fun removalEventCarriesOnlyAddressNeededByUndoFlow() =
        runTest {
            Dispatchers.setMain(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))
            val repository = mockk<PairedDeviceRepository>()
            every { repository.getAllPairedDevices() } returns MutableStateFlow<List<PairedDevice>>(emptyList())
            val viewModel = PairedDevicesViewModel(repository, mockk<UpdateDeviceAliasUseCase>())
            val device = PairedDevice("02:00:00:00:00:06", "synthetic-device", 1, "synthetic-alias", 1L, 1L)

            viewModel.pendingRemove(device)
            runCurrent()

            assertEquals(device.macAddress, viewModel.removalEvents.first())
        }

    @Test
    fun selectedEditorAddressClearsOnExitAndViewModelDisposal() =
        runTest {
            Dispatchers.setMain(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))
            val repository = mockk<PairedDeviceRepository>()
            every { repository.getAllPairedDevices() } returns MutableStateFlow<List<PairedDevice>>(emptyList())
            val viewModel = PairedDevicesViewModel(repository, mockk<UpdateDeviceAliasUseCase>())
            val store = ViewModelStore().apply { put("paired-devices", viewModel) }

            viewModel.beginEditingDevice("02:00:00:00:00:03")
            assertEquals("02:00:00:00:00:03", viewModel.editingDeviceAddress.value)
            viewModel.clearEditingDevice()
            assertEquals(null, viewModel.editingDeviceAddress.value)

            viewModel.beginEditingDevice("02:00:00:00:00:04")
            store.clear()
            assertEquals(null, viewModel.editingDeviceAddress.value)
        }

    @Test
    fun clearingEditorSelectionPreservesAddressForAdmittedAliasSaveBorrower() =
        runTest {
            Dispatchers.setMain(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))
            val repository = mockk<PairedDeviceRepository>()
            every { repository.getAllPairedDevices() } returns MutableStateFlow<List<PairedDevice>>(emptyList())
            val updateAlias = mockk<UpdateDeviceAliasUseCase>()
            val borrowerEntered = CompletableDeferred<String>()
            val borrowerCompleted = CompletableDeferred<Unit>()
            val releaseBorrower = CompletableDeferred<Unit>()
            coEvery { updateAlias.invoke(any(), any()) } coAnswers {
                borrowerEntered.complete(firstArg())
                releaseBorrower.await()
                borrowerCompleted.complete(Unit)
                Outcome.Success(Unit)
            }
            val viewModel = PairedDevicesViewModel(repository, updateAlias)
            val store = ViewModelStore().apply { put("paired-devices", viewModel) }
            val address = "02:00:00:00:00:05"

            viewModel.beginEditingDevice(address)
            viewModel.updateAlias(address, "synthetic-alias")
            runCurrent()
            assertEquals(address, borrowerEntered.await())

            viewModel.clearEditingDevice()
            assertEquals(null, viewModel.editingDeviceAddress.value)
            assertTrue(!releaseBorrower.isCompleted)

            releaseBorrower.complete(Unit)
            runCurrent()
            assertTrue(borrowerCompleted.isCompleted)
            store.clear()
        }
}
