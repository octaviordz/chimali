package com.chimali.core.ui.theme

import androidx.compose.ui.text.font.FontFamily
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LegibilityTypeTest {
    @Test
    fun `security text font families are configured and fallback is monospace`() {
        assertNotSame(FontFamily.Default, LegibilityType.AtkinsonFontFamily)
        assertNotSame(FontFamily.Default, LegibilityType.JetBrainsMonoFontFamily)
        assertSame(FontFamily.Monospace, LegibilityType.SystemMonospaceFontFamily)
        assertTrue(LegibilityType.AtkinsonFontFamily !== LegibilityType.JetBrainsMonoFontFamily)
    }
}
