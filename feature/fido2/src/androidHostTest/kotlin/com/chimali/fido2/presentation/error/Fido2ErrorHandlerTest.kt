package com.chimali.fido2.presentation.error

import com.chimali.core.common.result.DomainError
import com.chimali.fido2.domain.exception.Fido2Exception
import kotlin.test.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

class Fido2ErrorHandlerTest {
    private companion object {
        private const val ERR_CONNECTION = 0x07
        private const val ERR_SECURITY = 0x17
        private const val ERR_CREDENTIAL_EXCLUDED = 0x22
        private const val ERR_UNSUPPORTED_ALGORITHM = 0x26
        private const val ERR_PIN_POLICY = 0x27
        private const val ERR_OPERATION_DENIED = 0x29
        private const val ERR_NOT_FOUND = 0x2E
        private const val ERR_KEY_STORE_FULL = 0x49
        private const val ERR_UNEXPECTED = 0x7F
    }

    @Test
    fun `handle maps connection exceptions to user friendly messages`() {
        val exception = Fido2Exception.ConnectionException("BT disconnected")
        val ui = Fido2ErrorHandler.handle(exception)

        assertEquals("Connection lost", ui.title)
        assertEquals("The Bluetooth connection was interrupted. Move closer and try again.", ui.message)
        assertTrue(ui.isRetryable)
        assertEquals(ERR_CONNECTION, ui.ctap2ErrorCode)
    }

    @Test
    fun `handle maps biometric failures correctly`() {
        val exception = Fido2Exception.UserVerificationFailed("Too many attempts")
        val ui = Fido2ErrorHandler.handle(exception)

        assertEquals("Verification failed", ui.title)
        assertEquals("Too many attempts", ui.message)
        assertTrue(ui.isRetryable)
        assertEquals(ERR_OPERATION_DENIED, ui.ctap2ErrorCode)
    }

    @Test
    fun `handle maps unsupported algorithms directly`() {
        val exception = Fido2Exception.UnsupportedAlgorithmException("ES256 not supported")
        val ui = Fido2ErrorHandler.handle(exception)

        assertEquals("Unsupported algorithm", ui.title)
        assertEquals("This site requested a cryptographic algorithm not supported by this device.", ui.message)
        assertFalse(ui.isRetryable)
        assertEquals(ERR_UNSUPPORTED_ALGORITHM, ui.ctap2ErrorCode)
    }

    @Test
    fun `handle maps domain errors exhaustively`() {
        val denied = Fido2ErrorHandler.handle(DomainError.OperationDenied("User denied"))
        assertEquals("Operation denied", denied.title)
        assertEquals("User denied", denied.message)
        assertEquals(ERR_OPERATION_DENIED, denied.ctap2ErrorCode)

        val notFound = Fido2ErrorHandler.handle(DomainError.NotFound("Credential not found"))
        assertEquals("Not found", notFound.title)
        assertEquals(ERR_NOT_FOUND, notFound.ctap2ErrorCode)

        val dbError = Fido2ErrorHandler.handle(DomainError.DatabaseError("Disk full"))
        assertEquals("Database error", dbError.title)
        assertEquals(ERR_SECURITY, dbError.ctap2ErrorCode)

        val cryptoError = Fido2ErrorHandler.handle(DomainError.CryptoError("Key corrupt"))
        assertEquals("Security error", cryptoError.title)
        assertEquals(ERR_SECURITY, cryptoError.ctap2ErrorCode)

        val netError = Fido2ErrorHandler.handle(DomainError.NetworkError("Packet drop"))
        assertEquals("Connection error", netError.title)
        assertEquals(ERR_CONNECTION, netError.ctap2ErrorCode)

        val other = Fido2ErrorHandler.handle(DomainError.ValidationError("Validation failed"))
        assertEquals("Unexpected error", other.title)
        assertEquals(ERR_UNEXPECTED, other.ctap2ErrorCode)
    }

    @Test
    fun `handle maps all remaining fido2 exceptions and fallbacks`() {
        val noMethod = Fido2ErrorHandler.handle(Fido2Exception.NoVerificationMethodAvailable())
        assertEquals("No verification method", noMethod.title)
        assertFalse(noMethod.isRetryable)

        val consentDenied = Fido2ErrorHandler.handle(Fido2Exception.ConsentDenied("User declined"))
        assertEquals("Registration cancelled", consentDenied.title)
        assertEquals(ERR_PIN_POLICY, consentDenied.ctap2ErrorCode)

        val excluded = Fido2ErrorHandler.handle(Fido2Exception.CredentialCreationNotAllowed("Excluded"))
        assertEquals("Passkey not allowed", excluded.title)
        assertEquals(ERR_CREDENTIAL_EXCLUDED, excluded.ctap2ErrorCode)

        val dup = Fido2ErrorHandler.handle(Fido2Exception.DuplicateCredentialException("Already exists"))
        assertEquals("Passkey already exists", dup.title)
        assertEquals(ERR_CREDENTIAL_EXCLUDED, dup.ctap2ErrorCode)

        val full = Fido2ErrorHandler.handle(Fido2Exception.TooManyCredentials(100))
        assertEquals("Too many passkeys", full.title)
        assertEquals(ERR_KEY_STORE_FULL, full.ctap2ErrorCode)

        val keyGen = Fido2ErrorHandler.handle(Fido2Exception.KeyGenerationFailed("Keygen failed"))
        assertEquals("Security error", keyGen.title)
        assertEquals(ERR_SECURITY, keyGen.ctap2ErrorCode)

        val storage = Fido2ErrorHandler.handle(Fido2Exception.CredentialStorageFailed("Storage failed"))
        assertEquals("Storage error", storage.title)
        assertEquals(ERR_PIN_POLICY, storage.ctap2ErrorCode)

        val transport = Fido2ErrorHandler.handle(Fido2Exception.TransportException("Timeout"))
        assertEquals("Connection lost", transport.title)
        assertEquals(ERR_CONNECTION, transport.ctap2ErrorCode)

        val regFailed = Fido2ErrorHandler.handle(Fido2Exception.RegistrationFailed("Registration aborted"))
        assertEquals("Registration failed", regFailed.title)
        assertEquals(ERR_SECURITY, regFailed.ctap2ErrorCode)

        val generic = Fido2ErrorHandler.handle(RuntimeException("Random error"))
        assertEquals("Unexpected error", generic.title)
        assertEquals(ERR_UNEXPECTED, generic.ctap2ErrorCode)
    }

    @Test
    fun `isSilentRetryable classifies transport and connection errors`() {
        assertTrue(Fido2ErrorHandler.isSilentRetryable(Fido2Exception.TransportException("Timeout")))
        assertTrue(Fido2ErrorHandler.isSilentRetryable(Fido2Exception.ConnectionException("Dropped")))
        assertFalse(Fido2ErrorHandler.isSilentRetryable(Fido2Exception.UserVerificationFailed("Failed")))
        assertFalse(Fido2ErrorHandler.isSilentRetryable(RuntimeException("Other")))
    }
}
