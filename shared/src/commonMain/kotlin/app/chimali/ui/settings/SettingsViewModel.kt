package app.chimali.ui.settings

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel : ViewModel() {
    private val _text =
        MutableStateFlow(
            value = "This is settings Fragment",
        )
    val text: StateFlow<String> = _text.asStateFlow()
}
