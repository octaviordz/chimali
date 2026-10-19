package com.chimali.fido2.presentation.viewmodel

import androidx.lifecycle.ViewModelStore
import com.chimali.core.clipboard.ClipboardManagerService
import com.chimali.core.security.api.HdkKeyPair
import com.chimali.core.security.api.ImportMnemonicResult
import com.chimali.core.security.api.MasterSeedProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested

/**
 * T146a — Unit tests for [DevToolsViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DevToolsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val mockProvider: MasterSeedProvider = mockk()
    private val mockClipboard: ClipboardManagerService = mockk(relaxed = true)

    private companion object {
        private const val SEED_SIZE_64 = 64
        private const val MNEMONIC_WORD_COUNT = 24
    }

    private val fakeWords = (1..MNEMONIC_WORD_COUNT).map { "word$it" }
    private val fakeMnemonic = fakeWords.joinToString(" ").toCharArray()
    private val viewModelStore = ViewModelStore()

    private lateinit var viewModel: DevToolsViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { mockProvider.getMasterSeed() } returns ByteArray(SEED_SIZE_64)
        coEvery { mockProvider.getDeviceKeyPair() } returns mockk<HdkKeyPair>(relaxed = true)
        coEvery { mockProvider.getMnemonic() } answers { fakeMnemonic.copyOf() }
        coEvery {
            mockProvider.withSessionOwner(any(), any(), any<suspend (Long) -> Unit>())
        } coAnswers {
            thirdArg<suspend (Long) -> Unit>().invoke(1L)
        }
        coEvery { mockProvider.importMnemonic(any()) } returns ImportMnemonicResult.Created
        viewModel = DevToolsViewModel(mockProvider, mockClipboard)
        viewModelStore.put("dev-tools", viewModel)
    }

    @AfterEach
    fun tearDown() {
        viewModelStore.clear()
        Dispatchers.resetMain()
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `LoadMnemonic returns word list when provider has mnemonic`() =
        runTest {
            viewModel.onIntent(DevToolsIntent.LoadMnemonic)

            val state = viewModel.state.value
            assertFalse(state.isLoading)
            assertContentEquals(fakeMnemonic, state.mnemonicWords)
            assertTrue(state.isMnemonicVisible)
            assertNull(state.error)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `mnemonic reveal keeps its provider owner until it is hidden`() =
        runTest {
            var ownerReleased = false
            coEvery {
                mockProvider.withSessionOwner(any(), any(), any<suspend (Long) -> Unit>())
            } coAnswers {
                try {
                    thirdArg<suspend (Long) -> Unit>().invoke(1L)
                } finally {
                    ownerReleased = true
                }
            }

            viewModel.onIntent(DevToolsIntent.LoadMnemonic)

            assertTrue(viewModel.state.value.isMnemonicVisible)
            assertFalse(ownerReleased)

            viewModel.onIntent(DevToolsIntent.ClearMnemonic)

            assertTrue(ownerReleased)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `LoadMnemonic sets error when provider returns null`() =
        runTest {
            coEvery { mockProvider.getMnemonic() } returns null
            viewModel.onIntent(DevToolsIntent.LoadMnemonic)

            val state = viewModel.state.value
            assertFalse(state.isLoading)
            assertNull(state.mnemonicWords)
            assertNotNull(state.error)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `ClearMnemonic zeroes words from state`() =
        runTest {
            viewModel.onIntent(DevToolsIntent.LoadMnemonic)
            assertNotNull(viewModel.state.value.mnemonicWords)

            viewModel.onIntent(DevToolsIntent.ClearMnemonic)

            val state = viewModel.state.value
            assertNull(state.mnemonicWords)
            assertFalse(state.isMnemonicVisible)
            assertNull(state.error)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `copy requires a revealed mnemonic and uses the sensitive clipboard policy`() =
        runTest {
            viewModel.onIntent(DevToolsIntent.CopyToClipboard)
            coVerify(exactly = 0) { mockClipboard.copySensitiveData(any(), any(), any()) }

            viewModel.onIntent(DevToolsIntent.LoadMnemonic)
            val ownedWords = requireNotNull(viewModel.state.value.mnemonicWords)
            viewModel.onIntent(DevToolsIntent.CopyToClipboard)

            coVerify(exactly = 1) {
                mockClipboard.copySensitiveData("Chimali Master Seed", fakeMnemonic.concatToString(), 60_000L)
            }
            assertContentEquals(fakeMnemonic, ownedWords)

            viewModel.onIntent(DevToolsIntent.ClearMnemonic)

            assertTrue(
                ownedWords.all { it == '\u0000' },
                "Hiding the mnemonic must erase the UI-owned character buffer",
            )
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `reveal replacement erases the previous mutable mnemonic owner`() =
        runTest {
            val first = fakeMnemonic.copyOf()
            val second =
                "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon art"
                    .toCharArray()
            coEvery { mockProvider.getMnemonic() } returnsMany listOf(first, second)

            viewModel.onIntent(DevToolsIntent.LoadMnemonic)
            assertContentEquals(first, viewModel.state.value.mnemonicWords)

            viewModel.onIntent(DevToolsIntent.LoadMnemonic)

            assertTrue(first.all { it == '\u0000' }, "Replacing a reveal must erase the previous owner")
            assertContentEquals(second, viewModel.state.value.mnemonicWords)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `disposing view model erases revealed mnemonic owner`() =
        runTest {
            viewModel.onIntent(DevToolsIntent.LoadMnemonic)
            val owned = requireNotNull(viewModel.state.value.mnemonicWords)

            viewModelStore.clear()

            assertTrue(owned.all { it == '\u0000' }, "ViewModel disposal must erase its mnemonic buffer")
            assertNull(viewModel.state.value.mnemonicWords)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `cancelling import erases the in-flight mutable input`() =
        runTest {
            val captured = slot<CharArray>()
            val importEntered = CompletableDeferred<Unit>()
            coEvery { mockProvider.importMnemonic(capture(captured)) } coAnswers {
                importEntered.complete(Unit)
                awaitCancellation()
            }

            viewModel.onIntent(DevToolsIntent.RecoverFromSeed(fakeMnemonic.copyOf()))
            importEntered.await()
            val imported = captured.captured

            viewModelStore.clear()

            assertTrue(imported.all { it == '\u0000' }, "Cancellation must erase the owned import buffer")
            assertFalse(viewModel.state.value.isLoading)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `disposing before scheduled import starts erases the transferred input`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val imported = fakeMnemonic.copyOf()

            viewModel.onIntent(DevToolsIntent.RecoverFromSeed(imported))

            assertTrue(imported.any { it != '\u0000' }, "The queued import still owns its input before cancellation")
            viewModelStore.clear()

            assertTrue(imported.all { it == '\u0000' }, "Disposal must erase input even if the coroutine never starts")
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `hiding during mnemonic load cancels the reveal owner`() =
        runTest {
            val loadEntered = CompletableDeferred<Unit>()
            coEvery { mockProvider.getMnemonic() } coAnswers {
                loadEntered.complete(Unit)
                awaitCancellation()
            }

            viewModel.onIntent(DevToolsIntent.LoadMnemonic)
            loadEntered.await()
            assertTrue(viewModel.state.value.isLoading)

            viewModel.onIntent(DevToolsIntent.ClearMnemonic)

            assertFalse(viewModel.state.value.isLoading)
            assertNull(viewModel.state.value.mnemonicWords)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `RecoverFromSeed with 24 words emits success state`() =
        runTest {
            val mnemonic = fakeMnemonic.copyOf()
            viewModel.onIntent(DevToolsIntent.RecoverFromSeed(mnemonic))

            val state = viewModel.state.value
            assertFalse(state.isLoading)
            assertTrue(state.isRecoverSuccessful)
            assertNull(state.error)
            assertTrue(mnemonic.all { it == '\u0000' })
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `RecoverFromSeed with fewer than 24 words sets error`() =
        runTest {
            val mnemonic = "word1 word2".toCharArray()
            viewModel.onIntent(DevToolsIntent.RecoverFromSeed(mnemonic))

            val state = viewModel.state.value
            assertFalse(state.isRecoverSuccessful)
            assertNotNull(state.error)
            assertTrue(state.error.contains("2"))
            assertTrue(mnemonic.all { it == '\u0000' })
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `DismissError clears error from state`() =
        runTest {
            viewModel.onIntent(DevToolsIntent.RecoverFromSeed(charArrayOf()))
            assertNotNull(viewModel.state.value.error)

            viewModel.onIntent(DevToolsIntent.DismissError)

            assertNull(viewModel.state.value.error)
        }

    /**
     * T148c — Biometric lockout response tests (FR-HID-021).
     *
     * The biometric rate-limiting policy is enforced by Android OS / TEE.
     * These tests verify the *app's response* when it receives lockout error codes
     * via [DevToolsIntent.BiometricError]: sensitive state must be cleared
     * and an appropriate error message must be surfaced — no retry loop.
     *
     * Note: [DevToolsIntent.BiometricError] maps to BiometricPrompt error codes.
     */
    @Nested
    inner class BiometricLockoutTests {
        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Test
        fun `T148c BiometricError LOCKOUT clears mnemonic and sets error`() =
            runTest {
                // Pre-load mnemonic so we can verify it gets cleared
                viewModel.onIntent(DevToolsIntent.LoadMnemonic)
                assertNotNull(viewModel.state.value.mnemonicWords)

                // Simulate OS delivering ERROR_LOCKOUT (5 failed attempts)
                viewModel.onIntent(
                    DevToolsIntent.BiometricError(
                        errorCode = android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT,
                        message = "Too many attempts. Try again in 30 seconds.",
                    ),
                )

                val state = viewModel.state.value
                assertNull(state.mnemonicWords, "Mnemonic must be cleared on lockout")
                assertFalse(state.isMnemonicVisible)
                assertNotNull(state.error, "Error must be set on lockout")
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Test
        fun `T148c BiometricError LOCKOUT_PERMANENT clears mnemonic and sets error`() =
            runTest {
                viewModel.onIntent(DevToolsIntent.LoadMnemonic)
                assertNotNull(viewModel.state.value.mnemonicWords)

                // Simulate OS delivering ERROR_LOCKOUT_PERMANENT (device locked out permanently)
                viewModel.onIntent(
                    DevToolsIntent.BiometricError(
                        errorCode = android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT_PERMANENT,
                        message = "Too many failed attempts. Biometric is disabled.",
                    ),
                )

                val state = viewModel.state.value
                assertNull(state.mnemonicWords, "Mnemonic must be cleared on permanent lockout")
                assertFalse(state.isMnemonicVisible)
                assertNotNull(state.error)
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Test
        fun `T148c BiometricError does not trigger retry or LoadMnemonic`() =
            runTest {
                // After a lockout error, the ViewModel must NOT automatically retry.
                viewModel.onIntent(
                    DevToolsIntent.BiometricError(
                        errorCode = android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT,
                        message = "Locked out",
                    ),
                )

                // State must NOT be loading — no pending retry attempt
                assertFalse(viewModel.state.value.isLoading, "ViewModel must not retry after lockout")
            }
    }
}
