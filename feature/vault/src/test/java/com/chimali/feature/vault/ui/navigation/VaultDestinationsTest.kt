package com.chimali.feature.vault.ui.navigation

import com.chimali.feature.vault.api.VaultType
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class VaultDestinationsTest {
    @Test
    fun detailRoutesAreStableAndTypeSpecific() {
        val id = UUID.fromString("00000000-0000-0000-0000-000000000001")

        assertEquals("vault/detail/password/$id", VaultDestinations.detailRoute(VaultType.PASSWORD, id))
        assertEquals("vault/detail/card/$id", VaultDestinations.detailRoute(VaultType.CREDIT_CARD, id))
        assertEquals("vault/detail/note/$id", VaultDestinations.detailRoute(VaultType.NOTE, id))
    }
}
