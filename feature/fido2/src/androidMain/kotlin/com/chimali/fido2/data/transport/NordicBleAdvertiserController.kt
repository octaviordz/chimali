package com.chimali.fido2.data.transport

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.ParcelUuid
import co.touchlab.kermit.Logger
import com.chimali.core.common.annotation.Req
import java.util.UUID
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import no.nordicsemi.android.kotlin.ble.advertiser.BleAdvertiser
import no.nordicsemi.android.kotlin.ble.advertiser.callback.BleAdvertisingStatus
import no.nordicsemi.android.kotlin.ble.advertiser.callback.OnAdvertisingSetStarted
import no.nordicsemi.android.kotlin.ble.advertiser.callback.OnAdvertisingSetStopped
import no.nordicsemi.android.kotlin.ble.core.advertiser.BleAdvertisingConfig
import no.nordicsemi.android.kotlin.ble.core.advertiser.BleAdvertisingData
import no.nordicsemi.android.kotlin.ble.core.advertiser.BleAdvertisingSettings

@Req("FR-BLE-010", "TR-BLE-010")
class NordicBleAdvertiserController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val advertiserFactory: (Context) -> BleAdvertiser = { BleAdvertiser.create(it) },
) : BleAdvertiserController {
    private var advertisingJob: Job? = null
    private var advertiser: BleAdvertiser? = null
    private var adapterStateReceiver: BroadcastReceiver? = null

    @Volatile private var advertisingStarted = false

    @Suppress("TooGenericExceptionCaught") // Convert Nordic flow failures into a retryable transport startup result.
    override suspend fun startAdvertising(
        serviceUuid: UUID,
        onUnexpectedFailure: (Throwable) -> Unit,
    ): Result<Unit> =
        try {
            if (advertisingStarted && advertisingJob?.isActive == true) return Result.success(Unit)

            val adapterTurnedOff = AtomicBoolean(false)
            registerAdapterStateReceiver { failure ->
                adapterTurnedOff.set(true)
                onUnexpectedFailure(failure)
            }
            val adv = advertiserFactory(context)
            advertiser = adv
            val config =
                BleAdvertisingConfig(
                    settings =
                        BleAdvertisingSettings(
                            connectable = true,
                            legacyMode = true,
                            scannable = true,
                        ),
                    advertiseData =
                        BleAdvertisingData(
                            serviceUuid = ParcelUuid(serviceUuid),
                        ),
                    scanResponseData = BleAdvertisingData(includeDeviceName = true),
                )
            val startup = CompletableDeferred<Result<Unit>>()
            val job =
                scope.launch {
                    try {
                        adv.advertise(config).collect { event ->
                            when (event) {
                                is OnAdvertisingSetStarted -> {
                                    if (event.status == BleAdvertisingStatus.ADVERTISE_SUCCESS) {
                                        advertisingStarted = true
                                        startup.complete(Result.success(Unit))
                                    } else {
                                        startup.complete(
                                            Result.failure(
                                                IllegalStateException(
                                                    "Nordic advertiser failed to start: ${event.status}",
                                                ),
                                            ),
                                        )
                                    }
                                }
                                is OnAdvertisingSetStopped -> {
                                    advertisingStarted = false
                                    error("Nordic advertiser stopped")
                                }
                                else -> Unit
                            }
                        }
                        val failure = IllegalStateException("Nordic advertiser flow completed")
                        if (!startup.complete(Result.failure(failure))) onUnexpectedFailure(failure)
                    } catch (e: CancellationException) {
                        startup.complete(Result.failure(e))
                        throw e
                    } catch (e: Exception) {
                        if (!startup.complete(Result.failure(e))) onUnexpectedFailure(e)
                        Logger.e(e) { "NordicBleAdvertiserController: Advertising failed or interrupted" }
                    }
                }
            advertisingJob = job
            val result =
                try {
                    withTimeoutOrNull(ADVERTISING_START_TIMEOUT_MS) {
                        startup.await()
                    } ?: Result.failure(TimeoutException("Nordic advertiser did not report a start within 5 seconds"))
                } catch (cancellation: CancellationException) {
                    job.cancelAndJoin()
                    if (advertisingJob === job) {
                        advertisingJob = null
                        advertiser = null
                        advertisingStarted = false
                    }
                    unregisterAdapterStateReceiver()
                    throw cancellation
                }
            if (result.isFailure) {
                job.cancelAndJoin()
                if (advertisingJob === job) {
                    advertisingJob = null
                    advertiser = null
                    advertisingStarted = false
                }
                unregisterAdapterStateReceiver()
            } else if (adapterTurnedOff.get()) {
                val failure = IllegalStateException("Bluetooth adapter turned off during BLE startup")
                stopAdvertising()
                stopObservingAdapterState()
                return Result.failure(failure)
            }
            result
        } catch (e: SecurityException) {
            stopAdvertising()
            stopObservingAdapterState()
            Logger.e(e) { "NordicBleAdvertiserController: Failed to start advertising" }
            Result.failure(e)
        } catch (e: IllegalArgumentException) {
            stopAdvertising()
            stopObservingAdapterState()
            Logger.e(e) { "NordicBleAdvertiserController: Failed to start advertising" }
            Result.failure(e)
        } catch (e: IllegalStateException) {
            stopAdvertising()
            stopObservingAdapterState()
            Logger.e(e) { "NordicBleAdvertiserController: Failed to start advertising" }
            Result.failure(e)
        }

    override fun stopAdvertising() {
        advertisingJob?.cancel()
        advertisingJob = null
        advertiser = null
        advertisingStarted = false
    }

    override fun stopObservingAdapterState() {
        unregisterAdapterStateReceiver()
    }

    private fun registerAdapterStateReceiver(onUnexpectedFailure: (Throwable) -> Unit) {
        unregisterAdapterStateReceiver()
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context?,
                    intent: Intent?,
                ) {
                    if (intent?.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    if (state != BluetoothAdapter.STATE_TURNING_OFF && state != BluetoothAdapter.STATE_OFF) return

                    advertisingStarted = false
                    val failure = IllegalStateException("Bluetooth adapter turned off during BLE advertising")
                    onUnexpectedFailure(failure)
                    stopAdvertising()
                }
            }
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Bluetooth may broadcast from a privileged UID. Android protects ACTION_STATE_CHANGED,
            // so EXPORTED receives that system event without accepting app-forged broadcasts.
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        adapterStateReceiver = receiver
    }

    private fun unregisterAdapterStateReceiver() {
        val receiver = adapterStateReceiver ?: return
        adapterStateReceiver = null
        try {
            context.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
            // The receiver may already have been removed by Android during teardown.
        }
    }

    private companion object {
        const val ADVERTISING_START_TIMEOUT_MS = 5_000L
    }
}
