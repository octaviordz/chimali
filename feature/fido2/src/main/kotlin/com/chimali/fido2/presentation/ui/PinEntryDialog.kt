package com.chimali.fido2.presentation.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private const val MIN_PIN_LENGTH = 4
private const val MAX_PIN_LENGTH = 64

/**
 * T065 — PIN Entry Dialog Compose component.
 *
 * A modal dialog for secure PIN entry. Uses password visual transformation
 * (dots, not characters) and optionally shows a "reveal PIN" toggle for
 * accessibility. The submit button is only enabled when the PIN meets
 * minimum length requirements.
 *
 * @param title          Dialog title, e.g. "Enter PIN to register passkey".
 * @param subtitle       Secondary text shown below the title.
 * @param errorMessage   Optional validation error to display.
 * @param minPinLength   Minimum acceptable PIN length (default 4 digits).
 * @param maxPinLength   Maximum acceptable PIN length (default 64).
 * @param onDismiss      Called when the user taps outside or presses Cancel.
 * @param onPinEnteredAndConfirmed Called when the user taps Confirm with a valid PIN.
 */
@Composable
fun PinEntryDialog(
    title: String = "Enter PIN",
    subtitle: String = "Enter your PIN to verify your identity",
    errorMessage: String? = null,
    minPinLength: Int = MIN_PIN_LENGTH,
    maxPinLength: Int = MAX_PIN_LENGTH,
    onDismiss: () -> Unit,
    onPinEnteredAndConfirmed: (pin: String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var showPin by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector        = Icons.Filled.Lock,
                contentDescription = "PIN entry"
            )
        },
        title = {
            Text(
                text      = title,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text  = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value         = pin,
                    onValueChange = { newVal ->
                        if (newVal.length <= maxPinLength) pin = newVal
                    },
                    label         = { Text("PIN") },
                    placeholder   = { Text("Enter PIN…") },
                    singleLine    = true,
                    visualTransformation = if (showPin) VisualTransformation.None
                                          else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction    = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            keyboardController?.hide()
                            if (pin.length >= minPinLength) onPinEnteredAndConfirmed(pin)
                        }
                    ),
                    isError    = errorMessage != null,
                    modifier   = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .semantics { contentDescription = "PIN input field" },
                    trailingIcon = {
                        IconButton(
                            onClick = { showPin = !showPin },
                            modifier = Modifier.semantics {
                                contentDescription = if (showPin) "Hide PIN" else "Show PIN"
                            }
                        ) {
                            Icon(
                                imageVector = if (showPin) Icons.Filled.VisibilityOff
                                              else Icons.Filled.Visibility,
                                contentDescription = null
                            )
                        }
                    }
                )

                // Error / validation hint
                val hintText = errorMessage
                    ?: if (pin.isEmpty()) "Minimum $minPinLength digits"
                    else "${pin.length}/$maxPinLength"
                Text(
                    text  = hintText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (errorMessage != null) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick  = {
                    keyboardController?.hide()
                    onPinEnteredAndConfirmed(pin)
                },
                enabled  = pin.length >= minPinLength,
                modifier = Modifier.semantics { contentDescription = "Confirm PIN button" }
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(
                onClick  = onDismiss,
                modifier = Modifier.semantics { contentDescription = "Cancel PIN button" }
            ) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}
