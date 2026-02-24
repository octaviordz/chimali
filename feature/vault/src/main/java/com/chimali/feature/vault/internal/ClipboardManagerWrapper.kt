package com.chimali.feature.vault.internal

import javax.inject.Inject

class ClipboardManagerWrapper @Inject constructor() {
    
    // Future: Use Android ClipboardManager to actually clear
    // This wrapper allows tests to verify behavior without Android framework dependencies
    fun clear() {
        // Implementation for clearing clipboard
    }
}
