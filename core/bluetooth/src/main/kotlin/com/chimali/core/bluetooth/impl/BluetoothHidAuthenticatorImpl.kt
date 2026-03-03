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
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.Executors
import javax.inject.Inject

@SuppressLint("MissingPermission")
class BluetoothHidAuthenticatorImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : BluetoothHidAuthenticator, BluetoothProfile.ServiceListener {

    private var hidDevice: BluetoothHidDevice? = null
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    
    override var state: AuthenticatorState = AuthenticatorState.IDLE
        private set

    init {
        try {
            adapter?.getProfileProxy(context, this, BluetoothProfile.HID_DEVICE)
        } catch (e: SecurityException) {
            Log.e("BluetoothHID", "Bluetooth permission denied on init", e)
        }
    }

    @SuppressLint("MissingPermission")
    override fun startAdvertising() {
        if (state != AuthenticatorState.IDLE) return
        
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
        } catch (e: SecurityException) {
            Log.e("BluetoothHID", "Bluetooth permission denied when starting advertisement", e)
        }
    }

    @SuppressLint("MissingPermission")
    override fun stop() {
        try {
            // hidDevice?.unregisterApp()
        } catch (e: SecurityException) {
            Log.e("BluetoothHID", "Bluetooth permission denied when stopping", e)
        }
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
