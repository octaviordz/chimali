package com.chimali.fido2.presentation.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chimali.fido2.domain.service.VerificationMethod
import com.chimali.fido2.presentation.viewmodel.*
import kotlinx.coroutines.flow.collectLatest

/**
 * T063 — FIDO2 Registration Prompt Screen.
 *
 * Shown to the user when a website/app requests a new passkey credential.
 * Collects user consent (confirm or cancel), then delegates to biometric or PIN.
 *
 * @param onSuccess   Called with the new credential after successful registration.
 * @param onCancel    Called when user dismisses without registering.
 * @param viewModel   Hilt-injected [RegistrationPromptViewModel].
 */
@Composable
fun RegistrationPromptScreen(
    onSuccess: (credentialId: String) -> Unit,
    onCancel: () -> Unit,
    viewModel: RegistrationPromptViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Handle one-shot effects
    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is RegistrationEffect.NavigateBack                -> onCancel()
                is RegistrationEffect.NavigateToSuccess          -> onSuccess(effect.credential.id)
                is RegistrationEffect.NavigateToBiometricPrompt -> { /* handled inline */ }
                is RegistrationEffect.NavigateToPinEntry        -> { /* handled inline */ }
                is RegistrationEffect.ShowSnackbar              -> { /* handled via state */ }
            }
        }
    }

    RegistrationPromptContent(
        state = state,
        onConfirm    = { viewModel.handleIntent(RegistrationIntent.ConfirmRegistration) },
        onCancel     = { viewModel.handleIntent(RegistrationIntent.CancelRegistration) },
        onBiometric  = { viewModel.handleIntent(RegistrationIntent.UseBiometric) },
        onPinSubmit  = { viewModel.handleIntent(RegistrationIntent.UsePinVerification(it)) },
        onRetry      = { viewModel.handleIntent(RegistrationIntent.Retry) }
    )
}

@Composable
internal fun RegistrationPromptContent(
    state: RegistrationState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onBiometric: () -> Unit,
    onPinSubmit: (String) -> Unit,
    onRetry: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color    = MaterialTheme.colorScheme.background
    ) {
        AnimatedContent(
            targetState = state,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "registration-state"
        ) { currentState ->
            when (currentState) {
                is RegistrationState.Idle -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is RegistrationState.AwaitingUserConsent -> {
                    AwaitingConsentContent(
                        state     = currentState,
                        onConfirm = onConfirm,
                        onCancel  = onCancel
                    )
                }

                is RegistrationState.AwaitingBiometric -> {
                    Box(
                        Modifier.fillMaxSize().semantics { contentDescription = "Biometric prompt" },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector        = Icons.Filled.Lock,
                                contentDescription = "Biometric",
                                modifier           = Modifier.size(72.dp),
                                tint               = MaterialTheme.colorScheme.primary
                            )
                            Text("Verifying identity…", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }

                is RegistrationState.AwaitingPin -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        PinEntryDialog(
                            title      = "Enter PIN",
                            subtitle   = "Verify your PIN to continue",
                            onDismiss  = onCancel,
                            onPinEnteredAndConfirmed = onPinSubmit
                        )
                    }
                }

                is RegistrationState.Processing -> {
                    Box(
                        Modifier.fillMaxSize().semantics { contentDescription = "Registering credential" },
                        contentAlignment = Alignment.Center
                    ) {
                        RegistrationProgressIndicator()
                    }
                }

                is RegistrationState.Success -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier            = Modifier.padding(32.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(80.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Filled.Lock,
                                        contentDescription = "Success",
                                        modifier           = Modifier.size(40.dp),
                                        tint               = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            Text(
                                "Passkey created!",
                                style      = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "You can now sign in with your passkey.",
                                textAlign = TextAlign.Center,
                                color     = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                is RegistrationState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier            = Modifier.padding(32.dp)
                        ) {
                            Text(
                                "Registration failed",
                                style      = MaterialTheme.typography.titleLarge,
                                color      = MaterialTheme.colorScheme.error
                            )
                            Text(currentState.message, textAlign = TextAlign.Center)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(onClick = onCancel) { Text("Cancel") }
                                if (currentState.isRetryable) {
                                    Button(onClick = onRetry) { Text("Try again") }
                                }
                            }
                        }
                    }
                }

                is RegistrationState.Cancelled -> {
                    // Will navigate away; show nothing
                }
            }
        }
    }
}

@Composable
private fun AwaitingConsentContent(
    state: RegistrationState.AwaitingUserConsent,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Shield icon
        Surface(
            shape    = CircleShape,
            color    = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(96.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector        = Icons.Filled.Lock,
                    contentDescription = "Security",
                    modifier           = Modifier.size(48.dp),
                    tint               = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text       = "Create Passkey",
            style      = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        // Site/RP card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text  = state.rpName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text  = state.rpId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // User card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Signing in as", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = state.userDisplayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                if (state.userName != state.userDisplayName) {
                    Text(state.userName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Verification method info
        if (state.availableMethod != VerificationMethod.NONE) {
            val methodLabel = when (state.availableMethod) {
                VerificationMethod.BIOMETRIC -> "You'll verify with biometric"
                VerificationMethod.PIN       -> "You'll verify with your PIN"
                else                         -> "Verification required"
            }
            Text(
                text  = methodLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
        }

        Spacer(Modifier.height(16.dp))

        // Action buttons
        Button(
            onClick  = onConfirm,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Confirm registration button" }
        ) {
            Text("Create Passkey")
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick  = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Cancel registration button" }
        ) {
            Text("Cancel")
        }
    }
}
