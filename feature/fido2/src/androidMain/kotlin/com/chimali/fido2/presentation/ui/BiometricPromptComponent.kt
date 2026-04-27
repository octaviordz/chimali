package com.chimali.fido2.presentation.ui

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * T064 — Biometric Prompt Compose component.
 *
 * Wraps Android's [BiometricPrompt] (from `androidx.biometric`) and exposes it as
 * a Compose-friendly composable. Shows a system biometric dialog (fingerprint/face/iris)
 * and surfaces the result via callbacks.
 *
 * @param title         Title shown in the biometric dialog (e.g. "Sign in with passkey").
 * @param subtitle      Subtitle, typically the RP name.
 * @param description   Optional longer description.
 * @param negativeButtonText Text of the "use password" fallback button.
 * @param onSuccess     Called when biometric authentication succeeds.
 * @param onError       Called on permanent failure (too many attempts, locked out, etc.).
 * @param onFallback    Called when the user taps the negative/fallback button.
 */
@Suppress(
    // TODO: Add modifier parameter in follow-up refactor
    "ModifierMissing",
    // TODO: Use rememberUpdatedState for lambda params in DisposableEffect
    "LambdaParameterInRestartableEffect",
    // TODO: Reorder params (lambdas should be last) in follow-up refactor
    "ComposableParamOrder",
    "FunctionNaming",
    "ForbiddenComment",
)
@Composable
fun BiometricPromptComponent(
    title: String,
    subtitle: String,
    description: String? = null,
    negativeButtonText: String = "Use PIN instead",
    onSuccess: () -> Unit,
    onError: (errorCode: Int, message: String) -> Unit,
    onFallback: () -> Unit,
) {
    val context = LocalContext.current

    // Display the Android system biometric dialog when this composable enters composition
    DisposableEffect(title, subtitle) {
        val executor = ContextCompat.getMainExecutor(context)
        val activity = context as? FragmentActivity

        val promptInfo =
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .apply {
                    description?.let { setDescription(it) }
                    setNegativeButtonText(negativeButtonText)
                    val authenticators =
                        BiometricManager.Authenticators.BIOMETRIC_STRONG or
                            BiometricManager.Authenticators.BIOMETRIC_WEAK
                    setAllowedAuthenticators(authenticators)
                }
                .build()

        val biometricPrompt =
            activity?.let {
                BiometricPrompt(
                    it,
                    executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            onSuccess()
                        }

                        override fun onAuthenticationError(
                            errorCode: Int,
                            errString: CharSequence,
                        ) {
                            if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                                errorCode == BiometricPrompt.ERROR_USER_CANCELED
                            ) {
                                onFallback()
                            } else {
                                onError(errorCode, errString.toString())
                            }
                        }

                        override fun onAuthenticationFailed() {
                            // Partial attempt — dialog remains open, no action needed
                        }
                    },
                )
            }

        biometricPrompt?.authenticate(promptInfo)

        onDispose {
            biometricPrompt?.cancelAuthentication()
        }
    }

    // Visual affordance while system dialog is being shown
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Biometric authentication prompt" },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(96.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Face,
                        contentDescription = "Fingerprint icon",
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))

            TextButton(onClick = onFallback) {
                Text(negativeButtonText)
            }
        }
    }
}
