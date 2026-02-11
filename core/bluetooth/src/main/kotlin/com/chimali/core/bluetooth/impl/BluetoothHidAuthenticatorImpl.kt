package com.chimali.core.bluetooth.impl

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import com.chimali.core.bluetooth.api.AuthenticatorState
import com.chimali.core.bluetooth.api.BluetoothHidAuthenticator
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.Executors
import javax.inject.Inject

class BluetoothHidAuthenticatorImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : BluetoothHidAuthenticator, BluetoothProfile.ServiceListener {

    private var hidDevice: BluetoothHidDevice? = null
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    
    override var state: AuthenticatorState = AuthenticatorState.IDLE
        private set

    init {
        adapter?.getProfileProxy(context, this, BluetoothProfile.HID_DEVICE)
    }

    override fun startAdvertising() {
        if (state != AuthenticatorState.IDLE) return
        
        val sdpSettings = BluetoothHidDeviceAppSdpSettings(
            "Chimali Authenticator",
            "Virtual FIDO Key",
            "Chimali",
            BluetoothHidDevice.SUBCLASS1_COMBO,
            com.chimali.core.bluetooth.util.BluetoothHidConstants.FIDO_HID_REPORT_DESCRIPTOR
        )
        
        hidDevice?.registerApp(
            sdpSettings,
            null,
            null, // QOS
            Executors.newSingleThreadExecutor(),
            object : BluetoothHidDevice.Callback() {
                override fun onAppStatusChanged(pluggedDevice: android.bluetooth.BluetoothDevice?, registered: Boolean) {
                    if (registered) {
                        state = AuthenticatorState.ADVERTISING
                        Log.d("BluetoothHID", "App registered and advertising")
                    }
                }
                
                override fun onConnectionStateChanged(device: android.bluetooth.BluetoothDevice?, state: Int) {
                    this@BluetoothHidAuthenticatorImpl.state = when (state) {
                        BluetoothProfile.STATE_CONNECTED -> AuthenticatorState.CONNECTED
                        BluetoothProfile.STATE_DISCONNECTED -> AuthenticatorState.IDLE
                        else -> this@BluetoothHidAuthenticatorImpl.state
                    }
                }
            }
        )
    }

    override fun stop() {
        // hidDevice?.unregisterApp()
        state = AuthenticatorState.IDLE
    }

    override fun sendConfirmation() {
        if (state != AuthenticatorState.CONNECTED) return
        // Send actual HID report for "button press" or FIDO HID response
        Log.d("BluetoothHID", "Sending confirmation (skeleton)")
    }

    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
        if (profile == BluetoothProfile.HID_DEVICE) {
            hidDevice = proxy as BluetoothHidDevice
        }
    }

    override fun onServiceDisconnected(profile: Int) {
        if (profile == BluetoothProfile.HID_DEVICE) {
            hidDevice = null
        }
    }
}
