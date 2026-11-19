package com.chimali.core.domain.valueobject

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RpIdPrivacyTest {
    @Test
    fun genericRepresentationRedactsTheRelyingPartyIdentifierWithoutChangingSerialization() {
        val marker = "synthetic-rp-identifier.example"
        val rpId = RpId(marker)

        assertFalse(marker in rpId.toString())
        assertEquals(marker, rpId.value)
        assertEquals("\"$marker\"", Json.encodeToString(RpId.serializer(), rpId))
    }
}
