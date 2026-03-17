package com.chimali.fido2.presentation.viewmodel

import com.chimali.core.clipboard.ClipboardManagerService
import com.chimali.fido2.data.crypto.ImportMnemonicResult
import com.chimali.fido2.data.crypto.MasterSeedProvider
import com.chimali.core.security.api.HdkKeyPair
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * T146a — Unit tests for [DevToolsViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DevToolsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val mockProvider: MasterSeedProvider = mockk()
    private val mockClipboard: ClipboardManagerService = mockk(relaxed = true)
    private val fakeMnemonic = (1..24).map { "word$it" }

    private lateinit var viewModel: DevToolsViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { mockProvider.getMasterSeed() } returns ByteArray(64)
        coEvery { mockProvider.getDeviceKeyPair() } returns mockk<HdkKeyPair>(relaxed = true)
        coEvery { mockProvider.getMnemonic() } returns fakeMnemonic
        coEvery { mockProvider.importMnemonic(any()) } returns ImportMnemonicResult.Created
        viewModel = DevToolsViewModel(mockProvider, mockClipboard)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `LoadMnemonic returns word list when provider has mnemonic`() = runTest {
        viewModel.onIntent(DevToolsIntent.LoadMnemonic)

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals(fakeMnemonic, state.mnemonicWords)
        assertTrue(state.isMnemonicVisible)
        assertNull(state.error)
    }

    @Test
    fun `LoadMnemonic sets error when provider returns null`() = runTest {
        coEvery { mockProvider.getMnemonic() } returns null
        viewModel.onIntent(DevToolsIntent.LoadMnemonic)

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertNull(state.mnemonicWords)
        assertNotNull(state.error)
    }

    @Test
    fun `ClearMnemonic zeroes words from state`() = runTest {
        viewModel.onIntent(DevToolsIntent.LoadMnemonic)
        assertNotNull(viewModel.state.value.mnemonicWords)

        viewModel.onIntent(DevToolsIntent.ClearMnemonic)

        val state = viewModel.state.value
        assertNull(state.mnemonicWords)
        assertFalse(state.isMnemonicVisible)
        assertNull(state.error)
    }

    @Test
    fun `RecoverFromSeed with 24 words emits success state`() = runTest {
        viewModel.onIntent(DevToolsIntent.RecoverFromSeed(fakeMnemonic))

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertTrue(state.recoverSuccess)
        assertNull(state.error)
    }

    @Test
    fun `RecoverFromSeed with fewer than 24 words sets error`() = runTest {
        viewModel.onIntent(DevToolsIntent.RecoverFromSeed(listOf("word1", "word2")))

        val state = viewModel.state.value
        assertFalse(state.recoverSuccess)
        assertNotNull(state.error)
        assertTrue(state.error!!.contains("2"))
    }

    @Test
    fun `DismissError clears error from state`() = runTest {
        viewModel.onIntent(DevToolsIntent.RecoverFromSeed(emptyList()))
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

        @Test
        fun `T148c BiometricError LOCKOUT clears mnemonic and sets error`() = runTest {
            // Pre-load mnemonic so we can verify it gets cleared
            viewModel.onIntent(DevToolsIntent.LoadMnemonic)
            assertNotNull(viewModel.state.value.mnemonicWords)

            // Simulate OS delivering ERROR_LOCKOUT (5 failed attempts)
            viewModel.onIntent(DevToolsIntent.BiometricError(
                errorCode = android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT,
                message = "Too many attempts. Try again in 30 seconds."
            ))

            val state = viewModel.state.value
            assertNull(state.mnemonicWords, "Mnemonic must be cleared on lockout")
            assertFalse(state.isMnemonicVisible)
            assertNotNull(state.error, "Error must be set on lockout")
        }

        @Test
        fun `T148c BiometricError LOCKOUT_PERMANENT clears mnemonic and sets error`() = runTest {
            viewModel.onIntent(DevToolsIntent.LoadMnemonic)
            assertNotNull(viewModel.state.value.mnemonicWords)

            // Simulate OS delivering ERROR_LOCKOUT_PERMANENT (device locked out permanently)
            viewModel.onIntent(DevToolsIntent.BiometricError(
                errorCode = android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT_PERMANENT,
                message = "Too many failed attempts. Biometric is disabled."
            ))

            val state = viewModel.state.value
            assertNull(state.mnemonicWords, "Mnemonic must be cleared on permanent lockout")
            assertFalse(state.isMnemonicVisible)
            assertNotNull(state.error)
        }

        @Test
        fun `T148c BiometricError does not trigger retry or LoadMnemonic`() = runTest {
            // After a lockout error, the ViewModel must NOT automatically retry.
            viewModel.onIntent(DevToolsIntent.BiometricError(
                errorCode = android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT,
                message = "Locked out"
            ))

            // State must NOT be loading — no pending retry attempt
            assertFalse(viewModel.state.value.isLoading, "ViewModel must not retry after lockout")
        }
    }
}
