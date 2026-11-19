package com.chimali.fido2.service

import android.app.NotificationManager
import android.content.Context
import com.chimali.core.domain.valueobject.RpId
import com.chimali.fido2.domain.model.GetAssertionOptions
import com.chimali.fido2.presentation.navigation.Fido2UiEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred

class Fido2TransportServiceNotificationPrivacyTest {
    @Test
    fun freshProcessStartupCleanupCancelsOnlyTheAuthenticationNotificationId() {
        val context = mockk<Context>()
        val notificationManager = mockk<NotificationManager>(relaxed = true)
        every { context.getSystemService(Context.NOTIFICATION_SERVICE) } returns notificationManager

        clearStaleAuthenticationRequestNotification(context)

        verify(exactly = 1) { notificationManager.cancel(1002) }
    }

    @Test
    fun authenticationNotificationKeepsRpOnlyInPrivateVersion() {
        val rpMarker = "notification-rp-synthetic.example"
        val options = GetAssertionOptions(RpId(rpMarker), ByteArray(32), allowCredentials = null)
        val event = Fido2UiEvent.AuthenticationRequested(options, CompletableDeferred())

        try {
            val (privateTitle, privateText) = authRequestNotificationContent(event)
            val publicText = "$AUTH_REQUEST_PUBLIC_TITLE $AUTH_REQUEST_PUBLIC_TEXT"

            assertEquals("Sign In", privateTitle)
            assertTrue(privateText.contains(rpMarker))
            assertFalse(publicText.contains(rpMarker))
            assertEquals("Chimali Authenticator", AUTH_REQUEST_PUBLIC_TITLE)
            assertEquals("A passkey request is waiting. Unlock Chimali to review it.", AUTH_REQUEST_PUBLIC_TEXT)
        } finally {
            options.eraseSensitiveText()
            options.clientDataHash.fill(0)
        }
    }

    @Test
    fun completionDismissesOnlyTheNotificationOwnedByThatRequest() {
        val tracker = AuthenticationNotificationRequestTracker()
        val firstRequest = CompletableDeferred<Unit>()
        val replacementRequest = CompletableDeferred<Unit>()
        val published = mutableListOf<String>()
        val removed = mutableListOf<String>()

        assertTrue(tracker.trackAndPublish(firstRequest) { published += "first" })
        assertTrue(tracker.trackAndPublish(replacementRequest) { published += "replacement" })

        assertFalse(tracker.completeAndRemove(firstRequest) { removed += "first" })
        assertTrue(tracker.completeAndRemove(replacementRequest) { removed += "replacement" })
        assertFalse(tracker.completeAndRemove(replacementRequest) { removed += "replacement-again" })
        val alreadyCompletedRequest = CompletableDeferred<Unit>().apply { complete(Unit) }
        assertFalse(tracker.trackAndPublish(alreadyCompletedRequest) { published += "completed" })
        assertEquals(listOf("first", "replacement"), published)
        assertEquals(listOf("replacement"), removed)
    }

    @Test
    fun serviceShutdownReleasesThePendingNotificationOwner() {
        val tracker = AuthenticationNotificationRequestTracker()
        val request = CompletableDeferred<Unit>()
        val published = mutableListOf<String>()
        val removed = mutableListOf<String>()

        assertTrue(tracker.trackAndPublish(request) { published += "request" })

        tracker.shutdown { removed += "shutdown" }

        assertFalse(tracker.trackAndPublish(CompletableDeferred<Unit>()) { published += "late" })
        assertFalse(tracker.completeAndRemove(request) { removed += "completed" })
        assertEquals(listOf("request"), published)
        assertEquals(listOf("shutdown"), removed)
    }
}
