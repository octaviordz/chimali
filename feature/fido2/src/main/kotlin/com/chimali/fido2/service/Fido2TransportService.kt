package com.chimali.fido2.service

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.chimali.fido2.data.transport.Fido2Transport
import com.chimali.fido2.presentation.navigation.Fido2UiEvent
import com.chimali.fido2.presentation.navigation.Fido2UiEventBus
import com.chimali.fido2.presentation.viewmodel.Fido2HomeViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import timber.log.Timber

private const val CHANNEL_ID = "fido2_transport_channel"
private const val NOTIFICATION_ID = 1001
private const val AUTH_REQUEST_CHANNEL_ID = "fido2_auth_requests"
private const val AUTH_REQUEST_NOTIF_ID = 1002

/**
 * Foreground Service that keeps the Bluetooth HID transport alive while
 * the user navigates away from the Chimali Authenticator screen.
 *
 * Without this service, Android kills the CoroutineScope inside
 * [Fido2HomeViewModel] the moment the Activity leaves the foreground,
 * which cuts the HID transport within ~5 seconds (process death grace period).
 *
 * Lifecycle:
 * - Started via [ACTION_START] when the user presses "Start Authenticator".
 * - Stopped via [ACTION_STOP] when the user presses "Stop Authenticator" or
 *   explicitly dismisses the notification.
 */
class Fido2TransportService : Service() {
    companion object {
        const val ACTION_START = "com.chimali.fido2.START_TRANSPORT"
        const val ACTION_STOP = "com.chimali.fido2.STOP_TRANSPORT"

        fun startIntent(context: Context) =
            Intent(context, Fido2TransportService::class.java).apply {
                action = ACTION_START
            }

        fun stopIntent(context: Context) =
            Intent(context, Fido2TransportService::class.java).apply {
                action = ACTION_STOP
            }
    }

    private val transport: Fido2Transport by inject()

    private val uiEventBus: Fido2UiEventBus by inject()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_START -> startTransport()
            ACTION_STOP -> stopTransport()
        }
        return START_STICKY // Restart if killed — keeps the key alive
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // ── Transport lifecycle ────────────────────────────────────────────────────

    private fun startTransport() {
        startForeground(NOTIFICATION_ID, buildAdvertisingNotification())

        // Listen for incoming requests to push a Heads-Up notification if in background
        uiEventBus.events
            .onEach { event ->
                if (!isAppInForeground()) {
                    showAuthRequestNotification(event)
                }
            }
            .launchIn(scope)

        scope.launch {
            val result = transport.connect()
            if (result.isFailure) {
                Timber.e("Transport connect failed: %s", result.exceptionOrNull()?.message)
                stopSelf()
            } else {
                Timber.i("HID transport connected — service running in foreground")
            }
        }
    }

    private fun stopTransport() {
        scope.launch {
            transport.disconnect()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "Chimali Authenticator",
                NotificationManager.IMPORTANCE_LOW, // Silent — no sound/vibration
            ).apply {
                description = "Keeps the virtual security key active"
            }
        nm.createNotificationChannel(channel)

        val authChannel =
            NotificationChannel(
                AUTH_REQUEST_CHANNEL_ID,
                "Authentication Requests",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Notifies you when a passkey is requested"
            }
        nm.createNotificationChannel(authChannel)
    }

    private fun buildAdvertisingNotification(): Notification {
        // Tapping the notification (or the Stop action) will stop the service
        val stopPendingIntent =
            PendingIntent.getService(
                this,
                0,
                stopIntent(this),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Chimali Authenticator")
            .setContentText("Virtual security key is advertising…")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop",
                stopPendingIntent,
            )
            .build()
    }

    private fun isAppInForeground(): Boolean {
        val processInfo = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(processInfo)
        return processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

    private fun showAuthRequestNotification(event: Fido2UiEvent) {
        val (title, text) =
            when (event) {
                is Fido2UiEvent.RegistrationRequested -> "Register Passkey" to "Windows is requesting to register a passkey. Tap to authenticate."
                is Fido2UiEvent.AuthenticationRequested -> "Sign In" to "Windows is requesting a passkey for ${event.rpId}. Tap to authenticate."
            }

        val launchIntent =
            packageManager.getLaunchIntentForPackage(packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            } ?: return

        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val notification =
            NotificationCompat.Builder(this, AUTH_REQUEST_CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setFullScreenIntent(pendingIntent, true)
                .build()

        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(AUTH_REQUEST_NOTIF_ID, notification)
    }
}
