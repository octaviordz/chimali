package com.chimali.core.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidClipboardManagerService @Inject constructor(
    @ApplicationContext private val context: Context
) : ClipboardManagerService {

    private val clipboardManager: ClipboardManager? by lazy {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    }

    // Use Main-Immediate for UI clipboard operations, but we can manage the delay jobs
    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private var clearJob: Job? = null

    override fun copySensitiveData(label: String, text: String, clearDelayMs: Long) {
        val manager = clipboardManager ?: return
        
        // Android 13+ has built-in UI for clipboard that might show sensitive data. 
        // We set the "is_sensitive" extra on the ClipData to prevent it showing up in the UI.
        val clipData = ClipData.newPlainText(label, text).apply {
            description.extras = android.os.PersistableBundle().apply {
                putBoolean("is_sensitive", true) // Maps to ClipDescription.EXTRA_IS_SENSITIVE on API 33+
            }
        }
        
        manager.setPrimaryClip(clipData)

        // Reset the timer
        clearJob?.cancel()
        clearJob = scope.launch {
            delay(clearDelayMs)
            clearClipboard()
        }
    }

    override fun clearClipboard() {
        clearJob?.cancel()
        clearJob = null
        
        val manager = clipboardManager ?: return
        // Clearing clipboard relies on setting empty data or clearing the primary clip.
        // API 28+ supports clearPrimaryClip().
        manager.clearPrimaryClip()
    }
}
