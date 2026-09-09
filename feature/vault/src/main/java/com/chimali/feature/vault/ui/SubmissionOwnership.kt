package com.chimali.feature.vault.ui

import com.chimali.feature.vault.internal.payload.SensitivePayload

/** FR-VAULT-026: the callback consumes ownership only when dispatch returns successfully. */
internal inline fun <T : SensitivePayload> submitOwned(
    payload: T,
    accept: (T) -> Unit,
) {
    var transferred = false
    try {
        accept(payload)
        transferred = true
    } finally {
        if (!transferred) payload.clearMemory()
    }
}
