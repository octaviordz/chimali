package com.chimali.fido2.presentation.error

import com.chimali.fido2.domain.exception.Fido2Exception
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Fido2ErrorHandlerTest {
    @Test
    fun `handle maps connection exceptions to user friendly messages`() {
        // T152 — Verify connection issues map to clear, readable messages
        val exception = Fido2Exception.ConnectionException("BT disconnected")
        val ui = Fido2ErrorHandler.handle(exception)

        assertEquals("Connection lost", ui.title)
        assertEquals("The Bluetooth connection was interrupted. Move closer and try again.", ui.message)
        assertTrue(ui.isRetryable)
        assertEquals(0x07, ui.ctap2ErrorCode)
    }

    @Test
    fun `handle maps biometric failures correctly`() {
        // T149 — Verify FIDO2 protocol operation denied (0x29) is handled clearly
        val exception = Fido2Exception.UserVerificationFailed("Too many attempts")
        val ui = Fido2ErrorHandler.handle(exception)

        assertEquals("Verification failed", ui.title)
        assertEquals("Too many attempts", ui.message)
        assertTrue(ui.isRetryable)
        assertEquals(0x29, ui.ctap2ErrorCode) // CTAP2_ERR_OPERATION_DENIED
    }

    @Test
    fun `handle maps unsupported algorithms directly`() {
        val exception = Fido2Exception.UnsupportedAlgorithmException("ES256 not supported")
        val ui = Fido2ErrorHandler.handle(exception)

        assertEquals("Unsupported algorithm", ui.title)
        assertEquals("This site requested a cryptographic algorithm not supported by this device.", ui.message)
        assertFalse(ui.isRetryable)
        assertEquals(0x26, ui.ctap2ErrorCode) // CTAP2_ERR_UNSUPPORTED_ALGORITHM
    }
}
