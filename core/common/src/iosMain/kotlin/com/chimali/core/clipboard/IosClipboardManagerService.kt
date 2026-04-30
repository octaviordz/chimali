package com.chimali.core.clipboard

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single
import platform.UIKit.UIPasteboard

/**
 * iOS implementation of [ClipboardManagerService] using UIPasteboard.
 * Provides secure clipboard management with automatic clearing.
 */
@Single(binds = [ClipboardManagerService::class])
class IosClipboardManagerService(
    private val logger: Logger,
) : ClipboardManagerService {
    private val pasteboard = UIPasteboard.generalPasteboard
    private val scope = CoroutineScope(Dispatchers.Main)
    private var clearJob: Job? = null

    override suspend fun copySensitiveData(
        label: String,
        text: String,
        clearDelayMs: Long,
    ): Result<Unit> {
        return try {
            logger.i {
                "iOS Clipboard: Copying sensitive data" +
                    "| label: $label" +
                    "| length: ${text.length}" +
                    "| clearDelay: ${clearDelayMs}ms"
            }

            // Set the text to the general pasteboard
            pasteboard.string = text

            // Reset the timer for automatic clearing
            clearJob?.cancel()
            clearJob =
                scope.launch {
                    delay(clearDelayMs)
                    clearClipboard()
                }

            // Note: Content change event publishing can be added when event system is integrated

            Result.success(Unit)
        } catch (e: IllegalStateException) {
            logger.e(e) { "iOS Clipboard: Copy failed - ${e.message}" }
            Result.failure(ClipboardError.PlatformError("iOS", e::class.simpleName, e.message ?: "Unknown error", e))
        }
    }

    override suspend fun clearClipboard(): Result<Unit> {
        return try {
            logger.i { "iOS Clipboard: Clearing clipboard" }

            clearJob?.cancel()
            clearJob = null

            // Clear the pasteboard by setting it to null
            pasteboard.string = null

            // Note: Auto-clear event publishing can be added when event system is integrated

            Result.success(Unit)
        } catch (e: IllegalStateException) {
            logger.e(e) { "iOS Clipboard: Clear failed - ${e.message}" }
            Result.failure(ClipboardError.PlatformError("iOS", e::class.simpleName, e.message ?: "Unknown error", e))
        }
    }
}
