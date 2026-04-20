package com.chimali.core.clipboard

import co.touchlab.kermit.Logger

/**
 * Placeholder implementation of [ClipboardManagerService] for iOS.
 * Actual implementation would use UIPasteboard.
 */
import org.koin.core.annotation.Single

@Single(binds = [ClipboardManagerService::class])
class IosClipboardManagerService(
    private val logger: Logger
) : ClipboardManagerService {

    override fun copySensitiveData(label: String, text: String, clearDelayMs: Long) {
        logger.i { "iOS Clipboard: Copying '$text' with label '$label' (Placeholder)" }
        // TODO: Implement UIPasteboard interaction
    }

    override fun clearClipboard() {
        logger.i { "iOS Clipboard: Clearing (Placeholder)" }
        // TODO: Implement UIPasteboard clearing
    }
}
