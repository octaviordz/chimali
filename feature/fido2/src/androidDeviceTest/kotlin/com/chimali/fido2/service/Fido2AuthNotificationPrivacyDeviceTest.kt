package com.chimali.fido2.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Fido2AuthNotificationPrivacyDeviceTest {
    private companion object {
        const val AUTH_REQUEST_NOTIFICATION_ID = 1002
        const val TEST_CHANNEL_ID = "fido2_auth_notification_cleanup_test"
        const val NOTIFICATION_SETTLE_TIMEOUT_MILLIS = 3_000L
    }

    @Test
    fun authenticationNotificationUsesRedactedPublicVersion() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val rpMarker = "notification-rp-synthetic.example"
        val notification =
            buildAuthenticationRequestNotification(
                context = context,
                title = "Sign In",
                text = "Windows is requesting a passkey for $rpMarker. Tap to authenticate.",
                pendingIntent = null,
            )
        val publicVersion = notification.publicVersion

        assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
        assertNotNull(publicVersion)
        val privateText = notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        val publicTitle = publicVersion.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
        val publicText = publicVersion.extras.getCharSequence(Notification.EXTRA_TEXT).toString()

        assertTrue(privateText.contains(rpMarker))
        assertEquals(AUTH_REQUEST_PUBLIC_TITLE, publicTitle)
        assertEquals(AUTH_REQUEST_PUBLIC_TEXT, publicText)
        assertFalse(publicTitle.contains(rpMarker))
        assertFalse(publicText.contains(rpMarker))
    }

    @Test
    fun cleanupRemovesSyntheticAuthenticationNotificationFromAndroidManager() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val notificationPermissionGranted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        try {
            assumeTrue("POST_NOTIFICATIONS must be granted by the test harness", notificationPermissionGranted)
            assumeTrue(
                "Notifications are disabled for the test app",
                notificationManager.areNotificationsEnabled(),
            )
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    TEST_CHANNEL_ID,
                    "Synthetic test",
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
            val notificationBuilder = Notification.Builder(context, TEST_CHANNEL_ID)
            notificationBuilder.setSmallIcon(android.R.drawable.ic_dialog_info)
            notificationBuilder.setContentTitle("Synthetic FIDO notification")
            notificationBuilder.setContentText("Synthetic process-death cleanup marker")
            val syntheticNotification = notificationBuilder.build()
            notificationManager.notify(AUTH_REQUEST_NOTIFICATION_ID, syntheticNotification)

            assertTrue(
                "Synthetic notification was not posted",
                awaitAuthenticationNotification(notificationManager, shouldBeActive = true),
            )

            clearStaleAuthenticationRequestNotification(context)

            val remainingAuthenticationNotifications =
                notificationManager.activeNotifications.filter { it.id == AUTH_REQUEST_NOTIFICATION_ID }
            assertTrue(
                "Fresh-process cleanup left notifications active: " +
                    remainingAuthenticationNotifications.joinToString { "${it.packageName}/${it.id}/${it.tag}" },
                remainingAuthenticationNotifications.isEmpty(),
            )
        } finally {
            notificationManager.cancel(AUTH_REQUEST_NOTIFICATION_ID)
            notificationManager.deleteNotificationChannel(TEST_CHANNEL_ID)
        }
    }

    private fun awaitAuthenticationNotification(
        notificationManager: NotificationManager,
        shouldBeActive: Boolean,
    ): Boolean {
        val deadline = SystemClock.elapsedRealtime() + NOTIFICATION_SETTLE_TIMEOUT_MILLIS
        do {
            val isActive = notificationManager.activeNotifications.any { it.id == AUTH_REQUEST_NOTIFICATION_ID }
            if (isActive == shouldBeActive) return true
            SystemClock.sleep(25)
        } while (SystemClock.elapsedRealtime() < deadline)
        return false
    }
}
