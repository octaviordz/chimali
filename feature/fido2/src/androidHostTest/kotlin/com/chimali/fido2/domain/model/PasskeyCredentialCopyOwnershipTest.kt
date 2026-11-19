package com.chimali.fido2.domain.model

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PasskeyCredentialCopyOwnershipTest {
    @Test
    fun `compatibility constructor partial text copies are erased`() {
        for (failureAt in 1..4) {
            val stagedOwners = mutableListOf<SensitiveCredentialText>()
            var copyAttempt = 0

            assertFailsWith<IllegalStateException> {
                withOwnedCredentialTextInputs(
                    "https://example.com",
                    "synthetic-user",
                    "synthetic-account",
                    "Synthetic User",
                    copyText = { source ->
                        check(++copyAttempt != failureAt) { "synthetic constructor text-copy failure" }
                        SensitiveCredentialText.copyOf(source).also(stagedOwners::add)
                    },
                ) { _, _, _, _ -> error("Construction must not start after copy failure") }
            }

            assertTrue(stagedOwners.all(::isErased))
        }

        val stagedOwners = mutableListOf<SensitiveCredentialText>()
        assertFailsWith<IllegalStateException> {
            withOwnedCredentialTextInputs(
                "https://example.com",
                "synthetic-user",
                "synthetic-account",
                "Synthetic User",
                copyText = { source -> SensitiveCredentialText.copyOf(source).also(stagedOwners::add) },
            ) { _, _, _, _ -> error("synthetic compatibility-holder construction failure") }
        }
        assertTrue(stagedOwners.all(::isErased))
    }

    @Test
    fun `failed copy at each owner position erases staged credential copies`() {
        for (failureAt in 1..6) {
            val rpId = SensitiveCredentialText.copyOf("https://example.com")
            val userId = SensitiveCredentialText.copyOf("synthetic-user")
            val userName = SensitiveCredentialText.copyOf("synthetic-account")
            val displayName = SensitiveCredentialText.copyOf("Synthetic User")
            val aaguid = ByteArray(16) { it.toByte() }
            val credentialId = byteArrayOf(1, 2, 3, 4)
            val stagedOwners = mutableListOf<SensitiveCredentialText>()
            val stagedBytes = mutableListOf<ByteArray>()
            var copyAttempt = 0

            try {
                assertFailsWith<IllegalStateException> {
                    withOwnedPasskeyCredentialCopies(
                        rpId,
                        userId,
                        userName,
                        displayName,
                        aaguid,
                        credentialId,
                        copyText = { source ->
                            check(++copyAttempt != failureAt) { "synthetic credential text copy failure" }
                            source.copyOwner().also(stagedOwners::add)
                        },
                        copyBytes = { source ->
                            check(++copyAttempt != failureAt) { "synthetic credential byte copy failure" }
                            source.copyOf().also(stagedBytes::add)
                        },
                    ) { _, _, _, _, _, _ -> error("Construction must not start after copy failure") }
                }

                assertTrue(stagedOwners.all(::isErased))
                stagedBytes.forEach { assertContentEquals(ByteArray(it.size), it) }
                assertTrue(rpId.contentEquals("https://example.com"))
                assertTrue(userId.contentEquals("synthetic-user"))
                assertTrue(userName.contentEquals("synthetic-account"))
                assertTrue(displayName.contentEquals("Synthetic User"))
                assertContentEquals(ByteArray(16) { it.toByte() }, aaguid)
                assertContentEquals(byteArrayOf(1, 2, 3, 4), credentialId)
            } finally {
                rpId.erase()
                userId.erase()
                userName.erase()
                displayName.erase()
                aaguid.fill(0)
                credentialId.fill(0)
            }
        }
    }

    @Test
    fun `credential construction failure erases all staged copies`() {
        val rpId = SensitiveCredentialText.copyOf("https://example.com")
        val userId = SensitiveCredentialText.copyOf("synthetic-user")
        val userName = SensitiveCredentialText.copyOf("synthetic-account")
        val displayName = SensitiveCredentialText.copyOf("Synthetic User")
        val aaguid = ByteArray(16) { it.toByte() }
        val credentialId = byteArrayOf(1, 2, 3, 4)
        val stagedOwners = mutableListOf<SensitiveCredentialText>()
        val stagedBytes = mutableListOf<ByteArray>()

        try {
            assertFailsWith<IllegalStateException> {
                withOwnedPasskeyCredentialCopies(
                    rpId,
                    userId,
                    userName,
                    displayName,
                    aaguid,
                    credentialId,
                    copyText = { source -> source.copyOwner().also(stagedOwners::add) },
                    copyBytes = { source -> source.copyOf().also(stagedBytes::add) },
                ) { _, _, _, _, _, _ -> error("synthetic credential construction failure") }
            }

            assertTrue(stagedOwners.all(::isErased))
            stagedBytes.forEach { assertContentEquals(ByteArray(it.size), it) }
        } finally {
            rpId.erase()
            userId.erase()
            userName.erase()
            displayName.erase()
            aaguid.fill(0)
            credentialId.fill(0)
        }
    }

    private fun isErased(owner: SensitiveCredentialText): Boolean = (0 until owner.length).all { owner[it] == '\u0000' }
}
