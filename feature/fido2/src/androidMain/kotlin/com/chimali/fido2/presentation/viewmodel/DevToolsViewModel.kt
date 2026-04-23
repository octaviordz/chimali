package com.chimali.fido2.presentation.viewmodel

import org.koin.android.annotation.KoinViewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chimali.core.clipboard.ClipboardManagerService
import com.chimali.fido2.data.crypto.ImportMnemonicResult
import com.chimali.fido2.data.crypto.MasterSeedProvider
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import co.touchlab.kermit.Logger

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

data class DevToolsUiState(
    val mnemonicWords: List<String>? = null,
    val isMnemonicVisible: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val recoverSuccess: Boolean = false,
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

    /** Support copying the mnemonic words to the clipboard securely. */
    data object CopyToClipboard : DevToolsIntent

    /** Attempt to re-ingest a mnemonic (e.g. from QR scan or manual entry). */
    data class RecoverFromSeed(val words: List<String>) : DevToolsIntent

    /**
     * T148c — Notifies the ViewModel that Android BiometricPrompt reported an error.
     *
     * Specifically handles [android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT]
     * and [android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT_PERMANENT].
     * On lockout the ViewModel must clear any sensitive state from memory immediately.
     */
    data class BiometricError(val errorCode: Int, val message: String) : DevToolsIntent
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
@KoinViewModel
class DevToolsViewModel(
        private val masterSeedProvider: MasterSeedProvider,
        private val clipboardManagerService: ClipboardManagerService,
    ) : ViewModel() {
        private val _state = MutableStateFlow(DevToolsUiState())
        val state: StateFlow<DevToolsUiState> = _state.asStateFlow()

        private val _effects = Channel<DevToolsEffect>(Channel.BUFFERED)
        val effects: Flow<DevToolsEffect> = _effects.receiveAsFlow()

        fun onIntent(intent: DevToolsIntent) {
            when (intent) {
                is DevToolsIntent.LoadMnemonic -> loadMnemonic()
                is DevToolsIntent.ClearMnemonic -> clearMnemonic()
                is DevToolsIntent.DismissError -> _state.update { it.copy(error = null) }
                is DevToolsIntent.RecoverFromSeed -> recoverFromSeed(intent.words)
                is DevToolsIntent.CopyToClipboard -> {
                    _state.value.mnemonicWords?.joinToString(" ")?.let { text ->
                        viewModelScope.launch {
                            clipboardManagerService.copySensitiveData("Chimali Master Seed", text)
                            _effects.send(DevToolsEffect.ShowSnackbar("Mnemonic copied safely. It will clear in 60s."))
                        }
                    }
                }
                is DevToolsIntent.BiometricError -> handleBiometricError(intent.errorCode, intent.message)
            }
        }

        /**
         * T148c — Handles biometric authentication errors from the OS, including lockout
         * ([android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT] and
         * [android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT_PERMANENT]).
         *
         * Clears mnemonic from state immediately regardless of error code. No retry is attempted.
         */
        private fun handleBiometricError(
            errorCode: Int,
            message: String,
        ) {
            Logger.w { "Biometric error received: code=$errorCode, message=$message" }
            _state.update {
                it.copy(
                    mnemonicWords = null,
                    isMnemonicVisible = false,
                    isLoading = false,
                    error = message,
                )
            }
        }

        /** Called from UI after biometric succeeds to show the mnemonic. */
        private fun loadMnemonic() {
            if (_state.value.isLoading) return
            _state.update { it.copy(isLoading = true, error = null) }
            viewModelScope.launch {
                val words = masterSeedProvider.getMnemonic()
                _state.update { currentState ->
                    if (words != null) {
                        currentState.copy(
                            mnemonicWords = words,
                            isMnemonicVisible = true,
                            isLoading = false,
                        )
                    } else {
                        currentState.copy(
                            isLoading = false,
                            error = "Master seed not initialized. Launch the authenticator first.",
                        )
                    }
                }
            }
        }

        private fun clearMnemonic() {
            _state.update {
                it.copy(
                    mnemonicWords = null,
                    isMnemonicVisible = false,
                    recoverSuccess = false,
                    error = null,
                )
            }
        }

        /**
         * Validates the mnemonic word count, then delegates persistence to [MasterSeedProvider].
         *
         * Converts the word list to a [CharArray] before passing it to [MasterSeedProvider.importMnemonic] so that
         * the provider can zero the sensitive material after use (Constitution §I).
         *
         * If a seed already existed, the user is warned that previous credentials are orphaned.
         */
        private fun recoverFromSeed(words: List<String>) {
            if (words.size != 24) {
                _state.update {
                    it.copy(error = "Invalid mnemonic: expected 24 words, got ${words.size}.")
                }
                return
            }
            viewModelScope.launch {
                _state.update { it.copy(isLoading = true, error = null) }
                try {
                    val mnemonicChars = words.joinToString(" ").toCharArray()
                    val result = masterSeedProvider.importMnemonic(mnemonicChars)
                    // mnemonicChars is zeroed by the provider; do not use it after this point.

                    val message =
                        when (result) {
                            is ImportMnemonicResult.Created ->
                                "Mnemonic imported and persisted successfully."
                            is ImportMnemonicResult.Replaced ->
                                "⚠️ Existing seed overwritten. Re-registration required for previous credentials."
                        }
                    _state.update { it.copy(isLoading = false, recoverSuccess = true) }
                    _effects.send(DevToolsEffect.ShowSnackbar(message))
                } catch (e: IllegalArgumentException) {
                    Logger.e(e) { "Invalid mnemonic provided for recovery" }
                    _state.update { it.copy(isLoading = false, error = e.message) }
                } catch (e: Exception) {
                    Logger.e(e) { "Failed to import mnemonic: ${e.message ?: "Unknown error"}" }
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = "Failed to import mnemonic: ${e.message}",
                        )
                    }
                }
            }
        }
    }
