package app.chimali.ui.transform

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TransformViewModel : ViewModel() {

    // 1. Initialize data safely using a multiplatform MutableStateFlow
    private val _texts = MutableStateFlow<List<String>>(
        (1..16).map { i -> "This is item # $i" }
    )

    // 2. Expose an immutable StateFlow for your UI layer to observe safely
    val texts: StateFlow<List<String>> = _texts.asStateFlow()
}
