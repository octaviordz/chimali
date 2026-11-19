package com.chimali.fido2.presentation.viewmodel

import android.bluetooth.BluetoothDevice
import com.chimali.core.common.annotation.Req
import com.chimali.fido2.data.transport.Fido2ConnectionState
import com.chimali.fido2.domain.model.PairedDevice
import com.chimali.fido2.domain.repository.BluetoothTransportMode
import com.chimali.fido2.domain.repository.TransportAvailability
import com.chimali.fido2.domain.repository.TransportUnavailableReason
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class Fido2HomeViewModelTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun `connected host display name cache resets after dashboard collectors stop`() =
        runTest {
            val device = mockk<BluetoothDevice>()
            every { device.address } returns "02:00:00:00:00:01"
            every { device.name } returns "Synthetic device name"
            val connectionState = MutableStateFlow<Fido2ConnectionState>(Fido2ConnectionState.Connected(device))
            val pairedDevices =
                MutableStateFlow(
                    listOf(
                        PairedDevice(
                            macAddress = "02:00:00:00:00:01",
                            name = "Synthetic paired name",
                            deviceClass = null,
                            alias = "Synthetic alias marker",
                            lastUsedAt = 1L,
                            createdAt = 1L,
                        ),
                    ),
                )
            val displayName = connectedDeviceDisplayNameState(backgroundScope, connectionState, pairedDevices)
            val collector =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    displayName.collect()
                }

            runCurrent()
            assertEquals("Synthetic alias marker", displayName.value)

            collector.cancel()
            runCurrent()
            advanceTimeBy(CONNECTED_DEVICE_DISPLAY_NAME_STOP_TIMEOUT_MS)
            runCurrent()

            assertEquals(null, displayName.value)
        }

    @Test
    @Req("FR-BT-SEL-030", "FR-BT-SEL-040")
    fun `start is gated until the process preference is frozen`() {
        assertEquals(false, isTransportStartEnabled(null))
        assertEquals(true, isTransportStartEnabled(BluetoothTransportMode.CLASSIC_HID))
        assertEquals(true, isTransportStartEnabled(BluetoothTransportMode.FIDO_BLE))
    }

    @Test
    @Req("FR-BLE-070", "FR-BT-SEL-050", "TR-BLE-040")
    fun `start action retries a frozen unavailable transport`() {
        assertEquals(
            TransportToggleAction.Retry,
            transportToggleAction(
                Fido2ConnectionState.Idle,
                TransportAvailability.Unavailable(TransportUnavailableReason.PermissionRequired),
            ),
        )
        assertEquals(
            TransportToggleAction.Retry,
            transportToggleAction(
                Fido2ConnectionState.Error("Permission required"),
                TransportAvailability.Unavailable(TransportUnavailableReason.BluetoothOff),
            ),
        )
        assertEquals(
            TransportToggleAction.Retry,
            transportToggleAction(
                Fido2ConnectionState.Error("BLE advertising stopped unexpectedly"),
                TransportAvailability.Ready,
            ),
        )
        assertEquals(
            TransportToggleAction.Retry,
            transportToggleAction(
                Fido2ConnectionState.Idle,
                TransportAvailability.Ready,
            ),
        )
    }

    @Test
    @Req("FR-BT-SEL-030", "FR-BLE-060")
    fun `start and stop retain their normal actions when transport is available`() {
        assertEquals(
            TransportToggleAction.Start,
            transportToggleAction(Fido2ConnectionState.Idle, TransportAvailability.SelectedNotStarted),
        )
        assertEquals(
            TransportToggleAction.Stop,
            transportToggleAction(Fido2ConnectionState.Advertising, TransportAvailability.Ready),
        )
    }

    @Test
    @Req("FR-BT-SEL-050", "TR-BT-SEL-020")
    fun `dashboard exposes each unavailable reason and keeps retry guidance`() {
        val cases =
            listOf(
                TransportUnavailableReason.PreferenceReadFailed to
                    "Could not read the saved Bluetooth transport. Retry or open Transport Settings.",
                TransportUnavailableReason.InvalidPreference to
                    "The saved Bluetooth transport is invalid. Choose a transport in Settings.",
                TransportUnavailableReason.InitializationTimeout to
                    "Bluetooth transport initialization timed out. Retry or choose another transport in Settings.",
                TransportUnavailableReason.Unsupported to
                    "This Bluetooth transport is unsupported on this device. Choose the alternative in Settings.",
                TransportUnavailableReason.PermissionRequired to
                    "Bluetooth permission is required. Grant it and retry.",
                TransportUnavailableReason.BluetoothOff to
                    "Bluetooth is off. Turn it on and retry.",
                TransportUnavailableReason.StartupFailed to
                    "Bluetooth transport failed to start. Retry or choose another transport in Settings.",
            )

        cases.forEach { (reason, expectedMessage) ->
            val availability = TransportAvailability.Unavailable(reason)
            assertEquals(
                Fido2ConnectionState.Error(expectedMessage),
                dashboardStateForTransport(Fido2ConnectionState.Idle, availability),
                "Unexpected dashboard message for $reason",
            )
            assertEquals(
                TransportToggleAction.Retry,
                transportToggleAction(Fido2ConnectionState.Idle, availability),
                "Unavailable transport $reason must remain retryable",
            )
        }
    }
}
