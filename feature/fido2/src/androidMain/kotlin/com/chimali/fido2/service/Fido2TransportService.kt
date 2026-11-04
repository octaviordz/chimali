package com.chimali.fido2.service

import android.Manifest
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import co.touchlab.kermit.Logger
import com.chimali.core.common.annotation.Req
import com.chimali.core.common.result.exceptionOrNull
import com.chimali.core.common.result.isFailure
import com.chimali.fido2.data.transport.Fido2Transport
import com.chimali.fido2.data.transport.ProcessTransportSelection
import com.chimali.fido2.presentation.navigation.Fido2UiEvent
import com.chimali.fido2.presentation.navigation.Fido2UiEventBus
import com.chimali.fido2.presentation.viewmodel.Fido2HomeViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

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
        /** Explicit action that starts the authenticator foreground service. */
        @Req("FR-HID-010", "FR-HID-030")
        const val ACTION_START = "com.chimali.fido2.START_TRANSPORT"

        /** Explicit action that stops the authenticator foreground service. */
        @Req("FR-HID-010", "FR-HID-030")
        const val ACTION_STOP = "com.chimali.fido2.STOP_TRANSPORT"

        @Req("FR-BLE-060", "FR-BLE-070", "FR-BT-SEL-040", "TR-BLE-040")
        const val ACTION_RETRY = "com.chimali.fido2.RETRY_TRANSPORT"

        /** Creates an explicit service intent that starts the foreground authenticator transport. */
        @Req("FR-HID-010", "FR-HID-030")
        fun startIntent(context: Context) =
            Intent(context, Fido2TransportService::class.java).apply {
                action = ACTION_START
            }

        /** Creates an explicit service intent that disconnects and stops the authenticator transport. */
        @Req("FR-HID-010", "FR-HID-030")
        fun stopIntent(context: Context) =
            Intent(context, Fido2TransportService::class.java).apply {
                action = ACTION_STOP
            }

        fun retryIntent(context: Context) =
            Intent(context, Fido2TransportService::class.java).apply {
                action = ACTION_RETRY
            }
    }

    private val transport: Fido2Transport by inject()

    private val uiEventBus: Fido2UiEventBus by inject()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lifecycleMutex = Mutex()
    private var startJob: Job? = null
    private var eventCollectorJob: Job? = null
    private var startRequested = false

    /** Returns no binder; this service is controlled through explicit start and stop intents. */
    @Req("FR-HID-010", "FR-HID-030")
    override fun onBind(intent: Intent?): IBinder? = null

    /** Creates notification channels before the service begins advertising. */
    @Req("FR-HID-010", "FR-HID-030")
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    /** Starts or stops the transport according to the explicit action and requests sticky restart. */
    @Req("FR-HID-010", "FR-HID-030")
    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val action = intent?.action
        if (action != ACTION_STOP && !hasForegroundServicePrerequisites()) {
            Logger.w {
                "Bluetooth permissions are unavailable; stopping transport service without a foreground restart"
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        when (action) {
            ACTION_START -> startTransport()
            ACTION_STOP -> stopTransport()
            ACTION_RETRY -> retryTransport()
            null -> startTransport()
        }
        return START_STICKY // Restart if killed — keeps the key alive
    }

    /** Cancels service work before delegating final cleanup to the Android service lifecycle. */
    @Req("FR-HID-010", "FR-HID-030")
    override fun onDestroy() {
        startRequested = false
        startJob?.cancel()
        eventCollectorJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    // ── Transport lifecycle ────────────────────────────────────────────────────

    private fun startTransport() {
        promoteToForeground()

        if (startRequested) return
        startRequested = true

        // Keep one event collector across repeated ACTION_START intents and retries.
        ensureEventCollector()

        startJob =
            scope.launch {
                lifecycleMutex.withLock {
                    if (!startRequested) return@withLock
                    val result = transport.connect()
                    if (result.isFailure) {
                        Logger.e(result.exceptionOrNull()) { "Transport connect failed" }
                        startRequested = false
                        eventCollectorJob?.cancel()
                        eventCollectorJob = null
                        // Keep the started service shell available for the UI's explicit Retry action.
                        // The selected transport failed to activate, so release the foreground state
                        // but let a later ACTION_RETRY create a fresh activation attempt.
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        Logger.i("FIDO2 transport is active — service running in foreground")
                    }
                }
            }
    }

    private fun stopTransport() {
        scope.launch {
            startJob?.cancelAndJoin()
            lifecycleMutex.withLock {
                if (startRequested) {
                    startRequested = false
                    eventCollectorJob?.cancel()
                    eventCollectorJob = null
                    val result = transport.disconnect()
                    if (result.isFailure) Logger.w(result.exceptionOrNull()) { "Transport disconnect failed" }
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun retryTransport() {
        scope.launch {
            startJob?.cancelAndJoin()
            lifecycleMutex.withLock {
                // A failed start can remove its foreground notification after this retry intent
                // arrives. Promote only after that attempt has fully completed so its cleanup
                // cannot leave a successfully retried transport running without foreground state.
                withContext(Dispatchers.Main.immediate) { promoteToForeground() }
                val disconnectResult = transport.disconnect()
                if (disconnectResult.isFailure) {
                    Logger.w(disconnectResult.exceptionOrNull()) { "Transport cleanup before retry failed" }
                }
                (transport as? ProcessTransportSelection)?.retryActivation()
                startRequested = true
                ensureEventCollector()
                val result = transport.connect()
                if (result.isFailure) {
                    Logger.e(result.exceptionOrNull()) { "Transport retry failed" }
                    startRequested = false
                    eventCollectorJob?.cancel()
                    eventCollectorJob = null
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    private fun ensureEventCollector() {
        if (eventCollectorJob?.isActive == true) return
        eventCollectorJob =
            uiEventBus.events
                .onEach { event ->
                    if (!isAppInForeground()) showAuthRequestNotification(event)
                }.launchIn(scope)
    }

    private fun promoteToForeground() {
        val notification = buildAdvertisingNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun hasForegroundServicePrerequisites(): Boolean {
        val sdkInt = Build.VERSION.SDK_INT
        val hasConnectedDeviceForegroundPermission =
            sdkInt < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
                checkSelfPermission(Manifest.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasBluetoothRuntimePermission =
            sdkInt < Build.VERSION_CODES.S ||
                listOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_SCAN,
                ).any { permission ->
                    checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
                }
        return hasConnectedDeviceForegroundServicePrerequisites(
            sdkInt = sdkInt,
            foregroundServicePermissionGranted = hasConnectedDeviceForegroundPermission,
            bluetoothRuntimePermissionGranted = hasBluetoothRuntimePermission,
        )
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "Chimali Authenticator",
                // Silent — no sound/vibration
                NotificationManager.IMPORTANCE_LOW,
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

        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setContentTitle("Chimali Authenticator")
            .setContentText("Virtual security key is active")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop",
                stopPendingIntent,
            ).build()
    }

    private fun isAppInForeground(): Boolean {
        val processInfo = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(processInfo)
        return processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

    private fun showAuthRequestNotification(event: Fido2UiEvent) {
        val (title, text) =
            when (event) {
                is Fido2UiEvent.RegistrationRequested ->
                    "Register Passkey" to "Windows is requesting to register a passkey. Tap to authenticate."
                is Fido2UiEvent.AuthenticationRequested ->
                    "Sign In" to "Windows is requesting a passkey for ${event.options.rpId.value}. Tap to authenticate."
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
            NotificationCompat
                .Builder(this, AUTH_REQUEST_CHANNEL_ID)
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

internal fun hasConnectedDeviceForegroundServicePrerequisites(
    sdkInt: Int,
    foregroundServicePermissionGranted: Boolean,
    bluetoothRuntimePermissionGranted: Boolean,
): Boolean =
    (sdkInt < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || foregroundServicePermissionGranted) &&
        (sdkInt < Build.VERSION_CODES.S || bluetoothRuntimePermissionGranted)
