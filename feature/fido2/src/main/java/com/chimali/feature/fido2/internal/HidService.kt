package com.chimali.feature.fido2.internal

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.chimali.core.bluetooth.HidManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class HidService : Service() {

    @Inject
    lateinit var hidManager: HidManager

    @Inject
    lateinit var requestQueue: RequestQueue

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("ForegroundServiceType")
    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification())
        // Start listening for HID reports via HidManager callbacks
    }

    private fun createNotification(): Notification {
        val channelId = "fido_service"
        val channel = NotificationChannel(channelId, "FIDO2 Authenticator", NotificationManager.IMPORTANCE_LOW)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        return Notification.Builder(this, channelId)
            .setContentTitle("Chimali Authenticator Active")
            .setContentText("Listening for authentication requests...")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1003
    }
}
