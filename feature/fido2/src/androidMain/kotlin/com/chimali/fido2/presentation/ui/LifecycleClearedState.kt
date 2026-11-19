package com.chimali.fido2.presentation.ui

import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect

/** Collects screen state while visible and drops its last Compose-owned value when stopped. */
@androidx.compose.runtime.Composable
internal fun <T> StateFlow<T>.collectAsStateWithLifecycleAndReset(stoppedValue: T): State<T> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return produceState(initialValue = stoppedValue, this, lifecycle, stoppedValue) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try {
                collect { value = it }
            } finally {
                value = stoppedValue
            }
        }
    }
}
