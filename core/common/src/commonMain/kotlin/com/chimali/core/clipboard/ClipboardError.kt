package com.chimali.core.clipboard

/**
 * Sealed class representing all possible clipboard-related errors.
 * Provides type-safe error handling for clipboard operations.
 */
sealed class ClipboardError(
    message: String? = null,
) : Exception(message) {
    /**
     * Clipboard service is unavailable on this platform/device.
     */
    class ClipboardUnavailable : ClipboardError("Clipboard service unavailable")

    /** Admission was refused before clipboard publication because receipt capacity is occupied. */
    class CopyAdmissionRejected : ClipboardError("Copy is temporarily unavailable. Try again later.")

    /** Failed to copy data to clipboard; platform exception text is not retained. */
    @Suppress("UNUSED_PARAMETER")
    class CopyFailed(
        reason: String,
        cause: Throwable? = null,
    ) : ClipboardError("Copy failed")

    /** Failed to clear clipboard; platform exception text is not retained. */
    @Suppress("UNUSED_PARAMETER")
    class ClearFailed(
        reason: String,
        cause: Throwable? = null,
    ) : ClipboardError("Clear failed")

    /** Platform-specific clipboard error; platform-provided fields and causes are discarded. */
    @Suppress("UNUSED_PARAMETER")
    class PlatformError(
        platform: String,
        errorCode: String?,
        errorMessage: String,
        cause: Throwable? = null,
    ) : ClipboardError("Clipboard operation failed")
}
