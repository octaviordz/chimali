package com.chimali.fido2.service

import android.os.Build
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Fido2TransportServiceForegroundPermissionTest {
    @Test
    fun api35_deniedBluetoothPermissionPreventsStickyServiceRestart() {
        assertFalse(
            hasConnectedDeviceForegroundServicePrerequisites(
                sdkInt = Build.VERSION_CODES.VANILLA_ICE_CREAM,
                foregroundServicePermissionGranted = true,
                bluetoothRuntimePermissionGranted = false,
            ),
        )
    }

    @Test
    fun api35_requiresConnectedDeviceForegroundServicePermission() {
        assertFalse(
            hasConnectedDeviceForegroundServicePrerequisites(
                sdkInt = Build.VERSION_CODES.VANILLA_ICE_CREAM,
                foregroundServicePermissionGranted = false,
                bluetoothRuntimePermissionGranted = true,
            ),
        )
    }

    @Test
    fun api35_allowsStartWhenBothPrerequisitesAreGranted() {
        assertTrue(
            hasConnectedDeviceForegroundServicePrerequisites(
                sdkInt = Build.VERSION_CODES.VANILLA_ICE_CREAM,
                foregroundServicePermissionGranted = true,
                bluetoothRuntimePermissionGranted = true,
            ),
        )
    }

    @Test
    fun preApi31_doesNotRequireRuntimeBluetoothPermissionForForegroundStart() {
        assertTrue(
            hasConnectedDeviceForegroundServicePrerequisites(
                sdkInt = Build.VERSION_CODES.R,
                foregroundServicePermissionGranted = false,
                bluetoothRuntimePermissionGranted = false,
            ),
        )
    }

    @Test
    fun api31_throughApi33_requiresBluetoothRuntimePermissionOnly() {
        assertFalse(
            hasConnectedDeviceForegroundServicePrerequisites(
                sdkInt = Build.VERSION_CODES.TIRAMISU,
                foregroundServicePermissionGranted = false,
                bluetoothRuntimePermissionGranted = false,
            ),
        )
        assertTrue(
            hasConnectedDeviceForegroundServicePrerequisites(
                sdkInt = Build.VERSION_CODES.TIRAMISU,
                foregroundServicePermissionGranted = false,
                bluetoothRuntimePermissionGranted = true,
            ),
        )
    }
}
