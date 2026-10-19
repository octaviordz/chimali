package com.chimali

import android.content.ComponentCallbacks2
import com.chimali.fido2.data.crypto.WalletSessionMemoryPressure
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class WalletSessionMemoryPressureTest {
    /** @see WalletSessionMemoryPressure.handle NFR-SEC-041 / TR-AUTH-031. */
    @Test
    fun `each required severe trim fences session and requests gc after clearing`() {
        val observed = mutableListOf<String>()

        listOf(
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE,
        ).forEach { level ->
            WalletSessionMemoryPressure.handle(
                level = level,
                clearSession = { observed += "clear:$level" },
                requestGc = { observed += "gc:$level" },
            )
        }

        assertEquals(
            listOf(
                "clear:${ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL}",
                "gc:${ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL}",
                "clear:${ComponentCallbacks2.TRIM_MEMORY_BACKGROUND}",
                "gc:${ComponentCallbacks2.TRIM_MEMORY_BACKGROUND}",
                "clear:${ComponentCallbacks2.TRIM_MEMORY_MODERATE}",
                "gc:${ComponentCallbacks2.TRIM_MEMORY_MODERATE}",
            ),
            observed,
        )
    }

    /** @see WalletSessionMemoryPressure.handle NFR-SEC-041 / TR-AUTH-031. */
    @Test
    fun `non-required trim levels do not clear the wallet session or request gc`() {
        val observed = mutableListOf<String>()

        listOf(
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN,
        ).forEach { level ->
            WalletSessionMemoryPressure.handle(
                level = level,
                clearSession = { observed += "clear" },
                requestGc = { observed += "gc" },
            )
        }

        assertTrue(observed.isEmpty())
    }

    /** @see WalletSessionMemoryPressure.handle NFR-SEC-041 / TR-AUTH-031. */
    @Test
    fun `gc hint still runs when session clearing throws`() {
        var gcRequested = false

        assertThrows<IllegalStateException> {
            WalletSessionMemoryPressure.handle(
                level = ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
                clearSession = { error("synthetic provider failure") },
                requestGc = { gcRequested = true },
            )
        }

        assertEquals(true, gcRequested)
    }
}
