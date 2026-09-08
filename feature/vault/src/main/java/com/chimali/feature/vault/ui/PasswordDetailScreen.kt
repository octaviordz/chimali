package com.chimali.feature.vault.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chimali.feature.vault.internal.payload.PasswordPayload
import com.chimali.feature.vault.ui.components.LegibleSecretText
import com.chimali.feature.vault.ui.model.LegibilityFont
import com.chimali.feature.vault.ui.model.LegibilitySettings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordDetailScreen(
    payload: PasswordPayload,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onCopyPassword: ((String) -> Unit)? = null,
) {
    val legibilitySettings =
        LegibilitySettings(
            fontType = LegibilityFont.Atkinson,
            useSemanticHighlighting = true,
            highlightNumbers = true,
            colorblindMode = false,
        )

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Clear memory on dispose/back
    DisposableEffect(Unit) {
        onDispose {
            payload.clearMemory()
        }
    }

    BackHandler {
        onBack()
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(payload.title) },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = { showDeleteConfirmDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DetailRow(label = "Username", value = String(payload.username))

            PasswordRow(
                password = payload.password,
                legibilitySettings = legibilitySettings,
                onCopy = {
                    val pwd = String(payload.password)
                    if (onCopyPassword != null) {
                        onCopyPassword(pwd)
                    }
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Password copied. Clipboard clears in 60s.")
                    }
                },
            )

            DetailRow(label = "Website", value = payload.uri)

            if (payload.notes != null) {
                DetailRow(label = "Notes", value = String(payload.notes))
            }

            payload.customFields?.forEach { field ->
                if (field.isConcealed) {
                    ConcealedCustomFieldRow(
                        name = field.name,
                        value = field.value,
                        legibilitySettings = legibilitySettings,
                    )
                } else {
                    DetailRow(
                        label = field.name,
                        value = String(field.value),
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                contentPadding = PaddingValues(vertical = 16.dp, horizontal = 24.dp),
            ) {
                Text("Back to Vault")
            }
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Entry") },
            text = { Text("Are you sure you want to delete '${payload.title}'? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDelete()
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
fun DetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PasswordRow(
    password: CharArray,
    legibilitySettings: LegibilitySettings,
    onCopy: () -> Unit,
) {
    var isPasswordRevealed by remember { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Password",
                style = MaterialTheme.typography.labelMedium,
            )
            Row {
                IconButton(onClick = onCopy) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy password",
                    )
                }
                IconButton(
                    onClick = { isPasswordRevealed = !isPasswordRevealed },
                ) {
                    Icon(
                        imageVector = if (isPasswordRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (isPasswordRevealed) "Hide password" else "Show password",
                    )
                }
            }
        }

        if (isPasswordRevealed) {
            LegibleSecretText(
                secret = String(password),
                isRevealed = true,
                settings = legibilitySettings,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            BasicText(
                text = "•".repeat(password.size),
                style =
                    MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
            )
        }
    }
}

@Composable
private fun ConcealedCustomFieldRow(
    name: String,
    value: CharArray,
    legibilitySettings: LegibilitySettings,
) {
    var isFieldRevealed by remember { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
            )
            IconButton(
                onClick = { isFieldRevealed = !isFieldRevealed },
            ) {
                Icon(
                    imageVector = if (isFieldRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (isFieldRevealed) "Hide $name" else "Show $name",
                )
            }
        }

        if (isFieldRevealed) {
            LegibleSecretText(
                secret = String(value),
                isRevealed = true,
                settings = legibilitySettings,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            BasicText(
                text = "•".repeat(value.size),
                style =
                    MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PasswordDetailScreenPreview() {
    PasswordDetailScreen(
        payload =
            PasswordPayload(
                title = "Sample Login",
                username = "user@example.com".toCharArray(),
                password = "password".toCharArray(),
                uri = "https://example.com",
                notes = "This is a sample note.".toCharArray(),
                customFields = emptyList(),
            ),
        onEdit = {},
        onDelete = {},
        onBack = {},
    )
}
