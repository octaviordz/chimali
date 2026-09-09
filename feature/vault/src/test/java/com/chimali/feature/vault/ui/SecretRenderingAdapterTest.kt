package com.chimali.feature.vault.ui

import com.chimali.feature.vault.ui.components.secretDisplayText
import com.chimali.feature.vault.ui.model.LegibilitySettings
import org.junit.Assert.assertEquals
import org.junit.Test

/** T059 / FR-VAULT-025/026: rendering borrows arrays and only discloses revealed contents. */
class SecretRenderingAdapterTest {
    @Test
    fun maskedSecretProducesOnlyBullets() {
        val secret = "synthetic".toCharArray()
        try {
            assertEquals("•".repeat(secret.size), secretDisplayText(secret, false, LegibilitySettings()).text)
        } finally {
            secret.fill('\u0000')
        }
    }

    @Test
    fun revealedGroupingAndHighlightingPreserveCharacters() {
        val secret = "Ab12!xyz".toCharArray()
        try {
            for (highlight in listOf(false, true)) {
                val settings = LegibilitySettings(useSemanticHighlighting = highlight)
                assertEquals("Ab12 !xyz", secretDisplayText(secret, true, settings, 4).text)
                assertEquals("Ab12!xyz", secretDisplayText(secret, true, settings).text)
            }
            assertEquals('A', secret[0])
        } finally {
            secret.fill('\u0000')
        }
    }
}
