package com.chimali.fido2.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.chimali.core.common.annotation.Req
import com.chimali.fido2.data.transport.ProcessTransportSelection
import com.chimali.fido2.domain.repository.BluetoothTransportMode
import com.chimali.fido2.domain.repository.Fido2SettingsRepository
import com.chimali.fido2.domain.repository.ProcessTransportState
import com.chimali.fido2.domain.repository.TransportAvailability
import com.chimali.fido2.domain.repository.TransportPreferenceResult
import com.chimali.fido2.domain.repository.TransportSaveResult
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import org.koin.android.annotation.KoinViewModel

@Req("FR-BT-SEL-010", "FR-BT-SEL-020", "FR-BT-SEL-030", "FR-BT-SEL-040", "FR-BT-SEL-050")
data class TransportSettingsState(
    val savedChoice: BluetoothTransportMode? = null,
    val processState: ProcessTransportState =
        ProcessTransportState(null, TransportAvailability.Loading),
    val isReading: Boolean = true,
    val isSaving: Boolean = false,
    val readError: String? = null,
    val saveError: String? = null,
) {
    val restartRequired: Boolean
        get() = savedChoice != null && processState.processChoice != null && savedChoice != processState.processChoice
}

/** Presents durable selection separately from the immutable mode active in this process. */
@KoinViewModel
@Req("FR-BT-SEL-010", "FR-BT-SEL-020", "FR-BT-SEL-030", "FR-BT-SEL-040", "FR-BT-SEL-050")
class TransportSettingsViewModel(
    private val settingsRepository: Fido2SettingsRepository,
    private val processTransportSelection: ProcessTransportSelection,
) : ViewModel() {
    private val _state = MutableStateFlow(TransportSettingsState())
    val state: StateFlow<TransportSettingsState> = _state.asStateFlow()
    private val readMutex = Mutex()
    private val saveMutex = Mutex()

    init {
        viewModelScope.launch {
            processTransportSelection.state.collect { processState ->
                _state.value = _state.value.copy(processState = processState)
            }
        }
        retryRead()
    }

    @Req("FR-BT-SEL-010", "FR-BT-SEL-050")
    fun retryRead() {
        viewModelScope.launch {
            if (!readMutex.tryLock()) return@launch
            try {
                _state.value = _state.value.copy(isReading = true, readError = null)
                when (val result = settingsRepository.getTransportPreference()) {
                    is TransportPreferenceResult.Success -> {
                        _state.value = _state.value.copy(savedChoice = result.preference.mode, readError = null)
                    }
                    is TransportPreferenceResult.InvalidPreference -> {
                        _state.value =
                            _state.value.copy(
                                savedChoice = null,
                                readError = "invalid_preference",
                            )
                    }
                    is TransportPreferenceResult.ReadError -> {
                        _state.value =
                            _state.value.copy(
                                savedChoice = null,
                                readError = "read_failed",
                            )
                    }
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: IOException) {
                Logger.e(failure) { "TransportSettingsViewModel: Failed to read transport settings" }
                _state.value =
                    _state.value.copy(
                        savedChoice = null,
                        readError = "read_failed",
                    )
            } finally {
                _state.value = _state.value.copy(isReading = false)
                readMutex.unlock()
            }
        }
    }

    @Req("FR-BT-SEL-020", "FR-BT-SEL-030", "FR-BT-SEL-060")
    fun select(mode: BluetoothTransportMode) {
        if (_state.value.isReading || _state.value.isSaving || _state.value.readError != null) return
        viewModelScope.launch {
            if (!saveMutex.tryLock()) return@launch
            try {
                val current = _state.value
                if (current.savedChoice == mode) return@launch
                _state.value = current.copy(isSaving = true, saveError = null)
                when (val result = settingsRepository.saveTransportPreference(mode)) {
                    is TransportSaveResult.Success -> {
                        _state.value = _state.value.copy(savedChoice = result.preference.mode, saveError = null)
                    }
                    is TransportSaveResult.InvalidInput -> {
                        _state.value = _state.value.copy(saveError = "invalid_input")
                    }
                    is TransportSaveResult.WriteError -> {
                        _state.value = _state.value.copy(saveError = "write_failed")
                    }
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: IOException) {
                Logger.e(failure) { "TransportSettingsViewModel: Failed to save transport settings" }
                _state.value =
                    _state.value.copy(
                        saveError = "write_failed",
                    )
            } finally {
                _state.value = _state.value.copy(isSaving = false)
                saveMutex.unlock()
            }
        }
    }

    @Req("FR-BT-SEL-030")
    fun cancelPendingChange() {
        processTransportSelection.state.value.processChoice
            ?.let(::select)
    }
}
