package com.chimali.fido2.presentation.error

import com.chimali.fido2.domain.exception.Fido2Exception

/**
 * T149 / T152 — FIDO2 error handler.
 *
 * Maps [Fido2Exception] subclasses to user-facing messages and retry recommendations.
 * Keeps the ViewModel clean of string resources by centralising all error classification logic.
 */
object Fido2ErrorHandler {
    data class ErrorUi(
        val title: String,
        val message: String,
        val isRetryable: Boolean,
        val ctap2ErrorCode: Int? = null, // CTAP2 error code for transport layer
    )

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
                    ctap2ErrorCode = 0x29, // CTAP2_ERR_OPERATION_DENIED
                )

            is Fido2Exception.NoVerificationMethodAvailable ->
                ErrorUi(
                    title = "No verification method",
                    message = "No biometric or PIN is set up on this device. Please configure one in Settings.",
                    isRetryable = false,
                    ctap2ErrorCode = 0x26, // CTAP2_ERR_PIN_NOT_SET
                )

            is Fido2Exception.ConsentDenied ->
                ErrorUi(
                    title = "Registration cancelled",
                    message = "You declined to create a passkey. Tap the site's 'Create passkey' button to try again.",
                    isRetryable = false,
                    ctap2ErrorCode = 0x27, // CTAP2_ERR_PIN_POLICY_VIOLATION
                )

            // ── Credential conflicts ───────────────────────────────────────────────
            is Fido2Exception.CredentialCreationNotAllowed ->
                ErrorUi(
                    title = "Passkey not allowed",
                    message = error.message ?: "This site doesn't allow creating a new passkey at this time.",
                    isRetryable = false,
                    ctap2ErrorCode = 0x22, // CTAP2_ERR_CREDENTIAL_EXCLUDED
                )

            is Fido2Exception.DuplicateCredentialException ->
                ErrorUi(
                    title = "Passkey already exists",
                    message = "A passkey for this account already exists on this device.",
                    isRetryable = false,
                    ctap2ErrorCode = 0x22, // CTAP2_ERR_CREDENTIAL_EXCLUDED
                )

            is Fido2Exception.TooManyCredentials ->
                ErrorUi(
                    title = "Too many passkeys",
                    message = "This device's passkey storage is full. Remove unused passkeys and try again.",
                    isRetryable = false,
                    ctap2ErrorCode = 0x49, // CTAP2_ERR_KEY_STORE_FULL
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
                    ctap2ErrorCode = 0x17, // CTAP2_ERR_PROCESSING
                )

            is Fido2Exception.UnsupportedAlgorithmException,
            is Fido2Exception.UnsupportedAlgorithm,
            ->
                ErrorUi(
                    title = "Unsupported algorithm",
                    message = "This site requested a cryptographic algorithm not supported by this device.",
                    isRetryable = false,
                    ctap2ErrorCode = 0x26, // CTAP2_ERR_UNSUPPORTED_ALGORITHM
                )

            // ── Storage ────────────────────────────────────────────────────────────
            is Fido2Exception.CredentialStorageFailed,
            is Fido2Exception.StorageException,
            is Fido2Exception.DatabaseException,
            ->
                ErrorUi(
                    title = "Storage error",
                    message = "Could not save your passkey. ${error.message ?: "Please check available storage and try again."}",
                    isRetryable = true,
                    ctap2ErrorCode = 0x27, // CTAP2_ERR_PIN_POLICY_VIOLATION
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
                    ctap2ErrorCode = 0x07, // CTAP2_ERR_TIMEOUT
                )

            // ── Registration wrapper ───────────────────────────────────────────────
            is Fido2Exception.RegistrationFailed ->
                ErrorUi(
                    title = "Registration failed",
                    message = error.message ?: "Passkey registration failed. Please try again.",
                    isRetryable = true,
                    ctap2ErrorCode = 0x17, // CTAP2_ERR_PROCESSING
                )

            // ── Generic fallback ───────────────────────────────────────────────────
            else ->
                ErrorUi(
                    title = "Unexpected error",
                    message = error.message ?: "An unexpected error occurred. Please try again.",
                    isRetryable = true,
                    ctap2ErrorCode = 0x7F, // CTAP1_ERR_OTHER
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
