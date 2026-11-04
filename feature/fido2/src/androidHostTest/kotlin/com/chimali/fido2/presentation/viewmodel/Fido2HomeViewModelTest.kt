package com.chimali.fido2.presentation.viewmodel

import com.chimali.core.common.annotation.Req
import com.chimali.fido2.data.transport.Fido2ConnectionState
import com.chimali.fido2.domain.repository.TransportAvailability
import com.chimali.fido2.domain.repository.TransportUnavailableReason
import kotlin.test.Test
import kotlin.test.assertEquals

class Fido2HomeViewModelTest {
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
}
