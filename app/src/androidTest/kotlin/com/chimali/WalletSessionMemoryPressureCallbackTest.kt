package com.chimali

import android.content.ComponentCallbacks2
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chimali.core.security.api.MasterSeedProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.android.getKoin

/** Exercises the production Application callback that fences the wallet session on memory trim. */
@Suppress("DEPRECATION") // Android retains these callback levels as deprecated compatibility constants.
@RunWith(AndroidJUnit4::class)
class WalletSessionMemoryPressureCallbackTest {
    @Test
    fun severeAndroidTrimCallbacksFenceTheProductionWalletGeneration() {
        val application = ApplicationProvider.getApplicationContext<ChimaliApplication>()
        val provider = application.getKoin().get<MasterSeedProvider>()
        val severeLevels =
            intArrayOf(
                ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
                ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
                ComponentCallbacks2.TRIM_MEMORY_MODERATE,
            )

        try {
            severeLevels.forEach { level ->
                val generationBeforeTrim = provider.sessionGeneration()

                application.onTrimMemory(level)

                assertEquals(
                    "Application trim callback at level $level must fence the production wallet session",
                    generationBeforeTrim + 1,
                    provider.sessionGeneration(),
                )
            }
        } finally {
            provider.clearSession()
        }
    }
}
