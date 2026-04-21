package com.chimali.fido2.presentation.management

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.presentation.ui.BiometricPromptComponent
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/**
 * T121 — List Screen to show all FIDO2 Passkeys.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CredentialListScreen(
    onNavigateUp: () -> Unit,
    viewModel: CredentialManagementViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var showBiometricPrompt by remember { mutableStateOf<PasskeyCredential?>(null) }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is CredentialManagementEffect.ShowToast -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
                is CredentialManagementEffect.ShowUndoSnackbar -> {
                    val result = snackbarHostState.showSnackbar(
                        message = effect.message,
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.onIntent(CredentialManagementIntent.UndoDelete)
                    }
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            "Passkeys",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateUp) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
                SearchBar(
                    query = searchQuery,
                    onQueryChange = {
                        searchQuery = it
                        viewModel.onIntent(CredentialManagementIntent.UpdateSearchQuery(it))
                    },
                    onSearch = { },
                    active = false,
                    onActiveChange = { },
                    placeholder = { Text("Search passkeys") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                searchQuery = ""
                                viewModel.onIntent(CredentialManagementIntent.UpdateSearchQuery(""))
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) { }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (state.isLoading && state.credentials.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (state.credentials.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (searchQuery.isEmpty()) "No passkeys yet." else "No results for \"$searchQuery\"",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(state.credentials, key = { it.id }) { credential ->
                        CredentialItem(
                            credential = credential,
                            onClick = { viewModel.onIntent(CredentialManagementIntent.SelectCredential(it)) },
                            onDeleteClick = { viewModel.onIntent(CredentialManagementIntent.ShowDeleteDialog(it)) },
                        )
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    state.credentialToDelete?.let { credential ->
        DeleteConfirmationDialog(
            title = "Delete Passkey?",
            message = "This will permanently remove the passkey for ${credential.userName} from this device.",
            onConfirm = {
                viewModel.onIntent(CredentialManagementIntent.DismissDialog)
                showBiometricPrompt = credential
            },
            onDismiss = { viewModel.onIntent(CredentialManagementIntent.DismissDialog) },
        )
    }

    // Biometric Auth for Deletion
    showBiometricPrompt?.let { credential ->
        BiometricPromptComponent(
            title = "Confirm Deletion",
            subtitle = "Verify your identity to delete the passkey for ${credential.rpId}",
            onSuccess = {
                showBiometricPrompt = null
                viewModel.onIntent(CredentialManagementIntent.ConfirmDelete(credential.id))
            },
            onError = { _, _ ->
                showBiometricPrompt = null
                scope.launch { snackbarHostState.showSnackbar("Authentication failed") }
            },
            onFallback = {
                showBiometricPrompt = null
            }
        )
    }

    // Details Modal
    state.selectedCredential?.let { credential ->
        CredentialDetailsScreen(
            credential = credential,
            onDismiss = { viewModel.onIntent(CredentialManagementIntent.DismissDialog) },
            onDelete = {
                viewModel.onIntent(CredentialManagementIntent.DismissDialog)
                viewModel.onIntent(CredentialManagementIntent.ShowDeleteDialog(credential))
            },
            onUpdateLabel = { /* Not in scope for FR */ }
        )
    }
}

