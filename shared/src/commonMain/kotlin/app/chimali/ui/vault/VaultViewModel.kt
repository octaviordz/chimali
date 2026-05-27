package app.chimali.ui.vault

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class VaultViewModel : ViewModel() {
    private val _texts =
        MutableStateFlow<List<String>>(
            (1..16).map { i -> "This is item # $i" },
        )

    val texts: StateFlow<List<String>> = _texts.asStateFlow()
}
