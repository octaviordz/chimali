package com.chimali.feature.vault.ui

import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.core.view.inputmethod.EditorInfoCompat

/** FR-VAULT-026 / I.5: request minimal IME retention; third-party IME compliance is not guaranteed. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun VaultInputPolicy(content: @Composable () -> Unit) {
    // Autofill is controlled by the Android view root; the Compose focus host is not always
    // exposed as a parent of the currently focused editor.
    val hostView = LocalView.current.rootView
    DisposableEffect(hostView) {
        val previousAutofillImportance = hostView.importantForAutofill
        hostView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        onDispose { hostView.importantForAutofill = previousAutofillImportance }
    }
    InterceptPlatformTextInput(
        interceptor = { request, next ->
            next.startInputMethod(
                object : PlatformTextInputMethodRequest {
                    override fun createInputConnection(outAttributes: EditorInfo): InputConnection {
                        val connection = request.createInputConnection(outAttributes)
                        outAttributes.imeOptions = protectedImeOptions(outAttributes.imeOptions)
                        outAttributes.inputType = protectedInputType(outAttributes.inputType)
                        EditorInfoCompat.setInitialSurroundingText(outAttributes, "")
                        return connection
                    }
                },
            )
        },
        content = content,
    )
}

internal fun protectedImeOptions(options: Int): Int =
    options or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or
        EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN

internal fun protectedInputType(type: Int): Int =
    if (type and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT) {
        type and (InputType.TYPE_TEXT_FLAG_AUTO_CORRECT or InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE).inv() or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    } else {
        type
    }
