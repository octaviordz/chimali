package com.chimali.fido2.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chimali.fido2.data.crypto.MasterSeedProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

data class DevToolsUiState(
    val mnemonicWords: List<String>? = null,
    val isMnemonicVisible: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val recoverSuccess: Boolean = false
)

// ---------------------------------------------------------------------------
// Intents
// ---------------------------------------------------------------------------

sealed interface DevToolsIntent {
    /** Load the mnemonic after biometric authentication succeeds. */
    data object LoadMnemonic : DevToolsIntent
    /** Clear the mnemonic from state (e.g. on navigate away). */
    data object ClearMnemonic : DevToolsIntent
    /** Dismiss any error message. */
    data object DismissError : DevToolsIntent
    /** Attempt to re-ingest a mnemonic (e.g. from QR scan or manual entry). */
    data class RecoverFromSeed(val words: List<String>) : DevToolsIntent
}

// ---------------------------------------------------------------------------
// One-time Effects
// ---------------------------------------------------------------------------

sealed interface DevToolsEffect {
    /** Prompt the caller to show the system biometric dialog before loading. */
    data object RequestBiometric : DevToolsEffect
    data class ShowSnackbar(val message: String) : DevToolsEffect
}

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

/**
 * T146a — MVI ViewModel for the Dev Tools mnemonic export/recovery flows.
 *
 * **Security note**: mnemonic words are held only in Compose [StateFlow] state and
 * must be cleared via [DevToolsIntent.ClearMnemonic] (triggered by a `DisposableEffect`
 * in the UI) as soon as the screen is left. The strings in [DevToolsUiState.mnemonicWords]
 * are JVM `String` objects (interned), so callers should also call [System.gc] after
 * clearing; a future improvement would store them as `CharArray`.
 */
@HiltViewModel
class DevToolsViewModel @Inject constructor(
    private val masterSeedProvider: MasterSeedProvider
) : ViewModel() {

    private val _state = MutableStateFlow(DevToolsUiState())
    val state: StateFlow<DevToolsUiState> = _state.asStateFlow()

    private val _effects = Channel<DevToolsEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onIntent(intent: DevToolsIntent) {
        when (intent) {
            is DevToolsIntent.LoadMnemonic -> loadMnemonic()
            is DevToolsIntent.ClearMnemonic -> clearMnemonic()
            is DevToolsIntent.DismissError -> _state.value = _state.value.copy(error = null)
            is DevToolsIntent.RecoverFromSeed -> recoverFromSeed(intent.words)
        }
    }

    /** Called from UI after biometric succeeds to show the mnemonic. */
    private fun loadMnemonic() {
        if (_state.value.isLoading) return
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val words = masterSeedProvider.getMnemonic()
            _state.value = if (words != null) {
                _state.value.copy(
                    mnemonicWords = words,
                    isMnemonicVisible = true,
                    isLoading = false
                )
            } else {
                _state.value.copy(
                    isLoading = false,
                    error = "Master seed not initialised. Launch the authenticator first."
                )
            }
        }
    }

    private fun clearMnemonic() {
        _state.value = _state.value.copy(
            mnemonicWords = null,
            isMnemonicVisible = false,
            recoverSuccess = false,
            error = null
        )
    }

    /**
     * Validates and stores a recovering mnemonic (currently just validates word count;
     * in a real recovery flow this would re-derive and persist via WalletMasterSeedProvider).
     */
    private fun recoverFromSeed(words: List<String>) {
        if (words.size != 24) {
            _state.value = _state.value.copy(
                error = "Invalid mnemonic: expected 24 words, got ${words.size}."
            )
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            // TODO T146-future: persist recovered mnemonic via WalletMasterSeedProvider
            _state.value = _state.value.copy(isLoading = false, recoverSuccess = true)
            _effects.send(DevToolsEffect.ShowSnackbar("Mnemonic validated (${words.size} words). Implement persistence in T146-future."))
        }
    }
}
