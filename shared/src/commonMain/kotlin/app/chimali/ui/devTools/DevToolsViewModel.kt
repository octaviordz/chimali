package app.chimali.ui.devTools

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DevToolsViewModel : ViewModel() {
    private val _text =
        MutableStateFlow<String>(
            value = "This is slideshow Fragment",
        )
    val text: StateFlow<String> = _text.asStateFlow()
}
