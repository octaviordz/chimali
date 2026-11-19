package com.chimali.fido2.domain.exception

/**
 * Sealed hierarchy of all FIDO2 authenticator exceptions.
 *
 * Naming convention: every leaf class is a direct companion to the operation that fails.
 * The [errorCode] is a stable machine-readable identifier used for logging and CTAP2 error mapping.
 */
sealed class Fido2Exception(
    message: String,
    cause: Throwable? = null,
    val errorCode: String? = null,
) : Exception(message, cause) {
    // ── Cryptographic ─────────────────────────────────────────────────────────

    class CryptographicException(
        message: String,
        cause: Throwable? = null,
    ) : Fido2Exception(message, cause, "CRYPTO_ERROR")

    class KeyGenerationException(
        message: String,
        cause: Throwable? = null,
    ) : Fido2Exception(message, cause, "KEY_GEN_ERROR")

    /** Legacy key-generation alias retained for compatibility; also used by RegisterCredentialUseCase. */
    class KeyGenerationFailed(
        message: String,
        cause: Throwable? = null,
    ) : Fido2Exception(message, cause, "KEY_GEN_FAILED")

    class SignatureException(
        message: String,
        cause: Throwable? = null,
    ) : Fido2Exception(message, cause, "SIGNATURE_ERROR")

    /** Legacy crypto-service alias retained for compatibility. */
    class SignatureFailed(
        message: String,
        cause: Throwable? = null,
    ) : Fido2Exception(message, cause, "SIGNATURE_FAILED")

    /** Used by Fido2CryptoService / GetAssertionUseCase for HDK-based signing failures. */
    class SigningFailed(
        message: String,
        cause: Throwable? = null,
    ) : Fido2Exception(message, cause, "SIGNING_FAILED")

    /** Legacy crypto-service alias retained for compatibility. */
    class SignatureVerificationFailed(
        message: String,
        cause: Throwable? = null,
    ) : Fido2Exception(message, cause, "SIGNATURE_VERIFY_FAILED")

    class UnsupportedAlgorithmException(
        message: String,
        cause: Throwable? = null,
    ) : Fido2Exception(message, cause, "UNSUPPORTED_ALGORITHM")

    /** Legacy key-generation alias retained for compatibility; also used by RegisterCredentialUseCase. */
    @Suppress("UNUSED_PARAMETER")
    class UnsupportedAlgorithm(
        algorithm: String,
        cause: Throwable? = null,
    ) : Fido2Exception("Unsupported algorithm", cause, "UNSUPPORTED_ALGORITHM")

    /** Used by RegisterCredentialUseCase. */
    @Suppress("UNUSED_PARAMETER")
    class UnsupportedCurve(
        curve: String,
        cause: Throwable? = null,
    ) : Fido2Exception("Unsupported elliptic curve", cause, "UNSUPPORTED_CURVE")

    // ── Key Storage ───────────────────────────────────────────────────────────

    class KeyStoreException(
        message: String,
        cause: Throwable? = null,
    ) : Fido2Exception(message, cause, "KEYSTORE_ERROR")

    /** Legacy crypto-service alias retained for compatibility. */
    class KeyStorageFailed(
        message: String,
        cause: Throwable? = null,
    ) : Fido2Exception(message, cause, "KEY_STORAGE_FAILED")

    /** Legacy crypto-service alias retained for compatibility. */
    @Suppress("UNUSED_PARAMETER")
    class KeyNotFound(
        alias: String,
        cause: Throwable? = null,
    ) : Fido2Exception("Key not found", cause, "KEY_NOT_FOUND")

    /** Legacy crypto-service alias retained for compatibility. */
    class KeyDeletionFailed(
        message: String,
        cause: Throwable? = null,
    ) : Fido2Exception(message, cause, "KEY_DELETION_FAILED")

    /** Legacy crypto-service alias retained for compatibility. */
    class KeyRotationFailed(
        message: String,
        cause: Throwable? = null,
    ) : Fido2Exception(message, cause, "KEY_ROTATION_FAILED")

    /** Legacy crypto-service alias retained for compatibility. */
    class KeyDerivationFailed(
        message: String,
        cause: Throwable? = null,
    ) : Fido2Exception(message, cause, "KEY_DERIVATION_FAILED")

    /** Legacy crypto-service alias retained for compatibility. */
    class KeyCleanupFailed(
        message: String,
        cause: Throwable? = null,
    ) : Fido2Exception(message, cause, "KEY_CLEANUP_FAILED")

    /** Legacy crypto-service alias retained for compatibility. */
    class KeyExportFailed(
        message: String,
        cause: Throwable? = null,
    ) : Fido2Exception(message, cause, "KEY_EXPORT_FAILED")

    /** Legacy crypto-service alias retained for compatibility. */
    class KeyImportFailed(
        message: String,
        cause: Throwable? = null,
    ) : Fido2Exception(message, cause, "KEY_IMPORT_FAILED")

    // ── Encryption / Decryption ───────────────────────────────────────────────

    /** Legacy crypto-service alias retained for compatibility. */
    class EncryptionFailed(
        message: String,
        cause: Throwable? = null,
    ) : Fido2Exception(message, cause, "ENCRYPTION_FAILED")

    /** Legacy crypto-service alias retained for compatibility. */
    class DecryptionFailed(
        message: String,
        cause: Throwable? = null,
    ) : Fido2Exception(message, cause, "DECRYPTION_FAILED")

    /** Legacy crypto-service alias retained for compatibility. */
    class InvalidEncryptedData(
        message: String,
        cause: Throwable? = null,
    ) : Fido2Exception(message, cause, "INVALID_ENCRYPTED_DATA")

    /** Legacy crypto-service alias retained for compatibility. */
    @Suppress("UNUSED_PARAMETER")
    class RpIdMismatch(
        expected: String,
        actual: String,
        cause: Throwable? = null,
    ) : Fido2Exception("RP-ID mismatch", cause, "RP_ID_MISMATCH")

    // ── Database / Storage ────────────────────────────────────────────────────

    class StorageException(
        message: String,
        cause: Throwable? = null,
    ) : Fido2Exception(message, cause, "STORAGE_ERROR")

    class DatabaseException(
        message: String,
        cause: Throwable? = null,
    ) : Fido2Exception(message, cause, "DATABASE_ERROR")

    // ── Credential lifecycle ──────────────────────────────────────────────────

    class CredentialException(
        message: String,
        cause: Throwable? = null,
    ) : Fido2Exception(message, cause, "CREDENTIAL_ERROR")

    @Suppress("UNUSED_PARAMETER")
    class CredentialNotFoundException(
        credentialId: String,
        cause: Throwable? = null,
    ) : Fido2Exception("Credential not found", cause, "CREDENTIAL_NOT_FOUND")

    @Suppress("UNUSED_PARAMETER")
    class DuplicateCredentialException(
        rpId: String,
        cause: Throwable? = null,
    ) : Fido2Exception("Duplicate credential for RP", cause, "DUPLICATE_CREDENTIAL")

    /** Alias used by CredentialRepositoryImpl / RegisterCredentialUseCase. */
    class CredentialStorageFailed(
        message: String,
        cause: Throwable? = null,
    ) : Fido2Exception(message, cause, "CREDENTIAL_STORAGE_FAILED")

    /** Alias used by CredentialRepositoryImpl. */
    @Suppress("UNUSED_PARAMETER")
    class CredentialNotFound(
        credentialId: String,
        cause: Throwable? = null,
    ) : Fido2Exception("Credential not found", cause, "CREDENTIAL_NOT_FOUND")

    /** Alias used by CredentialRepositoryImpl. */
    class CredentialUpdateFailed(
        message: String,
        cause: Throwable? = null,
    ) : Fido2Exception(message, cause, "CREDENTIAL_UPDATE_FAILED")

    /** Alias used by CredentialRepositoryImpl. */
    class CredentialDeletionFailed(
        message: String,
        cause: Throwable? = null,
    ) : Fido2Exception(message, cause, "CREDENTIAL_DELETION_FAILED")

    /** Alias used by CredentialRepositoryImpl. */
    class CredentialCleanupFailed(
        message: String,
        cause: Throwable? = null,
    ) : Fido2Exception(message, cause, "CREDENTIAL_CLEANUP_FAILED")

    /** Alias used by CredentialRepositoryImpl / RegisterCredentialUseCase. */
    class CredentialCreationNotAllowed(
        message: String,
        cause: Throwable? = null,
    ) : Fido2Exception(message, cause, "CREDENTIAL_CREATION_NOT_ALLOWED")

    /** Alias used by RegisterCredentialUseCase. */
    class CredentialGenerationFailed(
        message: String,
        cause: Throwable? = null,
    ) : Fido2Exception(message, cause, "CREDENTIAL_GENERATION_FAILED")

    /** Alias used by CredentialRepositoryImpl. */
    class TooManyCredentials(
        val limit: Int,
        cause: Throwable? = null,
    ) : Fido2Exception("Credential limit exceeded: max=$limit", cause, "TOO_MANY_CREDENTIALS")

    // ── Relying Party ─────────────────────────────────────────────────────────

    class RelyingPartyException(
        message: String,
        cause: Throwable? = null,
    ) : Fido2Exception(message, cause, "RP_ERROR")

    /** Alias used by CredentialRepositoryImpl. */
    @Suppress("UNUSED_PARAMETER")
    class RelyingPartyBlocked(
        rpId: String,
        cause: Throwable? = null,
    ) : Fido2Exception("Relying party is blocked", cause, "RP_BLOCKED")

    /** Alias used by CredentialRepositoryImpl. */
    class RelyingPartyUpdateFailed(
        message: String,
        cause: Throwable? = null,
    ) : Fido2Exception(message, cause, "RP_UPDATE_FAILED")

    // ── User Consent ──────────────────────────────────────────────────────────

    /** Alias used by GetUserConsentUseCase / RegisterCredentialUseCase. */
    class ConsentStorageFailed(
        message: String,
        cause: Throwable? = null,
    ) : Fido2Exception(message, cause, "CONSENT_STORAGE_FAILED")

    /** Alias used by CredentialRepositoryImpl. */
    class ConsentDeletionFailed(
        message: String,
        cause: Throwable? = null,
    ) : Fido2Exception(message, cause, "CONSENT_DELETION_FAILED")

    /** Alias used by GetUserConsentUseCase. */
    class ConsentOperationFailed(
        message: String,
        cause: Throwable? = null,
    ) : Fido2Exception(message, cause, "CONSENT_OPERATION_FAILED")

    /** Alias used by RegisterCredentialUseCase. */
    class ConsentDenied(
        message: String,
        cause: Throwable? = null,
    ) : Fido2Exception(message, cause, "CONSENT_DENIED")

    // ── User Verification ─────────────────────────────────────────────────────

    class UserVerificationException(
        message: String,
        cause: Throwable? = null,
    ) : Fido2Exception(message, cause, "USER_VERIFICATION_ERROR")

    /** Alias used by GetUserConsentUseCase / RegisterCredentialUseCase. */
    class UserVerificationFailed(
        message: String,
        cause: Throwable? = null,
    ) : Fido2Exception(message, cause, "USER_VERIFICATION_FAILED")

    /** Alias used by GetUserConsentUseCase / RegisterCredentialUseCase. */
    class NoVerificationMethodAvailable(
        override val message: String = "No applicable user verification method",
    ) : Fido2Exception(message, null, "NO_VERIFICATION_METHOD")

    class InvalidParameter(
        message: String,
        cause: Throwable? = null,
    ) : Fido2Exception(message, cause, "INVALID_PARAMETER")

    class MissingParameterException(
        message: String,
        cause: Throwable? = null,
    ) : Fido2Exception(message, cause, "MISSING_PARAMETER")

    class InvalidFormatException(
        message: String,
        cause: Throwable? = null,
    ) : Fido2Exception(message, cause, "INVALID_FORMAT")

    class BiometricException(
        message: String,
        cause: Throwable? = null,
    ) : Fido2Exception(message, cause, "BIOMETRIC_ERROR")

    class DeviceLockException(
        message: String,
        cause: Throwable? = null,
    ) : Fido2Exception(message, cause, "DEVICE_LOCK_ERROR")

    // ── Registration / Authentication ─────────────────────────────────────────

    /** Alias used by RegisterCredentialUseCase. */
    class RegistrationFailed(
        message: String,
        cause: Throwable? = null,
    ) : Fido2Exception(message, cause, "REGISTRATION_FAILED")

    /** Alias used by GetAssertionUseCase. */
    class AuthenticationFailed(
        message: String,
        cause: Throwable? = null,
    ) : Fido2Exception(message, cause, "AUTHENTICATION_FAILED")

    class AuthenticationException(
        message: String,
        cause: Throwable? = null,
    ) : Fido2Exception(message, cause, "AUTH_ERROR")

    // ── Transport / Bluetooth ─────────────────────────────────────────────────

    class TransportException(
        message: String,
        cause: Throwable? = null,
    ) : Fido2Exception(message, cause, "TRANSPORT_ERROR")

    open class BluetoothException(
        message: String,
        cause: Throwable? = null,
    ) : Fido2Exception(message, cause, "BLUETOOTH_ERROR")

    class BluetoothPermissionDenied(
        message: String,
        cause: Throwable? = null,
    ) : BluetoothException(message, cause)

    class ConnectionException(
        message: String,
        cause: Throwable? = null,
    ) : Fido2Exception(message, cause, "CONNECTION_ERROR")

    // ── Security ──────────────────────────────────────────────────────────────

    class SecurityException(
        message: String,
        cause: Throwable? = null,
    ) : Fido2Exception(message, cause, "SECURITY_ERROR")

    class TamperException(
        message: String,
        cause: Throwable? = null,
    ) : Fido2Exception(message, cause, "TAMPER_ERROR")

    // ── Repository Stats ──────────────────────────────────────────────────────

    /** Used by CredentialRepositoryImpl. */
    class RepositoryStatistics(
        message: String,
        cause: Throwable? = null,
    ) : Fido2Exception(message, cause, "REPO_STATS_ERROR")

    // ── Protocol ──────────────────────────────────────────────────────────────

    /** Used by HidReportParser for framing/protocol-level errors. */
    class ProtocolException(
        message: String,
        cause: Throwable? = null,
    ) : Fido2Exception(message, cause, "PROTOCOL_ERROR")
}
