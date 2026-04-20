package com.chimali.core.clipboard

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.Foundation.NSString
import platform.UIKit.UIPasteboard

import org.koin.core.annotation.Single

/**
 * iOS implementation of [ClipboardManagerService] using UIPasteboard.
 * Provides secure clipboard management with automatic clearing.
 */
@Single(binds = [ClipboardManagerService::class])
class IosClipboardManagerService(
    private val logger: Logger
) : ClipboardManagerService {

    private val pasteboard = UIPasteboard.generalPasteboard
    private val scope = CoroutineScope(Dispatchers.Main)
    private var clearJob: Job? = null

    override fun copySensitiveData(label: String, text: String, clearDelayMs: Long) {
        logger.i { "iOS Clipboard: Copying sensitive data with label '$label'" }
        
        // Set the text to the general pasteboard
        pasteboard.string = text
        
        // Reset the timer for automatic clearing
        clearJob?.cancel()
        clearJob = scope.launch {
            delay(clearDelayMs)
            clearClipboard()
        }
    }

    override fun clearClipboard() {
        logger.i { "iOS Clipboard: Clearing clipboard" }
        
        clearJob?.cancel()
        clearJob = null
        
        // Clear the pasteboard by setting it to null
        pasteboard.string = null
    }
}
