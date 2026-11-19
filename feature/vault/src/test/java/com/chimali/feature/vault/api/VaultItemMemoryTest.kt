package com.chimali.feature.vault.api

import java.util.UUID
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class VaultItemMemoryTest {
    @Test
    fun `clearMemory erases title payload and CRDT state buffers`() {
        val title = "account label".toCharArray()
        val payload = byteArrayOf(0x11, 0x22, 0x33)
        val crdtState = byteArrayOf(0x44, 0x55)
        val item =
            VaultItem(
                id = UUID.randomUUID(),
                type = VaultType.PASSWORD,
                title = title,
                payload = payload,
                crdtState = crdtState,
                dateCreated = "",
                dateModified = "",
                lastBackedUpAt = null,
                identityId = UUID.randomUUID(),
            )

        item.clearMemory()

        assertArrayEquals(CharArray(title.size), title)
        assertArrayEquals(ByteArray(payload.size), payload)
        assertArrayEquals(ByteArray(crdtState.size), crdtState)
    }
}
