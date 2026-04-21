package com.chimali.core.events

/**
 * Sealed class representing clipboard-related events for security monitoring.
 */
sealed class ClipboardEvent {
    
    /**
     * Emitted when clipboard content changes.
     * Used for security monitoring and audit logging.
     */
    data class ContentChanged(
        val timestamp: Long,
        val hasContent: Boolean,
        val isSensitive: Boolean
    ) : ClipboardEvent()
    
    /**
     * Emitted when sensitive data is automatically cleared.
     */
    data class AutoCleared(
        val timestamp: Long,
        val reason: String
    ) : ClipboardEvent()
    
    /**
     * Emitted when clipboard access is denied for security reasons.
     */
    data class AccessDenied(
        val timestamp: Long,
        val reason: String
    ) : ClipboardEvent()
}
