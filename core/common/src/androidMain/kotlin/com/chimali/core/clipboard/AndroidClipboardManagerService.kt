package com.chimali.core.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single

@Single(binds = [ClipboardManagerService::class])
class AndroidClipboardManagerService(
    private val context: Context,
) : ClipboardManagerService {
    private val clipboardManager: ClipboardManager? by lazy {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    }

    // Use Main-Immediate for UI clipboard operations, but we can manage the delay jobs
    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private var clearJob: Job? = null

    // Use Mutex for thread-safe clipboard access
    private val clipboardMutex = Mutex()

    // Note: Clipboard state monitoring can be added later when event system is integrated
    // private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
    //     val hasContent = clipboardManager?.primaryClip != null
    //     eventBus.publish(ClipboardEvent.ContentChanged(
    //         timestamp = System.currentTimeMillis(),
    //         hasContent = hasContent,
    //         isSensitive = hasContent &&
    //             clipboardManager?.primaryClip?.description?.extras?.getBoolean("is_sensitive") == true
    //     ))
    // }

    override suspend fun copySensitiveData(
        label: String,
        text: String,
        clearDelayMs: Long,
    ): Result<Unit> {
        return try {
            val manager =
                clipboardManager
                    ?: return Result.failure(ClipboardError.ClipboardUnavailable)

            // Android 13+ has built-in UI for clipboard that might show sensitive data.
            // We set the "is_sensitive" extra on the ClipData to prevent it showing up in the UI.
            val clipData =
                ClipData.newPlainText(label, text).apply {
                    description.extras =
                        android.os.PersistableBundle().apply {
                            putBoolean("is_sensitive", true) // Maps to ClipDescription.EXTRA_IS_SENSITIVE on API 33+
                        }
                }

            manager.setPrimaryClip(clipData)

            // Reset the timer
            clearJob?.cancel()
            clearJob =
                scope.launch {
                    delay(clearDelayMs)
                    clearClipboard()
                }

            Result.success(Unit)
        } catch (e: SecurityException) {
            Result.failure(ClipboardError.CopyFailed("Security exception: ${e.message}", e))
        } catch (e: IllegalStateException) {
            Result.failure(
                ClipboardError.PlatformError("Android", e::class.simpleName, e.message ?: "Unknown error", e),
            )
        }
    }

    override suspend fun clearClipboard(): Result<Unit> {
        return try {
            // Ensure thread-safe clipboard access
            return clipboardMutex.withLock {
                clearJob?.cancel()
                clearJob = null

                val manager =
                    clipboardManager
                        ?: return@withLock Result.failure(ClipboardError.ClipboardUnavailable)

                // Clearing clipboard relies on setting empty data or clearing the primary clip.
                // API 28+ supports clearPrimaryClip().
                manager.clearPrimaryClip()

                // Note: Auto-clear event publishing can be added when event system is integrated
                // eventBus.publish(ClipboardEvent.AutoCleared(
                //     timestamp = System.currentTimeMillis(),
                //     reason = "Manual clear"
                // ))

                Result.success(Unit)
            }
        } catch (e: SecurityException) {
            Result.failure(ClipboardError.ClearFailed("Security exception: ${e.message}", e))
        } catch (e: IllegalStateException) {
            Result.failure(
                ClipboardError.PlatformError("Android", e.javaClass.simpleName, e.message ?: "Unknown error", e),
            )
        }
    }
}
