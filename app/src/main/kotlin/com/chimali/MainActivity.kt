package com.chimali

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.rememberNavController
import com.chimali.fido2.presentation.navigation.Fido2RegistrationNavGraph

class MainActivity : FragmentActivity() {
    companion object {
        private const val TAG = "Chimali:MainActivity"
    }

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (allGranted) {
                android.util.Log.i(TAG, "All startup permissions granted (Nearby Devices flow).")
            } else {
                android.util.Log.w(TAG, "Some permissions denied at startup: $permissions")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // T191a: Request mandatory permissions for Bluetooth HID functionality at startup.
        // - Android 13+ (TIRAMISU, API 33): Requires 'Nearby Devices' + 'Notifications' for the foreground service.
        // - Android 12 (S, API 31): Requires 'Nearby Devices' group only.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.util.Log.d(TAG, "Launching Nearby Devices + Notifications permission request...")
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.POST_NOTIFICATIONS,
                ),
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            android.util.Log.d(TAG, "Launching Nearby Devices permission request...")
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                ),
            )
        } else {
            // Legacy permissions for Bluetooth and Location on older devices
            android.util.Log.d(TAG, "Requesting legacy Bluetooth/Location permissions...")
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                ),
            )
        }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val navController = rememberNavController()
                    Fido2RegistrationNavGraph(
                        navController = navController,
                        onRegistrationComplete = { /* Handle global success */ },
                        onRegistrationCancelled = { /* Handle global cancel */ },
                    )
                }
            }
        }
    }
}
