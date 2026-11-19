package com.chimali.core.common.result

/**
 * Sealed hierarchy of domain-level errors used throughout the application.
 *
 * [DomainError] values preserve typed failure categories without requiring the raw
 * platform exception to cross the repository boundary. The optional [cause] may be
 * retained only when its message, stack and downstream handling are appropriate for
 * every diagnostic destination that can receive it.
 *
 * ## Design Contract
 *
 * - Failures are handled at the boundary (Repository / Service layer) where first
 *   caught. Log only fixed or reviewed-safe diagnostics; presentation code must NOT log
 *   raw errors again.
 * - The [cause] field is optional. Do not attach a sensitive platform exception merely
 *   to preserve its details for logging.
 * - New sub-types should be added here rather than using [UnknownError], which is
 *   reserved exclusively for APIs that throw undocumented runtime exceptions.
 */
sealed interface DomainError {
    /** Optional original exception; retain only when all downstream diagnostic paths are safe. */
    val cause: Throwable?

    /** A human-readable, developer-facing message describing the error. */
    val message: String

    // ── Network ───────────────────────────────────────────────────────────────

    /**
     * A network I/O failure (e.g. [java.io.IOException], [java.net.SocketTimeoutException]).
     */
    data class NetworkError(
        override val message: String,
        override val cause: Throwable? = null,
    ) : DomainError

    // ── Database / Storage ────────────────────────────────────────────────────

    /**
     * A database operation failure (e.g. [android.database.sqlite.SQLiteException]).
     */
    data class DatabaseError(
        override val message: String,
        override val cause: Throwable? = null,
    ) : DomainError

    /**
     * A general storage/persistence failure (file I/O, key-value store, etc.).
     */
    data class StorageError(
        override val message: String,
        override val cause: Throwable? = null,
    ) : DomainError

    // ── Security / Crypto ─────────────────────────────────────────────────────

    /**
     * A [java.lang.SecurityException] or permission denial at the OS level.
     */
    data class SecurityError(
        override val message: String,
        override val cause: Throwable? = null,
    ) : DomainError

    /**
     * A cryptographic operation failure (key generation, signing, encryption, decryption).
     */
    data class CryptoError(
        override val message: String,
        override val cause: Throwable? = null,
    ) : DomainError

    // ── Validation ────────────────────────────────────────────────────────────

    /**
     * An invalid or malformed input that cannot be processed.
     */
    data class ValidationError(
        override val message: String,
        override val cause: Throwable? = null,
    ) : DomainError

    // ── Business Logic ────────────────────────────────────────────────────────

    /**
     * An operation was denied by business rules (e.g. consent denied, limit exceeded).
     */
    data class OperationDenied(
        override val message: String,
        override val cause: Throwable? = null,
    ) : DomainError

    /**
     * An operation was canceled by the user.
     */
    data class OperationCanceled(
        override val message: String = "Operation canceled",
        override val cause: Throwable? = null,
    ) : DomainError

    /**
     * A required entity was not found.
     */
    data class NotFound(
        override val message: String,
        override val cause: Throwable? = null,
    ) : DomainError

    // ── Fallback ──────────────────────────────────────────────────────────────

    /**
     * A catch-all for APIs that throw undocumented runtime exceptions.
     *
     * **Use sparingly.** Prefer a specific sub-type when the exception source is known.
     * Retain the original [cause] only when its message, stack, and downstream handling are
     * safe for every diagnostic destination that can receive it. Otherwise omit the cause;
     * do not preserve it solely for logging.
     */
    data class UnknownError(
        override val message: String,
        override val cause: Throwable? = null,
    ) : DomainError
}
