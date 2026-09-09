package com.chimali.feature.vault.internal.crypto

import com.chimali.core.security.api.EncryptionManager
import com.chimali.core.security.api.EventStoreKeyProvider
import com.chimali.feature.vault.internal.payload.SecureNotePayload
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Test

/** FR-VAULT-026 / SC-VAULT-011: cancellation before dispatcher entry still consumes input. */
class VaultCancellationCleanupTest {
    @Test
    fun cancelledCallerStillErasesSubmission() =
        runBlocking {
            val content = "nonempty-secret".toCharArray()
            val payload = SecureNotePayload("title", content)
            val service = VaultCryptoServiceImpl(mockk<EncryptionManager>(), mockk<EventStoreKeyProvider>())
            launch(start = CoroutineStart.UNDISPATCHED) {
                currentCoroutineContext().cancel()
                service.encryptSecureNote(null, payload, UUID(0, 1))
            }.join()
            assertArrayEquals(CharArray(content.size), content)
        }
}
