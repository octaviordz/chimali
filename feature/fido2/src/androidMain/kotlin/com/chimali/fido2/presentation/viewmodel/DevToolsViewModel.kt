package com.chimali.fido2.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.chimali.core.clipboard.ClipboardManagerService
import com.chimali.core.common.annotation.Req
import com.chimali.core.security.api.ImportMnemonicResult
import com.chimali.core.security.api.MasterSeedOwnerKind
import com.chimali.core.security.api.MasterSeedProvider
import java.security.GeneralSecurityException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

private const val MNEMONIC_WORD_COUNT = 24

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

data class DevToolsUiState(
    val mnemonicWords: CharArray? = null,
    val isMnemonicVisible: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isRecoverSuccessful: Boolean = false,
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
    data class RecoverFromSeed(
        /** Mutable mnemonic ownership transfers to the ViewModel and is erased on every exit. */
        val mnemonic: CharArray,
    ) : DevToolsIntent

    /**
     * T148c — Notifies the ViewModel that Android BiometricPrompt reported an error.
     *
     * Specifically handles [android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT]
     * and [android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT_PERMANENT].
     * On lockout the ViewModel must clear any sensitive state from memory immediately.
     */
    data class BiometricError(
        val errorCode: Int,
        val message: String,
    ) : DevToolsIntent
}

// ---------------------------------------------------------------------------
// One-time Effects
// ---------------------------------------------------------------------------

sealed interface DevToolsEffect {
    /** Prompt the caller to show the system biometric dialog before loading. */
    data object RequestBiometric : DevToolsEffect

    data class ShowSnackbar(
        val message: String,
    ) : DevToolsEffect
}

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

/**
 * T146a — MVI ViewModel for the Dev Tools mnemonic export/recovery flows.
 *
 * **Security note**: the ViewModel owns the revealed mnemonic `CharArray`. The UI clears it
 * through [DevToolsIntent.ClearMnemonic] when hidden or disposed. Immutable copies are limited
 * to the explicit platform rendering/clipboard boundary.
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

    private var mnemonicLoadJob: Job? = null
    private var mnemonicImportJob: Job? = null
    private var mnemonicImportBuffer: CharArray? = null
    private var clipboardCopyJob: Job? = null
    private var mnemonicLoadGeneration: Long = 0
    private var mnemonicImportGeneration: Long = 0

    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun onIntent(intent: DevToolsIntent) {
        when (intent) {
            is DevToolsIntent.LoadMnemonic -> loadMnemonic()
            is DevToolsIntent.ClearMnemonic -> clearMnemonic()
            is DevToolsIntent.DismissError -> _state.update { it.copy(error = null) }
            is DevToolsIntent.RecoverFromSeed -> recoverFromSeed(intent.mnemonic)
            is DevToolsIntent.CopyToClipboard -> {
                _state.value.mnemonicWords?.let { words ->
                    val text = String(words)
                    clipboardCopyJob?.cancel()
                    clipboardCopyJob =
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
        cancelMnemonicReveal()
        clipboardCopyJob?.cancel()
        clipboardCopyJob = null
        eraseRevealedMnemonic()
        Logger.w { "Biometric error received: code=$errorCode" }
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
        val generation = ++mnemonicLoadGeneration
        mnemonicLoadJob?.cancel()
        eraseRevealedMnemonic()
        _state.update { it.copy(isLoading = true, error = null) }
        mnemonicLoadJob =
            viewModelScope.launch {
                var ownedWords: CharArray? = null
                var transferred = false
                try {
                    masterSeedProvider.withSessionOwner(
                        initialize = false,
                        ownerKind = MasterSeedOwnerKind.FOREGROUND,
                    ) { _ ->
                        ownedWords = masterSeedProvider.getMnemonic()
                        currentCoroutineContext().ensureActive()
                        if (generation != mnemonicLoadGeneration) return@withSessionOwner
                        val words = ownedWords
                        if (words != null) {
                            _state.update {
                                it.copy(mnemonicWords = words, isMnemonicVisible = true, isLoading = false)
                            }
                            transferred = true
                            // Keep the foreground owner for the entire reveal lifetime. Hide,
                            // replacement, disposal or a provider generation fence cancels this job.
                            kotlinx.coroutines.awaitCancellation()
                        } else {
                            _state.update {
                                it.copy(
                                    mnemonicWords = null,
                                    isMnemonicVisible = false,
                                    isLoading = false,
                                    error = "Master seed not initialized. Launch the authenticator first.",
                                )
                            }
                        }
                    }
                } finally {
                    if (!transferred) {
                        ownedWords?.fill('\u0000')
                    } else if (!currentCoroutineContext().isActive) {
                        ownedWords?.fill('\u0000')
                        _state.update { it.copy(mnemonicWords = null, isMnemonicVisible = false, isLoading = false) }
                    }
                    if (generation == mnemonicLoadGeneration) _state.update { it.copy(isLoading = false) }
                }
            }
    }

    private fun clearMnemonic() {
        cancelMnemonicReveal()
        clipboardCopyJob?.cancel()
        clipboardCopyJob = null
        eraseRevealedMnemonic()
        _state.update {
            it.copy(
                mnemonicWords = null,
                isMnemonicVisible = false,
                isLoading = false,
                isRecoverSuccessful = false,
                error = null,
            )
        }
    }

    /**
     * Validates the mnemonic word count, then delegates persistence to [MasterSeedProvider].
     *
     * Converts platform input words to an owned [CharArray]; the provider consumes and erases it.
     *
     * If a seed already existed, the user is warned that previous credentials are orphaned.
     */

    private fun recoverFromSeed(mnemonic: CharArray) {
        val wordCount = countMnemonicWords(mnemonic)
        if (wordCount != MNEMONIC_WORD_COUNT) {
            mnemonic.fill('\u0000')
            _state.update {
                it.copy(error = "Invalid mnemonic: expected $MNEMONIC_WORD_COUNT words, got $wordCount.")
            }
            return
        }
        cancelMnemonicReveal()
        eraseRevealedMnemonic()
        clipboardCopyJob?.cancel()
        clipboardCopyJob = null
        _state.update { it.copy(mnemonicWords = null, isMnemonicVisible = false) }
        val generation = ++mnemonicImportGeneration
        mnemonicImportJob?.cancel()
        mnemonicImportBuffer?.fill('\u0000')
        mnemonicImportBuffer = mnemonic
        val importJob =
            viewModelScope.launch {
                _state.update { it.copy(isLoading = true, error = null) }
                try {
                    val result = masterSeedProvider.importMnemonic(mnemonic)

                    val message =
                        when (result) {
                            is ImportMnemonicResult.Created ->
                                "Mnemonic imported and persisted successfully."
                            is ImportMnemonicResult.Replaced ->
                                "⚠️ Existing seed overwritten. Re-registration required for previous credentials."
                        }
                    _state.update { it.copy(isLoading = false, isRecoverSuccessful = true) }
                    _effects.send(DevToolsEffect.ShowSnackbar(message))
                } catch (_: IllegalArgumentException) {
                    Logger.e { "Mnemonic recovery input was rejected" }
                    _state.update { it.copy(isLoading = false, error = "Recovery phrase is invalid.") }
                } catch (_: GeneralSecurityException) {
                    Logger.e { "Mnemonic recovery security operation failed" }
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = "Security operation failed while importing the recovery phrase.",
                        )
                    }
                } finally {
                    mnemonic.fill('\u0000')
                    if (mnemonicImportBuffer === mnemonic) mnemonicImportBuffer = null
                    if (generation == mnemonicImportGeneration) _state.update { it.copy(isLoading = false) }
                }
            }
        // A job cancelled before its dispatcher starts the body never enters the try/finally.
        // Completion cleanup covers that queued-but-never-started ownership path as well.
        importJob.invokeOnCompletion { mnemonic.fill('\u0000') }
        mnemonicImportJob = importJob
    }

    private fun eraseRevealedMnemonic() {
        _state.value.mnemonicWords?.fill('\u0000')
    }

    private fun cancelMnemonicReveal() {
        mnemonicLoadGeneration += 1
        mnemonicLoadJob?.cancel()
        mnemonicLoadJob = null
    }

    private fun countMnemonicWords(mnemonic: CharArray): Int {
        var count = 0
        var inWord = false
        for (character in mnemonic) {
            if (character.isWhitespace()) {
                inWord = false
            } else if (!inWord) {
                count += 1
                inWord = true
            }
        }
        return count
    }

    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun onCleared() {
        mnemonicLoadGeneration += 1
        mnemonicImportGeneration += 1
        mnemonicLoadJob?.cancel()
        mnemonicImportJob?.cancel()
        clipboardCopyJob?.cancel()
        mnemonicImportBuffer?.fill('\u0000')
        mnemonicImportBuffer = null
        eraseRevealedMnemonic()
        _state.update { it.copy(mnemonicWords = null, isMnemonicVisible = false, isLoading = false) }
        super.onCleared()
    }
}
