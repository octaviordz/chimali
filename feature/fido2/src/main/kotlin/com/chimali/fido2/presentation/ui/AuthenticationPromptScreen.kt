package com.chimali.fido2.presentation.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chimali.fido2.presentation.viewmodel.*
import kotlinx.coroutines.flow.collectLatest

/**
 * T096 — FIDO2 Authentication Prompt Screen.
 *
 * Shown when a website requests sign-in with an existing passkey.
 * Collects user consent then delegates to biometric or PIN.
 */
@Composable
fun AuthenticationPromptScreen(
    onSuccess: (credentialId: String) -> Unit,
    onCancel: () -> Unit,
    viewModel: AuthenticationPromptViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is AuthenticationEffect.NavigateBack          -> onCancel()
                is AuthenticationEffect.NavigateToSuccess    -> onSuccess(effect.assertion.credentialId)
                else -> {}
            }
        }
    }

    AuthenticationPromptContent(
        state      = state,
        onConfirm  = { viewModel.handleIntent(AuthenticationIntent.ConfirmAuthentication) },
        onCancel   = { viewModel.handleIntent(AuthenticationIntent.CancelAuthentication) },
        onBiometric = { viewModel.handleIntent(AuthenticationIntent.UseBiometric) },
        onPinSubmit = { viewModel.handleIntent(AuthenticationIntent.UsePinVerification(it)) },
        onSelectCredential = { viewModel.handleIntent(AuthenticationIntent.SelectCredential(it)) },
        onRetry    = { viewModel.handleIntent(AuthenticationIntent.Retry) }
    )
}

@Composable
internal fun AuthenticationPromptContent(
    state: AuthenticationState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onBiometric: () -> Unit,
    onPinSubmit: (String) -> Unit,
    onSelectCredential: (String) -> Unit,
    onRetry: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        AnimatedContent(
            targetState = state,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "auth-state"
        ) { currentState ->
            when (currentState) {

                is AuthenticationState.Idle -> Box(
                    Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                is AuthenticationState.AwaitingUserConsent -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(96.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Key, contentDescription = "Passkey", modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        Text("Sign in with Passkey", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(currentState.rpId, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                if (currentState.credentialCount > 1) {
                                    Text("${currentState.credentialCount} passkeys available", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Confirm authentication button" }) {
                            Text("Sign in")
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Cancel authentication button" }) {
                            Text("Cancel")
                        }
                    }
                }

                is AuthenticationState.SelectingCredential -> {
                    CredentialSelectionDialog(
                        credentials = currentState.credentials,
                        onSelect    = onSelectCredential,
                        onDismiss   = onCancel
                    )
                }

                is AuthenticationState.AwaitingBiometric,
                is AuthenticationState.AwaitingPin -> {
                    Box(
                        modifier = Modifier.fillMaxSize().semantics { contentDescription = "Awaiting verification" },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Icon(Icons.Filled.Lock, contentDescription = "Verifying", modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("Verifying identity…", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }

                is AuthenticationState.Processing -> Box(
                    modifier = Modifier.fillMaxSize().semantics { contentDescription = "Authentication in progress" },
                    contentAlignment = Alignment.Center
                ) { AuthenticationProgressIndicator() }

                is AuthenticationState.Success -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(32.dp)) {
                        Text("Signed in!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("Authentication successful.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                is AuthenticationState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(32.dp)) {
                        Text("Authentication failed", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
                        Text(currentState.message, textAlign = TextAlign.Center)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = onCancel) { Text("Cancel") }
                            if (currentState.isRetryable) Button(onClick = onRetry) { Text("Try again") }
                        }
                    }
                }

                is AuthenticationState.Cancelled -> { /* navigate away */ }
            }
        }
    }
}

// ── T098 — Authentication Progress Indicator ──────────────────────────────────

@Composable
fun AuthenticationProgressIndicator(message: String = "Signing in…") {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.padding(32.dp).semantics { contentDescription = "Authentication in progress" }
    ) {
        CircularProgressIndicator(modifier = Modifier.size(64.dp), strokeWidth = 5.dp)
        Text(message, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.6f))
    }
}
