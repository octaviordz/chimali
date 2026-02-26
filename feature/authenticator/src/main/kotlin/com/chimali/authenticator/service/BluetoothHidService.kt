package com.chimali.authenticator.service

import android.app.Service
import android.bluetooth.*
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@AndroidEntryPoint
class BluetoothHidService : Service() {
    
    @Inject
    lateinit var bluetoothHidRepository: com.chimali.authenticator.domain.repository.BluetoothHidRepository
    
    @Inject
    lateinit var logger: com.chimali.authenticator.domain.logging.AuthenticatorLogger
    
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var hidDevice: BluetoothHidDevice? = null
    private val binder = LocalBinder()
    
    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning = _isServiceRunning.asStateFlow()
    
    private val hidDeviceCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            logger.d("BluetoothHidService", "HID app status changed: $registered")
        }
        
        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            val stateName = when (state) {
                BluetoothHidDevice.STATE_CONNECTED -> "CONNECTED"
                BluetoothHidDevice.STATE_CONNECTING -> "CONNECTING"
                BluetoothHidDevice.STATE_DISCONNECTED -> "DISCONNECTED"
                else -> "UNKNOWN"
            }
            logger.i("BluetoothHidService", "Connection state changed: $device -> $stateName")
            
            val connectionState = when (state) {
                BluetoothHidDevice.STATE_CONNECTED -> com.chimali.authenticator.domain.model.ConnectionState.CONNECTED
                BluetoothHidDevice.STATE_CONNECTING -> com.chimali.authenticator.domain.model.ConnectionState.CONNECTING
                BluetoothHidDevice.STATE_DISCONNECTED -> com.chimali.authenticator.domain.model.ConnectionState.DISCONNECTED
                else -> com.chimali.authenticator.domain.model.ConnectionState.ERROR
            }
            
            // Update repository with connection state change
        }
        
        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            logger.d("BluetoothHidService", "Get report request: $device, type=$type, id=$id")
        }
        
        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int, buffer: ByteArray) {
            logger.d("BluetoothHidService", "Set report request: $device, type=$type, id=$id")
        }
        
        override fun onInterruptData(device: BluetoothDevice, reportId: Byte, data: ByteArray) {
            logger.d("BluetoothHidService", "Interrupt data: $device, reportId=$reportId")
        }
    }
    
    inner class LocalBinder : Binder() {
        fun getService(): BluetoothHidService = this@BluetoothHidService
    }
    
    override fun onBind(intent: Intent): IBinder = binder
    
    override fun onCreate() {
        super.onCreate()
        logger.i("BluetoothHidService", "Service created")
        initializeBluetoothHid()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logger.i("BluetoothHidService", "Service started")
        _isServiceRunning.value = true
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        logger.i("BluetoothHidService", "Service destroyed")
        _isServiceRunning.value = false
        cleanupBluetoothHid()
    }
    
    private fun initializeBluetoothHid() {
        try {
            if (bluetoothAdapter == null) {
                logger.e("BluetoothHidService", "Bluetooth not available")
                return
            }
            
            if (!bluetoothAdapter.isEnabled) {
                logger.w("BluetoothHidService", "Bluetooth not enabled")
                return
            }
            
            // Get HID device proxy
            hidDevice = bluetoothAdapter.getProfileProxy(
                this,
                object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                        if (profile == BluetoothProfile.HID_DEVICE) {
                            hidDevice = proxy as? BluetoothHidDevice
                            logger.i("BluetoothHidService", "HID device proxy connected")
                            setupHidDevice()
                        }
                    }
                    
                    override fun onServiceDisconnected(profile: Int) {
                        if (profile == BluetoothProfile.HID_DEVICE) {
                            hidDevice = null
                            logger.i("BluetoothHidService", "HID device proxy disconnected")
                        }
                    }
                },
                BluetoothProfile.HID_DEVICE
            )
        } catch (e: Exception) {
            logger.e("BluetoothHidService", "Failed to initialize Bluetooth HID", e)
        }
    }
    
    private fun setupHidDevice() {
        hidDevice?.let { device ->
            try {
                // Register as HID app
                val success = device.registerApp(
                    /*sdpRecord=*/ null,
                    /*inQoS=*/ null,
                    /*outQoS=*/ null,
                    hidDeviceCallback
                )
                
                if (success) {
                    logger.i("BluetoothHidService", "HID app registered successfully")
                } else {
                    logger.e("BluetoothHidService", "Failed to register HID app")
                }
            } catch (e: Exception) {
                logger.e("BluetoothHidService", "Failed to setup HID device", e)
            }
        }
    }
    
    private fun cleanupBluetoothHid() {
        hidDevice?.let { device ->
            try {
                device.unregisterApp()
                bluetoothAdapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, device)
                logger.i("BluetoothHidService", "HID device cleaned up")
            } catch (e: Exception) {
                logger.e("BluetoothHidService", "Failed to cleanup HID device", e)
            }
        }
    }
    
    fun startDiscovery(): Boolean {
        return try {
            bluetoothAdapter?.startDiscovery() ?: false
        } catch (e: Exception) {
            logger.e("BluetoothHidService", "Failed to start discovery", e)
            false
        }
    }
    
    fun stopDiscovery(): Boolean {
        return try {
            bluetoothAdapter?.cancelDiscovery() ?: false
        } catch (e: Exception) {
            logger.e("BluetoothHidService", "Failed to stop discovery", e)
            false
        }
    }
}
