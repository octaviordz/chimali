package com.chimali.core.bluetooth.impl

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import com.chimali.core.bluetooth.api.AuthenticatorState
import com.chimali.core.bluetooth.api.BluetoothHidAuthenticator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.annotation.RequiresPermission
import java.util.concurrent.Executors

/**
 * Concrete implementation of [BluetoothHidAuthenticator] backed by the Android
 * [BluetoothHidDevice] profile (Bluetooth Classic HID).
 *
 * ## Role in the architecture
 * This class is responsible solely for:
 *  - Acquiring the [BluetoothHidDevice] profile proxy from the system.
 *  - Registering the app's HID SDP record so the host OS can discover and
 *    enumerate the device as a FIDO2 Bluetooth authenticator.
 *  - Exposing a [state] flow that reflects the current connection lifecycle
 *    (IDLE → ADVERTISING → CONNECTED).
 *
 * ## Relationship with BluetoothHidWrapper / BluetoothHidTransportImpl
 * This class handles only the *profile registration* side: the SDP record,
 * capability advertisement, and connection state tracking. The actual HID
 * interrupt-data exchange (sending/receiving HID reports) is delegated to
 * [BluetoothHidWrapper] and orchestrated by [BluetoothHidTransportImpl],
 * which handles CTAPHID framing and routes CTAP2 commands to the appropriate
 * handlers (MakeCredential, GetAssertion, GetInfo).
 *
 * ## Lifecycle
 * 1. Constructed by Koin — immediately calls [initializeProfileProxy] to
 *    register as a [BluetoothProfile.ServiceListener].
 * 2. When [onServiceConnected] fires, the [BluetoothHidDevice] proxy is stored.
 * 3. [startAdvertising] registers the SDP record and sets the app ready for
 *    incoming connections from the host. This is guarded by state so it
 *    cannot be called twice while already advertising.
 * 4. [stop] unregisters the app (currently no-op pending cleanup refactor)
 *    and resets the state to IDLE.
 * 5. [sendConfirmation] is a skeleton call for explicit user-presence signals;
 *    the actual FIDO2 packet sending is done via [BluetoothHidWrapper].
 */
class BluetoothHidAuthenticatorImpl(
    private val context: Context
) : BluetoothHidAuthenticator, BluetoothProfile.ServiceListener {

    companion object {
        private const val TAG = "BluetoothHID"
    }

    /** Underlying Bluetooth HID Device profile proxy, provided by the Android framework. */
    private var hidDevice: BluetoothHidDevice? = null

    /** System Bluetooth adapter, used to acquire the HID Device profile proxy. */
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    
    /**
     * Current lifecycle state of this authenticator. Exposed as a [StateFlow]
     * so observers (e.g., the ViewModel) can react to changes without polling.
     */
    private val _state = MutableStateFlow(AuthenticatorState.IDLE)
    override val state: StateFlow<AuthenticatorState> = _state.asStateFlow()

    init {
        initializeProfileProxy()
    }

    /**
     * Acquires the [BluetoothHidDevice] profile proxy from the Bluetooth adapter.
     *
     * This is called once at construction time. The result arrives asynchronously
     * via [onServiceConnected]. If Bluetooth permission has not been granted yet,
     * the [SecurityException] is caught and logged — the app will need to retry
     * after the user grants the permission.
     */
    @SuppressLint("MissingPermission")
    private fun initializeProfileProxy() {
        try {
            adapter?.getProfileProxy(context, this, BluetoothProfile.HID_DEVICE)
        } catch (e: SecurityException) {
            Log.e(TAG, "Bluetooth permission denied on init", e)
        }
    }

    /**
     * Registers the FIDO2 HID SDP record and starts advertising the device
     * as a Bluetooth HID authenticator.
     *
     * The SDP record uses [BluetoothHidDevice.SUBCLASS1_COMBO] so the host OS
     * recognises the device as a combo HID peripheral (keyboard + other), which
     * is required for CTAP2 HID compliance.
     *
     * This is a no-op if already advertising or connected ([AuthenticatorState] != IDLE).
     *
     * @see com.chimali.core.bluetooth.util.BluetoothHidConstants.FIDO_HID_REPORT_DESCRIPTOR
     */
    @RequiresPermission(
        allOf = [
            android.Manifest.permission.BLUETOOTH_CONNECT,
            android.Manifest.permission.BLUETOOTH_ADVERTISE
        ]
    )
    override fun startAdvertising() {
        if (_state.value != AuthenticatorState.IDLE) return
        
        val sdpSettings = BluetoothHidDeviceAppSdpSettings(
            "Chimali Authenticator",
            "Virtual FIDO Key",
            "Chimali",
            BluetoothHidDevice.SUBCLASS1_COMBO,
            com.chimali.core.bluetooth.util.BluetoothHidConstants.FIDO_HID_REPORT_DESCRIPTOR
        )
        
        try {
            hidDevice?.registerApp(
                sdpSettings,
                null,
                null, // QOS — null uses platform defaults; no latency constraints needed
                Executors.newSingleThreadExecutor(),
                object : BluetoothHidDevice.Callback() {
                    /**
                     * Called when the HID Device app is successfully registered with the
                     * Bluetooth stack. At this point the adapter is visible to BT hosts
                     * as a HID peripheral and Windows/macOS can initiate a pairing.
                     */
                    override fun onAppStatusChanged(
                        pluggedDevice: android.bluetooth.BluetoothDevice?,
                        registered: Boolean
                    ) {
                        if (registered) {
                            _state.value = AuthenticatorState.ADVERTISING
                            Log.d(TAG, "App registered and advertising")
                        }
                    }
                    
                    /**
                     * Called when the HID connection state changes with the remote host.
                     * Maps [BluetoothProfile] state constants to [AuthenticatorState]:
                     *  - STATE_CONNECTED  → CONNECTED  (ready to receive/send HID reports)
                     *  - STATE_DISCONNECTED → IDLE     (waiting for a new connection)
                     *  - Anything else (CONNECTING/DISCONNECTING) is ignored.
                     */
                    override fun onConnectionStateChanged(device: android.bluetooth.BluetoothDevice?, state: Int) {
                        this@BluetoothHidAuthenticatorImpl._state.value = when (state) {
                            BluetoothProfile.STATE_CONNECTED -> AuthenticatorState.CONNECTED
                            BluetoothProfile.STATE_DISCONNECTED -> AuthenticatorState.IDLE
                            else -> this@BluetoothHidAuthenticatorImpl._state.value
                        }
                    }
                }
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Bluetooth permission denied when starting advertisement", e)
        }
    }

    /**
     * Stops the authenticator and resets its state to IDLE.
     *
     * Note: [BluetoothHidDevice.unregisterApp] is currently commented out pending
     * a lifecycle refactor — calling it while the transport layer is still processing
     * an in-flight CTAP2 request can cause a race condition. The SDP record will be
     * cleaned up when the app process exits.
     */
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    override fun stop() {
        try {
            // hidDevice?.unregisterApp() — intentionally deferred; see KDoc above.
        } catch (e: SecurityException) {
            Log.e(TAG, "Bluetooth permission denied when stopping", e)
        }
        _state.value = AuthenticatorState.IDLE
    }

    /**
     * Sends an explicit user-presence confirmation signal to the currently connected host.
     *
     * This is a skeleton implementation. In the current architecture, FIDO2 responses
     * (which encode user-presence implicitly) are sent via [BluetoothHidWrapper] inside
     * [BluetoothHidTransportImpl]. This method provides a hook for future explicit
     * out-of-band confirmation flows (e.g., a physical button tap event).
     */
    override fun sendConfirmation() {
        if (_state.value != AuthenticatorState.CONNECTED) return
        // Send actual HID report for "button press" or FIDO HID response
        Log.d(TAG, "Sending confirmation (skeleton)")
    }

    /**
     * [BluetoothProfile.ServiceListener] callback — stores the [BluetoothHidDevice]
     * proxy so [startAdvertising] and other operations can use it.
     */
    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
        if (profile == BluetoothProfile.HID_DEVICE) {
            hidDevice = proxy as BluetoothHidDevice
        }
    }

    /**
     * [BluetoothProfile.ServiceListener] callback — clears the proxy reference
     * so we do not hold a stale handle after the Bluetooth service has disconnected.
     */
    override fun onServiceDisconnected(profile: Int) {
        if (profile == BluetoothProfile.HID_DEVICE) {
            hidDevice = null
        }
    }
}
