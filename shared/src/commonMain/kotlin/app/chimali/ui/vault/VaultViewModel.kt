package app.chimali.ui.vault

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class VaultViewModel : ViewModel() {
    private val _text =
        MutableStateFlow(
            value = "This is reflow Fragment",
        )

    val text: StateFlow<String> = _text.asStateFlow()
}
