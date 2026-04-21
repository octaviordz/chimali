package com.chimali.fido2.presentation.management

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.presentation.ui.components.ChimaliButton
import com.chimali.fido2.presentation.ui.components.ChimaliOutlinedButton

/** T122 — List Item */
@Composable
fun CredentialItem(
    credential: PasskeyCredential,
    onClick: (PasskeyCredential) -> Unit,
    onDeleteClick: (PasskeyCredential) -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                text = credential.rpId,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium
            )
        },
        supportingContent = {
            Column {
                Text(
                    text = credential.userName,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Last used: ${credential.lastUsedAt.toString().substring(0, 10)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        leadingContent = {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = credential.rpId.take(1).uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        },
        trailingContent = {
            IconButton(onClick = { onDeleteClick(credential) }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        },
        modifier = Modifier.clickable { onClick(credential) },
    )
}


/** T123 — Confirmation Dialog */
@Composable
fun DeleteConfirmationDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDestructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        icon = { if (isDestructive) Icon(Icons.Default.Warning, contentDescription = null) },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            ChimaliButton(
                onClick = onConfirm,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            ChimaliOutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

/** T124 — Details View (BottomSheet or Dialog in real world, using Dialog for simplicity) */
@Composable
fun CredentialDetailsScreen(
    credential: PasskeyCredential,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onUpdateLabel: (String?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        icon = { Icon(Icons.Default.Info, contentDescription = null) },
        title = { Text("Passkey Details") },
        text = {
            var labelText by remember { mutableStateOf(credential.label ?: "") }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = labelText,
                    onValueChange = { labelText = it },
                    label = { Text("Custom Label / Note") },
                    placeholder = { Text("e.g. Work Account") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    leadingIcon = { Icon(Icons.Default.Label, contentDescription = null) },
                    trailingIcon = {
                        if (labelText != (credential.label ?: "")) {
                            IconButton(onClick = { onUpdateLabel(labelText.ifBlank { null }) }) {
                                Icon(Icons.Default.Check, contentDescription = "Save label", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    },
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "ID: ${credential.id}",
                        fontFamily = com.chimali.core.ui.theme.LegibilityType.AtkinsonFontFamily,
                    )
                    Text("User: ${credential.userName}")
                    Text("Display Name: ${credential.userDisplayName}")
                    Text(
                        text = "Relying Party: ${credential.rpId}",
                        fontFamily = com.chimali.core.ui.theme.LegibilityType.AtkinsonFontFamily,
                    )
                    Text("Created: ${credential.createdAt}")
                    Text("Last Used: ${credential.lastUsedAt ?: "Never"}")
                    Text("Sign Count: ${credential.signCount}")
                }
            }
        },
        confirmButton = {
            ChimaliButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        dismissButton = {
            ChimaliOutlinedButton(
                onClick = onDelete,
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
            ) {
                Text("Delete")
            }
        },
    )
}
