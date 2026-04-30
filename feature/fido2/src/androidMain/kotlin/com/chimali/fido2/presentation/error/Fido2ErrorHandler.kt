package com.chimali.fido2.presentation.error

import com.chimali.core.common.result.DomainError
import com.chimali.fido2.domain.exception.Fido2Exception

/**
 * T149 / T152 — FIDO2 error handler.
 *
 * Maps [Fido2Exception] and [DomainError] subclasses to user-facing messages and retry recommendations.
 * Keeps the ViewModel clean of string resources by centralising all error classification logic.
 */
object Fido2ErrorHandler {
    data class ErrorUi(
        val title: String,
        val message: String,
        val isRetryable: Boolean,
        // CTAP2 error code for transport layer
        val ctap2ErrorCode: Int? = null,
    )

    /**
     * Maps a DomainError to UI-renderable [ErrorUi].
     */
    fun handle(error: DomainError): ErrorUi =
        when (error) {
            is DomainError.OperationDenied ->
                ErrorUi(
                    title = "Operation denied",
                    message = error.message,
                    isRetryable = true,
                    ctap2ErrorCode = 0x29,
                )

            is DomainError.NotFound ->
                ErrorUi(
                    title = "Not found",
                    message = error.message,
                    isRetryable = false,
                    ctap2ErrorCode = 0x2E,
                )

            is DomainError.DatabaseError ->
                ErrorUi(
                    title = "Database error",
                    message = "Could not access local storage. ${error.message}",
                    isRetryable = true,
                    ctap2ErrorCode = 0x17,
                )

            is DomainError.CryptoError ->
                ErrorUi(
                    title = "Security error",
                    message = "Could not perform cryptographic operation. ${error.message}",
                    isRetryable = true,
                    ctap2ErrorCode = 0x17,
                )

            is DomainError.NetworkError ->
                ErrorUi(
                    title = "Connection error",
                    message = "Communication with the host was interrupted. ${error.message}",
                    isRetryable = true,
                    ctap2ErrorCode = 0x07,
                )

            else ->
                ErrorUi(
                    title = "Unexpected error",
                    message = error.message,
                    isRetryable = true,
                    ctap2ErrorCode = 0x7F,
                )
        }

    /**
     * Maps a throwable (expected to be [Fido2Exception]) to UI-renderable [ErrorUi].
     */
    fun handle(error: Throwable): ErrorUi =
        when (error) {
            // ── User verification ──────────────────────────────────────────────────
            is Fido2Exception.UserVerificationFailed ->
                ErrorUi(
                    title = "Verification failed",
                    message = error.message ?: "Could not verify your identity. Please try again.",
                    isRetryable = true,
                    // CTAP2_ERR_OPERATION_DENIED
                    ctap2ErrorCode = 0x29,
                )

            is Fido2Exception.NoVerificationMethodAvailable ->
                ErrorUi(
                    title = "No verification method",
                    message = "No biometric or PIN is set up on this device. Please configure one in Settings.",
                    isRetryable = false,
                    // CTAP2_ERR_PIN_NOT_SET
                    ctap2ErrorCode = 0x26,
                )

            is Fido2Exception.ConsentDenied ->
                ErrorUi(
                    title = "Registration cancelled",
                    message = "You declined to create a passkey. Tap the site's 'Create passkey' button to try again.",
                    isRetryable = false,
                    // CTAP2_ERR_PIN_POLICY_VIOLATION
                    ctap2ErrorCode = 0x27,
                )

            // ── Credential conflicts ───────────────────────────────────────────────
            is Fido2Exception.CredentialCreationNotAllowed ->
                ErrorUi(
                    title = "Passkey not allowed",
                    message = error.message ?: "This site doesn't allow creating a new passkey at this time.",
                    isRetryable = false,
                    // CTAP2_ERR_CREDENTIAL_EXCLUDED
                    ctap2ErrorCode = 0x22,
                )

            is Fido2Exception.DuplicateCredentialException ->
                ErrorUi(
                    title = "Passkey already exists",
                    message = "A passkey for this account already exists on this device.",
                    isRetryable = false,
                    // CTAP2_ERR_CREDENTIAL_EXCLUDED
                    ctap2ErrorCode = 0x22,
                )

            is Fido2Exception.TooManyCredentials ->
                ErrorUi(
                    title = "Too many passkeys",
                    message = "This device's passkey storage is full. Remove unused passkeys and try again.",
                    isRetryable = false,
                    // CTAP2_ERR_KEY_STORE_FULL
                    ctap2ErrorCode = 0x49,
                )

            // ── Cryptographic ──────────────────────────────────────────────────────
            is Fido2Exception.KeyGenerationFailed,
            is Fido2Exception.KeyGenerationException,
            is Fido2Exception.CryptographicException,
            ->
                ErrorUi(
                    title = "Security error",
                    message = "Could not generate a secure key for your passkey. Please try again.",
                    isRetryable = true,
                    // CTAP2_ERR_PROCESSING
                    ctap2ErrorCode = 0x17,
                )

            is Fido2Exception.UnsupportedAlgorithmException,
            is Fido2Exception.UnsupportedAlgorithm,
            ->
                ErrorUi(
                    title = "Unsupported algorithm",
                    message = "This site requested a cryptographic algorithm not supported by this device.",
                    isRetryable = false,
                    // CTAP2_ERR_UNSUPPORTED_ALGORITHM
                    ctap2ErrorCode = 0x26,
                )

            // ── Storage ────────────────────────────────────────────────────────────
            is Fido2Exception.CredentialStorageFailed,
            is Fido2Exception.StorageException,
            is Fido2Exception.DatabaseException,
            ->
                ErrorUi(
                    title = "Storage error",
                    message =
                        "Could not save your passkey. " +
                            "${error.message ?: "Please check available storage and try again."}",
                    isRetryable = true,
                    // CTAP2_ERR_PIN_POLICY_VIOLATION
                    ctap2ErrorCode = 0x27,
                )

            // ── Timeout / transport ────────────────────────────────────────────────
            is Fido2Exception.TransportException,
            is Fido2Exception.BluetoothException,
            is Fido2Exception.ConnectionException,
            ->
                ErrorUi(
                    title = "Connection lost",
                    message = "The Bluetooth connection was interrupted. Move closer and try again.",
                    isRetryable = true,
                    // CTAP2_ERR_TIMEOUT
                    ctap2ErrorCode = 0x07,
                )

            // ── Registration wrapper ───────────────────────────────────────────────
            is Fido2Exception.RegistrationFailed ->
                ErrorUi(
                    title = "Registration failed",
                    message = error.message ?: "Passkey registration failed. Please try again.",
                    isRetryable = true,
                    // CTAP2_ERR_PROCESSING
                    ctap2ErrorCode = 0x17,
                )

            // ── Generic fallback ───────────────────────────────────────────────────
            else ->
                ErrorUi(
                    title = "Unexpected error",
                    message = error.message ?: "An unexpected error occurred. Please try again.",
                    isRetryable = true,
                    // CTAP1_ERR_OTHER
                    ctap2ErrorCode = 0x7F,
                )
        }

    /**
     * Returns true if the error should be silently retried without user interaction.
     * Currently only [Fido2Exception.TransportException] qualifies.
     */
    fun isSilentRetryable(error: Throwable): Boolean =
        error is Fido2Exception.TransportException ||
            error is Fido2Exception.ConnectionException
}
