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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.annotation.RequiresPermission
import java.util.concurrent.Executors
import javax.inject.Inject

class BluetoothHidAuthenticatorImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : BluetoothHidAuthenticator, BluetoothProfile.ServiceListener {

    private var hidDevice: BluetoothHidDevice? = null
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    
    private val _state = MutableStateFlow(AuthenticatorState.IDLE)
    override val state: StateFlow<AuthenticatorState> = _state.asStateFlow()

    init {
        initializeProfileProxy()
    }

    @SuppressLint("MissingPermission")
    private fun initializeProfileProxy() {
        try {
            adapter?.getProfileProxy(context, this, BluetoothProfile.HID_DEVICE)
        } catch (e: SecurityException) {
            Log.e("BluetoothHID", "Bluetooth permission denied on init", e)
        }
    }

    @RequiresPermission(allOf = [android.Manifest.permission.BLUETOOTH_CONNECT, android.Manifest.permission.BLUETOOTH_ADVERTISE])
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
                null, // QOS
                Executors.newSingleThreadExecutor(),
                object : BluetoothHidDevice.Callback() {
                    override fun onAppStatusChanged(pluggedDevice: android.bluetooth.BluetoothDevice?, registered: Boolean) {
                        if (registered) {
                            _state.value = AuthenticatorState.ADVERTISING
                            Log.d("BluetoothHID", "App registered and advertising")
                        }
                    }
                    
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
            Log.e("BluetoothHID", "Bluetooth permission denied when starting advertisement", e)
        }
    }

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    override fun stop() {
        try {
            // hidDevice?.unregisterApp()
        } catch (e: SecurityException) {
            Log.e("BluetoothHID", "Bluetooth permission denied when stopping", e)
        }
        _state.value = AuthenticatorState.IDLE
    }

    override fun sendConfirmation() {
        if (_state.value != AuthenticatorState.CONNECTED) return
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
