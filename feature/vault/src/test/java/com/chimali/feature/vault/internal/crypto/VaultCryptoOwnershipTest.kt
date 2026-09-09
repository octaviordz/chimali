package com.chimali.feature.vault.internal.crypto

import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.security.api.EncryptionManager
import com.chimali.core.security.api.EventStoreKeyProvider
import com.chimali.feature.vault.api.VaultItem
import com.chimali.feature.vault.api.VaultType
import com.chimali.feature.vault.internal.payload.CreditCardPayload
import com.chimali.feature.vault.internal.payload.PasswordPayload
import com.chimali.feature.vault.internal.payload.SecureNotePayload
import com.chimali.feature.vault.internal.payload.SensitivePayload
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.security.GeneralSecurityException
import java.util.UUID
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** T057 / FR-VAULT-026: actual plaintext/key references and undelivered decrypted ownership. */
@OptIn(ExperimentalCoroutinesApi::class)
class VaultCryptoOwnershipTest {
    private val types = listOf(VaultType.PASSWORD, VaultType.CREDIT_CARD, VaultType.NOTE)
    private val identity = UUID(0, 1)

    @Test
    fun decryptFailuresReturnSafeErrorsAndEraseTheAcquiredKey() =
        runTest {
            for (type in types) {
                for (failure in listOf(GeneralSecurityException("synthetic"), IllegalArgumentException("synthetic"))) {
                    val key = ByteArray(32) { 1 }
                    val provider = mockk<EventStoreKeyProvider>()
                    coEvery { provider.getEventStoreKey(any()) } returns key
                    val encryption = mockk<EncryptionManager>()
                    every { encryption.decrypt(any(), any()) } throws failure
                    val service = VaultCryptoServiceImpl(encryption, provider, coroutineContextDispatcher())
                    val item = VaultItem(identity, type, "title", byteArrayOf(1), byteArrayOf(), "", "", null, identity)
                    val result =
                        when (type) {
                            VaultType.PASSWORD -> service.decryptPassword(item)
                            VaultType.CREDIT_CARD -> service.decryptCreditCard(item)
                            VaultType.NOTE -> service.decryptSecureNote(item)
                        }
                    assertTrue(result is Outcome.Error)
                    erased(key)
                }
            }
        }

    @Test
    fun cancellationAndProviderFailurePreserveOwnershipForEveryType() =
        runTest {
            for (type in types) {
                for (cancel in listOf(false, true)) {
                    val arrays = mutableListOf<Any>()
                    val codec = VaultPayloadCodec { arrays.add(it) }
                    val payload = decode(codec, type, fixture(type))
                    val provider = mockk<EventStoreKeyProvider>()
                    coEvery { provider.getEventStoreKey(any()) } answers {
                        if (cancel) throw kotlinx.coroutines.CancellationException("synthetic")
                        error("synthetic")
                    }
                    val service = VaultCryptoServiceImpl(mockk(), provider, coroutineContextDispatcher(), codec)
                    val item = VaultItem(identity, type, "title", byteArrayOf(1), byteArrayOf(), "", "", null, identity)
                    var cancelled = false
                    try {
                        assertTrue(encrypt(service, payload) is Outcome.Error)
                    } catch (_: kotlinx.coroutines.CancellationException) {
                        cancelled = true
                    }
                    org.junit.Assert.assertEquals(cancel, cancelled)
                    arrays.forEach { erased(it) }
                    cancelled = false
                    try {
                        val result =
                            when (type) {
                                VaultType.PASSWORD -> service.decryptPassword(item)
                                VaultType.CREDIT_CARD -> service.decryptCreditCard(item)
                                VaultType.NOTE -> service.decryptSecureNote(item)
                            }
                        assertTrue(result is Outcome.Error)
                    } catch (_: kotlinx.coroutines.CancellationException) {
                        cancelled = true
                    }
                    org.junit.Assert.assertEquals(cancel, cancelled)
                }
            }
        }

    @Test
    fun rejectedEncryptionErasesAllocatedPlaintextForEveryType() =
        runTest {
            for (type in types) {
                val arrays = mutableListOf<Any>()
                val codec = VaultPayloadCodec { arrays.add(it) }
                val payload = decode(codec, type, fixture(type))
                val key = ByteArray(32) { 1 }
                val provider = mockk<EventStoreKeyProvider>()
                coEvery { provider.getEventStoreKey(any()) } returns key
                val encryption = mockk<EncryptionManager>()
                var plaintext: ByteArray? = null
                every { encryption.encrypt(any(), any()) } answers {
                    plaintext = firstArg()
                    throw IllegalArgumentException("synthetic")
                }
                val service = VaultCryptoServiceImpl(encryption, provider, coroutineContextDispatcher(), codec)
                assertTrue(encrypt(service, payload) is Outcome.Error)
                arrays.forEach { erased(it) }
                erased(checkNotNull(plaintext))
                erased(key)
            }
        }

    private fun fixture(type: VaultType): ByteArray {
        val name =
            when (type) {
                VaultType.PASSWORD -> "password"
                VaultType.CREDIT_CARD -> "card"
                VaultType.NOTE -> "note"
            }
        return checkNotNull(javaClass.getResourceAsStream("/vault-legacy-v1/$name.json")).use { it.readBytes() }
    }

    @Test
    fun successAndEncryptionFailureEraseInputsKeysPlaintextAndScratchForEveryType() =
        runTest {
            for (type in types) {
                for (fail in listOf(false, true)) {
                    val arrays = mutableListOf<Any>()
                    val codec = VaultPayloadCodec { arrays.add(it) }
                    val payload = decode(codec, type, fixture(type))
                    val key = ByteArray(32) { 1 }
                    var plaintext: ByteArray? = null
                    val encryption = mockk<EncryptionManager>()
                    every { encryption.encrypt(any(), any()) } answers {
                        plaintext = firstArg()
                        if (fail) throw GeneralSecurityException("synthetic failure")
                        byteArrayOf(1, 2, 3)
                    }
                    val provider = mockk<EventStoreKeyProvider>()
                    coEvery { provider.getEventStoreKey(any()) } returns key
                    val service = VaultCryptoServiceImpl(encryption, provider, coroutineContextDispatcher(), codec)
                    val result = encrypt(service, payload)
                    assertTrue(if (fail) result is Outcome.Error else result is Outcome.Success)
                    assertTrue(plaintext != null)
                    arrays.forEach { erased(it) }
                    erased(plaintext!!)
                    erased(key)
                }
            }
        }

    @Test
    fun keyFailureAllocatesNoSerializedPlaintextAndErasesEverySubmittedField() =
        runTest {
            for (type in types) {
                val arrays = mutableListOf<Any>()
                val codec = VaultPayloadCodec { arrays.add(it) }
                val payload = decode(codec, type, fixture(type))
                val before = arrays.size
                val provider = mockk<EventStoreKeyProvider>()
                coEvery { provider.getEventStoreKey(any()) } throws IllegalStateException("synthetic missing key")
                val service = VaultCryptoServiceImpl(mockk(), provider, coroutineContextDispatcher(), codec)
                assertTrue(encrypt(service, payload) is Outcome.Error)
                org.junit.Assert.assertEquals(before, arrays.size)
                arrays.forEach { erased(it) }
            }
        }

    @Test
    fun cancellationDuringReturnDeliveryErasesProducedPayloadForEveryType() =
        runTest {
            for (type in types) {
                val arrays = mutableListOf<Any>()
                val codec = VaultPayloadCodec { arrays.add(it) }
                val dispatcher = QueuedDispatcher()
                val plaintext = fixture(type)
                val key = ByteArray(32) { 1 }
                val provider = mockk<EventStoreKeyProvider>()
                coEvery { provider.getEventStoreKey(any()) } returns key
                val encryption = mockk<EncryptionManager>()
                every { encryption.decrypt(any(), any()) } returns plaintext
                val service = VaultCryptoServiceImpl(encryption, provider, dispatcher, codec)
                val item = VaultItem(identity, type, "title", byteArrayOf(1), byteArrayOf(), "", "", null, identity)
                var delivered = false
                val job =
                    launch {
                        when (type) {
                            VaultType.PASSWORD -> service.decryptPassword(item)
                            VaultType.CREDIT_CARD -> service.decryptCreditCard(item)
                            VaultType.NOTE -> service.decryptSecureNote(item)
                        }
                        delivered = true
                    }
                runCurrent()
                dispatcher.runNext()
                assertTrue(arrays.any { it is CharArray && it.any { c -> c != '\u0000' } })
                job.cancel()
                advanceUntilIdle()
                assertFalse(delivered)
                arrays.forEach { erased(it) }
                erased(plaintext)
                erased(key)
            }
        }

    @Test
    fun cancelledQueuedDecryptionDoesNotAcquireKeyOrPublishPayload() =
        runTest {
            for (type in types) {
                val dispatcher = QueuedDispatcher()
                val provider = mockk<EventStoreKeyProvider>()
                val service = VaultCryptoServiceImpl(mockk(), provider, dispatcher)
                val item = VaultItem(identity, type, "title", byteArrayOf(1), byteArrayOf(), "", "", null, identity)
                var delivered = false
                val job =
                    launch {
                        when (type) {
                            VaultType.PASSWORD -> service.decryptPassword(item)
                            VaultType.CREDIT_CARD -> service.decryptCreditCard(item)
                            VaultType.NOTE -> service.decryptSecureNote(item)
                        }
                        delivered = true
                    }
                runCurrent()
                job.cancel()
                dispatcher.runNext()
                advanceUntilIdle()
                assertFalse(delivered)
                io.mockk.coVerify(exactly = 0) { provider.getEventStoreKey(any()) }
            }
        }

    private fun decode(
        codec: VaultPayloadCodec,
        type: VaultType,
        bytes: ByteArray,
    ): SensitivePayload =
        when (type) {
            VaultType.PASSWORD -> codec.decodePassword(bytes)
            VaultType.CREDIT_CARD -> codec.decodeCreditCard(bytes)
            VaultType.NOTE -> codec.decodeSecureNote(bytes)
        }

    private suspend fun encrypt(
        service: VaultCryptoService,
        payload: SensitivePayload,
    ): Outcome<VaultItem, DomainError> =
        when (payload) {
            is PasswordPayload -> service.encryptPassword(null, payload, identity)
            is CreditCardPayload -> service.encryptCreditCard(null, payload, identity)
            is SecureNotePayload -> service.encryptSecureNote(null, payload, identity)
            else -> error("Unexpected test payload")
        }

    private suspend fun coroutineContextDispatcher(): CoroutineDispatcher =
        kotlin.coroutines.coroutineContext[kotlin.coroutines.ContinuationInterceptor] as CoroutineDispatcher

    private fun erased(value: Any) {
        when (value) {
            is ByteArray -> assertTrue(value.all { it == 0.toByte() })
            is CharArray -> assertTrue(value.all { it == '\u0000' })
            else -> error("Unexpected allocation")
        }
    }

    private class QueuedDispatcher : CoroutineDispatcher() {
        private val queue = ArrayDeque<Runnable>()

        override fun dispatch(
            context: CoroutineContext,
            block: Runnable,
        ) {
            queue.addLast(block)
        }

        fun runNext() {
            queue.removeFirst().run()
        }
    }
}
