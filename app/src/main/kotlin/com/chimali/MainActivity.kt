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
import com.chimali.core.security.api.MasterSeedOwnerKind
import com.chimali.core.security.api.MasterSeedProvider
import com.chimali.navigation.AppNavGraph
import org.koin.android.ext.android.inject

private const val MAIN_ACTIVITY_TAG = "Chimali:MainActivity"

/** Hosts the wallet UI and owns only foreground reveal/import session access. */
class MainActivity : FragmentActivity() {
    private val masterSeedProvider: MasterSeedProvider by inject()

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (!allGranted) {
                logDeniedStartupPermissions(permissions)
            }
        }

    /**
     * Requests startup permissions and installs the app navigation content.
     * @param savedInstanceState Android's restored state, if present.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // T191a: Request mandatory permissions for Bluetooth HID functionality at startup.
        // - Android 13+ (TIRAMISU, API 33): Requires 'Nearby Devices' + 'Notifications' for the foreground service.
        // - Android 12 (S, API 31): Requires 'Nearby Devices' group only.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.POST_NOTIFICATIONS,
                ),
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                ),
            )
        } else {
            // Legacy permissions for Bluetooth and Location on older devices
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
                    AppNavGraph(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    /** Cancels foreground seed owners when the UI stops; active HID ceremonies retain their own owner. */
    override fun onStop() {
        // Foreground reveal/import owners end when the app is no longer visible. Active HID
        // authenticator owners remain independently bounded to their admitted ceremony.
        masterSeedProvider.cancelSessionOwners(MasterSeedOwnerKind.FOREGROUND)
        super.onStop()
    }
}

internal fun logDeniedStartupPermissions(
    permissions: Map<String, Boolean>,
    emitWarning: (tag: String, message: String) -> Unit = { tag, message -> android.util.Log.w(tag, message) },
) {
    if (permissions.values.any { !it }) {
        emitWarning(MAIN_ACTIVITY_TAG, "Some startup permissions were denied at startup.")
    }
}
