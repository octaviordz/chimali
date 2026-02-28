package com.chimali.feature.fido2.internal

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.chimali.core.bluetooth.impl.BleGattManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class FidoBleService : Service() {

    @Inject
    lateinit var bleGattManager: BleGattManager

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("ForegroundServiceType")
    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification())
        bleGattManager.startServer()
    }

    override fun onDestroy() {
        bleGattManager.stopServer()
        super.onDestroy()
    }

    private fun createNotification(): Notification {
        val channelId = "fido_ble_service"
        val channel = NotificationChannel(channelId, "FIDO2 BLE Authenticator", NotificationManager.IMPORTANCE_LOW)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        return Notification.Builder(this, channelId)
            .setContentTitle("Chimali BLE Authenticator Active")
            .setContentText("Advertising as a FIDO2 Security Key...")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1004
    }
}
