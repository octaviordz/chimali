package com.chimali.fido2.data.transport

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.chimali.core.common.annotation.Req
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import no.nordicsemi.android.kotlin.ble.advertiser.BleAdvertiser
import no.nordicsemi.android.kotlin.ble.advertiser.callback.BleAdvertisingStatus
import no.nordicsemi.android.kotlin.ble.advertiser.callback.OnAdvertisingSetStarted
import no.nordicsemi.android.kotlin.ble.core.advertiser.BleAdvertisingConfig

@Req("FR-BLE-010", "FR-BLE-060", "TR-BLE-010")
class NordicBleAdvertiserControllerTest {
    @Test
    fun collectsNordicFlowAndWaitsForSuccessfulStartBeforeReturning() =
        runTest {
            var collected = false
            val advertiser = mockk<BleAdvertiser>()
            val config = slot<BleAdvertisingConfig>()
            every { advertiser.advertise(capture(config)) } returns
                flow {
                    collected = true
                    emit(startedEvent(BleAdvertisingStatus.ADVERTISE_SUCCESS))
                    awaitCancellation()
                }
            val controller = NordicBleAdvertiserController(mockk<Context>(relaxed = true), this, { advertiser })

            val result = controller.startAdvertising(UUID.randomUUID())

            assertTrue(result.isSuccess)
            assertTrue(collected)
            assertTrue(config.captured.settings.connectable)
            assertTrue(config.captured.settings.legacyMode)
            assertTrue(config.captured.settings.scannable == true)
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
            val controller = NordicBleAdvertiserController(mockk<Context>(relaxed = true), this, { advertiser })

            val result = controller.startAdvertising(UUID.randomUUID())

            assertFalse(result.isSuccess)
            assertTrue(cancelled)
        }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun bluetoothTurningOffReportsUnexpectedAdvertisingFailureAndStopsAdvertiser() =
        runTest {
            var cancelled = false
            var collectionCount = 0
            val context = mockk<Context>(relaxed = true)
            val receiver = slot<BroadcastReceiver>()
            every { context.registerReceiver(capture(receiver), any<IntentFilter>()) } returns null
            every { context.registerReceiver(capture(receiver), any<IntentFilter>(), any<Int>()) } returns null
            val advertiser = mockk<BleAdvertiser>()
            every { advertiser.advertise(any()) } returns
                flow {
                    collectionCount += 1
                    emit(startedEvent(BleAdvertisingStatus.ADVERTISE_SUCCESS))
                    try {
                        awaitCancellation()
                    } finally {
                        cancelled = true
                    }
                }
            val controller = NordicBleAdvertiserController(context, this, { advertiser })
            val failures = mutableListOf<Throwable>()
            val offIntent = mockk<Intent>()
            every { offIntent.action } returns BluetoothAdapter.ACTION_STATE_CHANGED
            every { offIntent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR) } returns
                BluetoothAdapter.STATE_TURNING_OFF

            assertTrue(controller.startAdvertising(UUID.randomUUID(), failures::add).isSuccess)
            receiver.captured.onReceive(context, offIntent)
            runCurrent()

            assertEquals(1, failures.size)
            assertTrue(
                failures
                    .single()
                    .message
                    .orEmpty()
                    .contains("turned off"),
            )
            assertTrue(cancelled)
            assertTrue(controller.startAdvertising(UUID.randomUUID()).isSuccess)
            assertEquals(2, collectionCount)
            controller.stopAdvertising()
        }

    private fun startedEvent(status: BleAdvertisingStatus) = OnAdvertisingSetStarted(mockk(), 0, status)
}
