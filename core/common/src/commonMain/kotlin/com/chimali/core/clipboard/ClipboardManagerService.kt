package com.chimali.core.clipboard

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private val EMPTY_CLEANUP_STATUSES: StateFlow<Map<Long, ClipboardCleanupStatus>> = MutableStateFlow(emptyMap())

/** Non-secret receipt for one attempted sensitive clipboard publication. */
data class ClipboardCopyReceipt(
    val generation: Long,
    val scheduledElapsedMs: Long,
    val publication: PublicationStatus,
)

enum class PublicationStatus { MetadataConfirmed, Unconfirmed }

/** Sanitized lifecycle state. It must never contain copied text, labels or raw exceptions. */
data class ClipboardCleanupStatus(
    val generation: Long,
    val outcome: CleanupOutcome,
    val origin: CleanupOrigin,
)

enum class CleanupOutcome {
    Pending,
    ClearRequestSubmitted,
    SkippedReplaced,
    SkippedOwnershipUnavailable,
    Interrupted,
    Superseded,
    ManualClearSubmitted,
    Abandoned,
}

enum class CleanupOrigin { Automatic, Manual }

/** Fixed product policy. Tests control virtual time; callers cannot override it. */
const val CLIPBOARD_CLEANUP_DELAY_MS: Long = 30_000L

interface ClipboardManagerService {
    val cleanupStatuses: StateFlow<Map<Long, ClipboardCleanupStatus>> get() = EMPTY_CLEANUP_STATUSES

    /**
     * Borrows the mutable operation buffer until this call returns. [label] is a caller hint only;
     * platform adapters must use a fixed non-identifying clipboard label and never publish this value.
     */
    suspend fun copySensitiveData(
        label: String,
        text: CharArray,
    ): Result<ClipboardCopyReceipt>

    suspend fun clearClipboard(): Result<Unit>

    /** Release a terminal receipt after its caller handled the result. */
    fun acknowledge(generation: Long) = Unit

    /** Release a terminal receipt after recording a sanitized abandonment. */
    fun abandon(generation: Long) = Unit

    /** Transfer a pending receipt to the application-scoped custodian when its caller is disposed. */
    fun transferToCustodian(generation: Long) = abandon(generation)
}
