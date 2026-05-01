package com.chimali.core.clipboard

/**
 * Sealed class representing all possible clipboard-related errors.
 * Provides type-safe error handling for clipboard operations.
 */
sealed class ClipboardError(
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause) {
    /**
     * Clipboard service is unavailable on this platform/device.
     */
    data object ClipboardUnavailable : ClipboardError("Clipboard service unavailable")

    /**
     * Failed to copy data to clipboard.
     */
    data class CopyFailed(
        val reason: String,
        override val cause: Throwable? = null,
    ) : ClipboardError("Copy failed: $reason", cause)

    /**
     * Failed to clear clipboard.
     */
    data class ClearFailed(
        val reason: String,
        override val cause: Throwable? = null,
    ) : ClipboardError("Clear failed: $reason", cause)

    /**
     * Timer operation failed (creation, cancellation, etc.).
     */
    data class TimerFailed(
        val operation: String,
        val reason: String,
        override val cause: Throwable? = null,
    ) : ClipboardError("Timer operation $operation failed: $reason", cause)

    /**
     * Platform-specific clipboard error.
     */
    data class PlatformError(
        val platform: String,
        val errorCode: String?,
        val errorMessage: String,
        override val cause: Throwable? = null,
    ) : ClipboardError("[$platform Error ${errorCode ?: ""}] $errorMessage", cause)
}
