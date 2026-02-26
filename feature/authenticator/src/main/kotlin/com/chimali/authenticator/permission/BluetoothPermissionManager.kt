package com.chimali.authenticator.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.chimali.authenticator.domain.error.AuthenticatorError
import com.chimali.authenticator.domain.logging.AuthenticatorLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BluetoothPermissionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: AuthenticatorLogger
) {
    
    private val _permissionStatus = MutableStateFlow<PermissionStatus>(PermissionStatus.Unknown)
    val permissionStatus = _permissionStatus.asStateFlow()
    
    fun checkBluetoothPermissions(): PermissionStatus {
        val permissions = getRequiredBluetoothPermissions()
        
        val allGranted = permissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
        
        val status = if (allGranted) {
            PermissionStatus.Granted
        } else {
            PermissionStatus.Denied(permissions.filter { 
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED 
            })
        }
        
        _permissionStatus.value = status
        return status
    }
    
    fun getRequiredBluetoothPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }
    
    fun shouldShowPermissionRationale(activity: Activity, permission: String): Boolean {
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }
    
    fun requestBluetoothPermissions(activity: Activity, requestCode: Int) {
        val permissions = getRequiredBluetoothPermissions()
        ActivityCompat.requestPermissions(activity, permissions, requestCode)
    }
    
    fun handlePermissionResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ): PermissionStatus {
        if (requestCode == BLUETOOTH_PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            
            val status = if (allGranted) {
                PermissionStatus.Granted
            } else {
                val deniedPermissions = permissions.filterIndexed { index, _ ->
                    grantResults[index] != PackageManager.PERMISSION_GRANTED
                }
                PermissionStatus.Denied(deniedPermissions)
            }
            
            _permissionStatus.value = status
            logger.logSecurityEvent("Bluetooth permission result", mapOf(
                "granted" to allGranted,
                "denied_count" to deniedPermissions.size
            ))
            
            return status
        }
        
        return PermissionStatus.Unknown
    }
    
    fun isBluetoothEnabled(): Boolean {
        val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
        return bluetoothAdapter?.isEnabled == true
    }
    
    fun getBluetoothState(): BluetoothState {
        val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
        return when {
            bluetoothAdapter == null -> BluetoothState.NotSupported
            bluetoothAdapter.isEnabled -> BluetoothState.Enabled
            else -> BluetoothState.Disabled
        }
    }
    
    companion object {
        const val BLUETOOTH_PERMISSION_REQUEST_CODE = 1001
    }
}

sealed class PermissionStatus {
    object Granted : PermissionStatus()
    data class Denied(val deniedPermissions: List<String>) : PermissionStatus()
    object Unknown : PermissionStatus()
}

enum class BluetoothState {
    NotSupported,
    Disabled,
    Enabled
}
