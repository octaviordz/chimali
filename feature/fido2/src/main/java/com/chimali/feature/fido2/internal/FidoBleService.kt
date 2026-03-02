package com.chimali.feature.fido2.internal

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.chimali.core.bluetooth.api.BluetoothHidAuthenticator
import com.chimali.core.bluetooth.impl.BluetoothHidAuthenticatorImpl
import com.chimali.core.bluetooth.impl.FidoHidFraming
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private const val TAG = "FidoBleService"

@AndroidEntryPoint
class FidoBleService : Service() {

    @Inject
    lateinit var hidAuthenticator: BluetoothHidAuthenticator

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("ForegroundServiceType")
    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification())

        // Register HID message listener before starting to advertise.
        // The cast is safe: Hilt always provides BluetoothHidAuthenticatorImpl here.
        (hidAuthenticator as? BluetoothHidAuthenticatorImpl)?.setMessageListener { message ->
            Log.d(TAG, "HID message received: cmd=0x${message.cmd.toInt().and(0xFF).toString(16)} len=${message.data.size}")
            handleHidMessage(message)
        }

        hidAuthenticator.startAdvertising()
        Log.d(TAG, "HID Authenticator started advertising")
    }

    override fun onDestroy() {
        hidAuthenticator.stop()
        super.onDestroy()
    }

    /**
     * Handles a complete, reassembled FIDO HID message dispatched from the transport layer.
     *
     * Currently logs the command for debugging. The next phase will dispatch this to
     * the passkey-rs CTAP2 processor.
     */
    private fun handleHidMessage(message: FidoHidFraming.HidMessage) {
        val cmdHex = "0x${message.cmd.toInt().and(0xFF).toString(16).padStart(2, '0')}"
        when (message.cmd) {
            FidoHidFraming.CMD_PING -> {
                // Echo back the same payload.
                Log.d(TAG, "PING received, echoing back")
                (hidAuthenticator as? BluetoothHidAuthenticatorImpl)?.sendHidMessage(message)
            }
            FidoHidFraming.CMD_CBOR -> {
                Log.d(TAG, "CBOR (CTAP2) request received — passkey-rs integration pending")
                // TODO: dispatch to CtapProcessor via passkey-rs UniFFI binding
            }
            FidoHidFraming.CMD_MSG -> {
                Log.d(TAG, "MSG (CTAP1/U2F) request received — not yet implemented")
            }
            else -> {
                Log.w(TAG, "Unknown HID command: $cmdHex")
            }
        }
    }

    private fun createNotification(): Notification {
        val channelId = "fido_hid_service"
        val channel = NotificationChannel(
            channelId,
            "FIDO2 HID Authenticator",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        return Notification.Builder(this, channelId)
            .setContentTitle("Chimali Security Key Active")
            .setContentText("Paired as a Bluetooth FIDO2 Security Key")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1004
    }
}
