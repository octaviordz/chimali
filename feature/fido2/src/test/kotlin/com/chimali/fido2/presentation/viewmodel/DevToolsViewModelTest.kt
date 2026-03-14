package com.chimali.fido2.presentation.viewmodel

import com.chimali.fido2.data.crypto.MasterSeedProvider
import com.chimali.core.security.api.HdkKeyPair
import io.mockk.coEvery
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
import org.junit.jupiter.api.Test

/**
 * T146a — Unit tests for [DevToolsViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DevToolsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val mockProvider: MasterSeedProvider = mockk()
    private val fakeMnemonic = (1..24).map { "word$it" }

    private lateinit var viewModel: DevToolsViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { mockProvider.getMasterSeed() } returns ByteArray(64)
        coEvery { mockProvider.getDeviceKeyPair() } returns mockk<HdkKeyPair>(relaxed = true)
        coEvery { mockProvider.getMnemonic() } returns fakeMnemonic
        viewModel = DevToolsViewModel(mockProvider)
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
}
