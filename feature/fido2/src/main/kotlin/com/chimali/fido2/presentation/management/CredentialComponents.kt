package com.chimali.fido2.presentation.management

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chimali.fido2.domain.model.PasskeyCredential

/** T122 — List Item */
@Composable
fun CredentialItem(
    credential: PasskeyCredential,
    onClick: (PasskeyCredential) -> Unit,
    onDeleteClick: (PasskeyCredential) -> Unit
) {
    ListItem(
        headlineContent = { Text(credential.userName) },
        supportingContent = { Text(credential.rpId) },
        leadingContent = {
            Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(40.dp))
        },
        trailingContent = {
            IconButton(onClick = { onDeleteClick(credential) }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        },
        modifier = Modifier.clickable { onClick(credential) }
    )
}

/** T123 — Confirmation Dialog */
@Composable
fun DeleteConfirmationDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDestructive: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { if (isDestructive) Icon(Icons.Default.Warning, contentDescription = null) },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/** T124 — Details View (BottomSheet or Dialog in real world, using Dialog for simplicity) */
@Composable
fun CredentialDetailsScreen(
    credential: PasskeyCredential,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Info, contentDescription = null) },
        title = { Text("Passkey Details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("User: ${credential.userName}")
                Text("Display Name: ${credential.userDisplayName}")
                Text("Relying Party: ${credential.rpId}")
                Text("Created: ${credential.createdAt}")
                Text("Last Used: ${credential.lastUsedAt ?: "Never"}")
                Text("Sign Count: ${credential.signCount}")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDelete,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete")
            }
        }
    )
}
