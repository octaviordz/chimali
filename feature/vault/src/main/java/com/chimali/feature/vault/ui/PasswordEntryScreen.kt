package com.chimali.feature.vault.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chimali.feature.vault.internal.payload.CustomField
import com.chimali.feature.vault.internal.payload.PasswordPayload

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordEntryScreen(
    onSave: (PasswordPayload) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    initialPayload: PasswordPayload? = null,
) {
    var title by remember { mutableStateOf(initialPayload?.title ?: "") }
    var username by remember { mutableStateOf(initialPayload?.let { String(it.username) } ?: "") }
    var password by remember { mutableStateOf(initialPayload?.let { String(it.password) } ?: "") }
    var uri by remember { mutableStateOf(initialPayload?.uri ?: "") }
    var notes by remember { mutableStateOf(initialPayload?.notes?.let { String(it) } ?: "") }

    val customFields =
        remember {
            mutableStateListOf<CustomField>().apply {
                initialPayload?.customFields?.let { addAll(it) }
            }
        }

    var showDiscardConfirmDialog by remember { mutableStateOf(false) }

    val hasUnsavedChanges = title.isNotBlank() || username.isNotBlank() || password.isNotBlank()

    fun attemptCancel() {
        if (hasUnsavedChanges) {
            showDiscardConfirmDialog = true
        } else {
            onCancel()
        }
    }

    BackHandler {
        attemptCancel()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(if (initialPayload != null) "Edit Password" else "New Password") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            item {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            item {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            item {
                OutlinedTextField(
                    value = uri,
                    onValueChange = { uri = it },
                    label = { Text("Website URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            item {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
            }

            // Dynamic Custom Fields
            itemsIndexed(customFields) { index, field ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = field.name,
                        onValueChange = { newName ->
                            customFields[index] = field.copy(name = newName)
                        },
                        label = { Text("Field Name") },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = String(field.value),
                        onValueChange = { newValue ->
                            customFields[index] = field.copy(value = newValue.toCharArray())
                        },
                        label = { Text("Value") },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                TextButton(onClick = {
                    customFields.add(CustomField("New Field", charArrayOf(), false))
                }) {
                    Text("Add Custom Field")
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = { attemptCancel() }) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val payload =
                                PasswordPayload(
                                    title = title,
                                    username = username.toCharArray(),
                                    password = password.toCharArray(),
                                    uri = uri,
                                    notes = if (notes.isNotBlank()) notes.toCharArray() else null,
                                    customFields = customFields.toList(),
                                )
                            onSave(payload)
                        },
                        enabled = title.isNotBlank() && password.isNotBlank(),
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }

    if (showDiscardConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirmDialog = false },
            title = { Text("Discard Changes?") },
            text = { Text("You have unsaved changes. Are you sure you want to discard them?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardConfirmDialog = false
                        onCancel()
                    },
                ) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirmDialog = false }) {
                    Text("Keep Editing")
                }
            },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PasswordEntryScreenPreview() {
    PasswordEntryScreen(
        onSave = {},
        onCancel = {},
    )
}
