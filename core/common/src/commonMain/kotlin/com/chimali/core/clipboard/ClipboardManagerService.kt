package com.chimali.core.clipboard

/**
 * Service for securely managing sensitive data in the system clipboard.
 * Ensure that data is cleared within 60 seconds as mandated by the project constitution.
 */
interface ClipboardManagerService {
    
    /**
     * Copies the provided sensitive text to the clipboard and schedules an automatic
     * clear operation after the [clearDelayMs] elapses. If copied again before the delay
     * expires, the timer should be reset.
     *
     * @param label A user-visible label for the copied data.
     * @param text The sensitive text to copy.
     * @param clearDelayMs The delay in milliseconds before clearing the clipboard (default 60s).
     */
    fun copySensitiveData(label: String, text: String, clearDelayMs: Long = 60_000L)
    
    /**
     * Explicitly clears the clipboard immediately and cancels any pending scheduled clears.
     */
    fun clearClipboard()
}
