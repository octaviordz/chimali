package com.chimali.fido2.data.transport

import android.content.Context
import com.chimali.core.common.annotation.Req
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import no.nordicsemi.android.kotlin.ble.advertiser.BleAdvertiser
import no.nordicsemi.android.kotlin.ble.advertiser.callback.BleAdvertisingStatus
import no.nordicsemi.android.kotlin.ble.advertiser.callback.OnAdvertisingSetStarted

@Req("FR-BLE-010", "FR-BLE-060", "TR-BLE-010")
class NordicBleAdvertiserControllerTest {
    @Test
    fun collectsNordicFlowAndWaitsForSuccessfulStartBeforeReturning() =
        runTest {
            var collected = false
            val advertiser = mockk<BleAdvertiser>()
            every { advertiser.advertise(any()) } returns
                flow {
                    collected = true
                    emit(startedEvent(BleAdvertisingStatus.ADVERTISE_SUCCESS))
                    awaitCancellation()
                }
            val controller = NordicBleAdvertiserController(mockk<Context>(), this, { advertiser })

            val result = controller.startAdvertising(UUID.randomUUID())

            assertTrue(result.isSuccess)
            assertTrue(collected)
            controller.stopAdvertising()
        }

    @Test
    fun failedStartEventReturnsFailureAndStopsFlow() =
        runTest {
            var cancelled = false
            val advertiser = mockk<BleAdvertiser>()
            every { advertiser.advertise(any()) } returns
                flow {
                    try {
                        emit(startedEvent(BleAdvertisingStatus.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS))
                        awaitCancellation()
                    } finally {
                        cancelled = true
                    }
                }
            val controller = NordicBleAdvertiserController(mockk<Context>(), this, { advertiser })

            val result = controller.startAdvertising(UUID.randomUUID())

            assertFalse(result.isSuccess)
            assertTrue(cancelled)
        }

    private fun startedEvent(status: BleAdvertisingStatus) = OnAdvertisingSetStarted(mockk(), 0, status)
}
