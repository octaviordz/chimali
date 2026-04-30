package com.chimali.core.common.result

/**
 * Sealed hierarchy of domain-level errors used throughout the application.
 *
 * Every [DomainError] preserves the original [cause] throwable so that Crashlytics
 * always receives the full stack trace, while domain and presentation layers only
 * ever deal with strongly-typed categorized errors — never raw [Throwable]s.
 *
 * ## Design Contract
 *
 * - Errors are **logged at the boundary** (Repository / Service layer) where they are
 *   first caught. Presentation layer code must NOT log again.
 * - The [cause] field is optional to allow purely synthetic errors (e.g. validation
 *   failures that have no underlying exception).
 * - New sub-types should be added here rather than using [UnknownError], which is
 *   reserved exclusively for APIs that throw undocumented runtime exceptions.
 */
sealed interface DomainError {
    /** The original exception, preserved for Crashlytics / Kermit logging at the boundary. */
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
     * When using [UnknownError], always include the original [cause] for diagnostics.
     */
    data class UnknownError(
        override val message: String,
        override val cause: Throwable? = null,
    ) : DomainError
}
