package com.chimali.fido2.data.eventsourcing

import com.chimali.core.domain.eventsourcing.passkey.PasskeyState
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class PasskeyStateCopyOwnershipTest {
    @ParameterizedTest
    @ValueSource(ints = [1, 2, 3, 4, 5])
    fun `failed metadata copy erases every staged array`(failureAt: Int) {
        val source =
            PasskeyState().copy(
                rpId = "rp.example".toCharArray(),
                userId = "synthetic-user".toCharArray(),
                userName = "account".toCharArray(),
                userDisplayName = "Synthetic User".toCharArray(),
                credentialId = byteArrayOf(1, 2, 3),
            )
        val originalRpId = source.rpId.copyOf()
        val originalUserId = source.userId.copyOf()
        val originalUserName = source.userName.copyOf()
        val originalDisplayName = source.userDisplayName.copyOf()
        val originalCredentialId = source.credentialId.copyOf()
        val stagedCharacters = mutableListOf<CharArray>()
        val stagedBytes = mutableListOf<ByteArray>()
        var copyAttempt = 0

        try {
            assertFailsWith<IllegalStateException> {
                source.copyWithOwnedMetadata(
                    copyCharacters = { value ->
                        check(++copyAttempt != failureAt) { "synthetic metadata copy failure" }
                        value.copyOf().also(stagedCharacters::add)
                    },
                    copyBytes = { value ->
                        check(++copyAttempt != failureAt) { "synthetic metadata copy failure" }
                        value.copyOf().also(stagedBytes::add)
                    },
                )
            }

            assertEquals(failureAt, copyAttempt)
            stagedCharacters.forEach { assertContentEquals(CharArray(it.size), it) }
            stagedBytes.forEach { assertContentEquals(ByteArray(it.size), it) }
            assertContentEquals(originalRpId, source.rpId)
            assertContentEquals(originalUserId, source.userId)
            assertContentEquals(originalUserName, source.userName)
            assertContentEquals(originalDisplayName, source.userDisplayName)
            assertContentEquals(originalCredentialId, source.credentialId)
        } finally {
            source.eraseSensitiveMetadata()
            originalRpId.fill('\u0000')
            originalUserId.fill('\u0000')
            originalUserName.fill('\u0000')
            originalDisplayName.fill('\u0000')
            originalCredentialId.fill(0)
        }
    }
}
