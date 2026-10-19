package com.chimali.fido2.presentation.ui

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
import com.chimali.core.common.annotation.Req

/** Restricts retention hints at the Android IME/autofill boundary for manually entered recovery words. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
@Req("NFR-SEC-041", "TR-AUTH-031")
internal fun SensitiveMnemonicInputPolicy(content: @Composable () -> Unit) {
    val rootView = LocalView.current.rootView
    DisposableEffect(rootView) {
        val previousAutofillImportance = rootView.importantForAutofill
        rootView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        onDispose { rootView.importantForAutofill = previousAutofillImportance }
    }

    InterceptPlatformTextInput(
        interceptor = { request, next ->
            next.startInputMethod(
                object : PlatformTextInputMethodRequest {
                    @Req("NFR-SEC-041", "TR-AUTH-031")
                    override fun createInputConnection(outAttributes: EditorInfo): InputConnection {
                        val connection = request.createInputConnection(outAttributes)
                        outAttributes.imeOptions =
                            outAttributes.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or
                            EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
                        if (outAttributes.inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT) {
                            val suggestionFlags =
                                InputType.TYPE_TEXT_FLAG_AUTO_CORRECT or InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE
                            outAttributes.inputType =
                                outAttributes.inputType and suggestionFlags.inv() or
                                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                        }
                        EditorInfoCompat.setInitialSurroundingText(outAttributes, "")
                        return connection
                    }
                },
            )
        },
        content = content,
    )
}
