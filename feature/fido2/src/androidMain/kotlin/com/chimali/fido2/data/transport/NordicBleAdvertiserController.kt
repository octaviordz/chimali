package com.chimali.fido2.data.transport

import android.content.Context
import android.os.ParcelUuid
import co.touchlab.kermit.Logger
import com.chimali.core.common.annotation.Req
import java.util.UUID
import java.util.concurrent.TimeoutException
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

@Req("FR-BLE-010", "TR-BLE-010")
class NordicBleAdvertiserController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val advertiserFactory: (Context) -> BleAdvertiser = { BleAdvertiser.create(it) },
) : BleAdvertiserController {
    private var advertisingJob: Job? = null
    private var advertiser: BleAdvertiser? = null

    @Volatile private var advertisingStarted = false

    @Suppress("TooGenericExceptionCaught") // Convert Nordic flow failures into a retryable transport startup result.
    override suspend fun startAdvertising(
        serviceUuid: UUID,
        onUnexpectedFailure: (Throwable) -> Unit,
    ): Result<Unit> =
        try {
            if (advertisingStarted && advertisingJob?.isActive == true) return Result.success(Unit)

            val adv = advertiserFactory(context)
            advertiser = adv
            val config =
                BleAdvertisingConfig(
                    advertiseData =
                        BleAdvertisingData(
                            serviceUuid = ParcelUuid(serviceUuid),
                            includeDeviceName = true,
                        ),
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
                                    val failure = IllegalStateException("Nordic advertiser stopped")
                                    if (!startup.complete(Result.failure(failure))) onUnexpectedFailure(failure)
                                    throw failure
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
                    throw cancellation
                }
            if (result.isFailure) {
                job.cancelAndJoin()
                if (advertisingJob === job) {
                    advertisingJob = null
                    advertiser = null
                    advertisingStarted = false
                }
            }
            result
        } catch (e: SecurityException) {
            Logger.e(e) { "NordicBleAdvertiserController: Failed to start advertising" }
            Result.failure(e)
        } catch (e: IllegalArgumentException) {
            Logger.e(e) { "NordicBleAdvertiserController: Failed to start advertising" }
            Result.failure(e)
        } catch (e: IllegalStateException) {
            Logger.e(e) { "NordicBleAdvertiserController: Failed to start advertising" }
            Result.failure(e)
        }

    override fun stopAdvertising() {
        advertisingJob?.cancel()
        advertisingJob = null
        advertiser = null
        advertisingStarted = false
    }

    private companion object {
        const val ADVERTISING_START_TIMEOUT_MS = 5_000L
    }
}
