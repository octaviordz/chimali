package com.chimali.fido2.presentation.management

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chimali.core.ui.R
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.presentation.ui.components.ChimaliButton
import com.chimali.fido2.presentation.ui.components.ChimaliOutlinedButton

/** T122 — List Item */
@Composable
fun CredentialItem(
    credential: PasskeyCredential,
    onClick: (PasskeyCredential) -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                text = credential.rpId,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = com.chimali.core.ui.theme.LegibilityType.AtkinsonFontFamily,
            )
        },
        supportingContent = {
            Column {
                Text(
                    text = credential.userName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = com.chimali.core.ui.theme.LegibilityType.AtkinsonFontFamily,
                )
                Text(
                    text = "Last used: ${credential.lastUsedAt.toString().substring(0, ISO_DATE_LENGTH)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        leadingContent = {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(id = R.drawable.passkey_24),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        },
        modifier = Modifier.clickable { onClick(credential) },
    )
}

private const val ISO_DATE_LENGTH = 10

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
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        icon = { Icon(Icons.Default.Info, contentDescription = null) },
        title = { Text("Passkey Details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Removed custom label field per FR

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Identifier: ${credential.id}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = com.chimali.core.ui.theme.LegibilityType.AtkinsonFontFamily,
                    )
                    Text(
                        text = "User Name: ${credential.userName}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "Display Name: ${credential.userDisplayName}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "Relying Party: ${credential.rpId}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = com.chimali.core.ui.theme.LegibilityType.AtkinsonFontFamily,
                    )
                    Text(
                        text = "Last Used: ${credential.lastUsedAt}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
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
