@file:Suppress("MissingPermission")

package com.chimali.fido2.bluetooth

import android.bluetooth.BluetoothAdapter
import co.touchlab.kermit.Logger
import com.chimali.core.common.annotation.Req
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/** Waits briefly for Android's Bluetooth adapter to finish its startup transition. */
@Req("NFR-SEC-041", "TR-AUTH-031")
internal class BluetoothAdapterStateWaiter(
    private val adapter: () -> BluetoothAdapter?,
) {
    /** Returns the adapter state after a bounded wait through TURNING_ON. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun waitForBluetoothToTurnOn(): Int {
        var state = readBluetoothState("during initial query")
        var waitAttempt = 0
        while (state == BluetoothAdapter.STATE_TURNING_ON && waitAttempt < MAX_WAIT_ATTEMPTS) {
            Logger.d {
                "Bluetooth is turning on, waiting $WAIT_DELAY " +
                    "(attempt ${waitAttempt + 1})..."
            }
            delay(WAIT_DELAY)
            state = readBluetoothState("during wait")
            waitAttempt++
        }
        return state
    }

    private fun readBluetoothState(context: String): Int =
        try {
            adapter()?.state ?: BluetoothAdapter.ERROR
        } catch (e: SecurityException) {
            Logger.e(e) { "registerApp: Failed to query Bluetooth state (permission denied) $context" }
            BluetoothAdapter.ERROR
        }

    private companion object {
        const val MAX_WAIT_ATTEMPTS = 10
        val WAIT_DELAY = 500.milliseconds
    }
}
