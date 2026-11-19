package com.chimali.feature.vault.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CustomFieldPrivacyTest {
    @Test
    fun genericRepresentationDoesNotExposeCustomFieldName() {
        val marker = "SYNTHETIC_CUSTOM_FIELD_NAME_MARKER"
        val field = CustomField(name = marker, value = "synthetic-value".toCharArray(), isConcealed = true)

        assertFalse(marker in field.toString())
        assertEquals(marker, field.name)
        assertEquals("synthetic-value", field.value.concatToString())
    }
}
