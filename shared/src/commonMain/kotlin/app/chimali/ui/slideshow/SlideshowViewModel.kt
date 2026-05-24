package app.chimali.ui.slideshow

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SlideshowViewModel : ViewModel() {
    private val _text =
        MutableStateFlow<String>(
            value = "This is slideshow Fragment",
        )
    val text: StateFlow<String> = _text.asStateFlow()
}
