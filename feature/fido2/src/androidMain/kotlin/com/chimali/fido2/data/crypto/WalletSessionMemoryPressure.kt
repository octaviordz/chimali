package com.chimali.fido2.data.crypto

import android.content.ComponentCallbacks2
import com.chimali.core.common.annotation.Req

/** Applies the wallet-session fence policy to severe Android memory callbacks. */
object WalletSessionMemoryPressure {
    /**
     * Clears the wallet session for the three severe [level] values handled here.
     * [clearSession] runs before [requestGc]; the GC hint still runs if clearing fails.
     * Other trim levels leave both callbacks untouched, and callback failures propagate.
     * @param level Android memory-trim level supplied by the application callback.
     * @param clearSession Clears and fences the owned wallet session.
     * @param requestGc Requests a GC hint after severe-level clearing.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun handle(
        level: Int,
        clearSession: () -> Unit,
        requestGc: () -> Unit,
    ) {
        if (level != ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL &&
            level != ComponentCallbacks2.TRIM_MEMORY_BACKGROUND &&
            level != ComponentCallbacks2.TRIM_MEMORY_MODERATE
        ) {
            return
        }

        try {
            clearSession()
        } finally {
            requestGc()
        }
    }
}
