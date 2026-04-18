package com.chimali.feature.vault.internal

import org.koin.core.annotation.Single


@Single
class ClipboardManagerWrapper() {
    
    // Future: Use Android ClipboardManager to actually clear
    // This wrapper allows tests to verify behavior without Android framework dependencies
    fun clear() {
        // Implementation for clearing clipboard
    }
}
