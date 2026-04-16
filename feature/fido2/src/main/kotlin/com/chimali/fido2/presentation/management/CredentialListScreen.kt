package com.chimali.fido2.presentation.management

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chimali.fido2.domain.model.PasskeyCredential

import androidx.hilt.navigation.compose.hiltViewModel

/**
 * T121 — List Screen to show all FIDO2 Passkeys.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CredentialListScreen(
    onNavigateUp: () -> Unit,
    viewModel: CredentialManagementViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Passkeys", 
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    ) 
                },
                actions = {
                    IconButton(onClick = { viewModel.onIntent(CredentialManagementIntent.ShowDeleteAllDialog) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete All")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (state.isLoading && state.credentials.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (state.credentials.isEmpty()) {
                Text(
                    text = "No passkeys found.",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.credentials, key = { it.id }) { credential ->
                        CredentialItem(
                            credential = credential,
                            onClick = { viewModel.onIntent(CredentialManagementIntent.SelectCredential(it)) },
                            onDeleteClick = { viewModel.onIntent(CredentialManagementIntent.ShowDeleteDialog(it)) }
                        )
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog for single credential
    state.credentialToDelete?.let { credential ->
        DeleteConfirmationDialog(
            title = "Delete Passkey?",
            message = "Are you sure you want to delete the passkey for ${credential.userName}? This cannot be undone.",
            onConfirm = { viewModel.onIntent(CredentialManagementIntent.ConfirmDelete(credential.id)) },
            onDismiss = { viewModel.onIntent(CredentialManagementIntent.DismissDialog) }
        )
    }

    // Delete All Confirmation
    if (state.showDeleteAllWarning) {
        DeleteConfirmationDialog(
            title = "Delete All Passkeys?",
            message = "This will permanently delete all passkeys stored on this device. You may lose access to your accounts.",
            onConfirm = { viewModel.onIntent(CredentialManagementIntent.ConfirmDeleteAll) },
            onDismiss = { viewModel.onIntent(CredentialManagementIntent.DismissDialog) },
            isDestructive = true
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
            onUpdateLabel = { label -> 
                viewModel.onIntent(CredentialManagementIntent.UpdateLabel(credential.id, label))
            }
        )
    }
}
