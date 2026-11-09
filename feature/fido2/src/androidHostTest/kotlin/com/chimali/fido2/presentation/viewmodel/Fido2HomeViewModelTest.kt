package com.chimali.fido2.presentation.viewmodel

import com.chimali.core.common.annotation.Req
import com.chimali.fido2.data.transport.Fido2ConnectionState
import com.chimali.fido2.domain.repository.BluetoothTransportMode
import com.chimali.fido2.domain.repository.TransportAvailability
import com.chimali.fido2.domain.repository.TransportUnavailableReason
import kotlin.test.Test
import kotlin.test.assertEquals

class Fido2HomeViewModelTest {
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
