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
import com.chimali.feature.vault.internal.payload.SecureNotePayload

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecureNoteEntryScreen(
    onSave: (SecureNotePayload) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    initialPayload: SecureNotePayload? = null,
) {
    var title by remember { mutableStateOf(initialPayload?.title ?: "") }
    var content by remember { mutableStateOf(initialPayload?.let { String(it.content) } ?: "") }

    val customFields =
        remember {
            mutableStateListOf<CustomField>().apply {
                initialPayload?.customFields?.let { addAll(it) }
            }
        }

    var showDiscardConfirmDialog by remember { mutableStateOf(false) }

    val hasUnsavedChanges = title.isNotBlank() || content.isNotBlank()

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
                title = { Text(if (initialPayload != null) "Edit Secure Note" else "New Secure Note") },
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
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Content *") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 5,
                )
            }

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
                                SecureNotePayload(
                                    title = title,
                                    content = content.toCharArray(),
                                    customFields = customFields.toList(),
                                )
                            onSave(payload)
                        },
                        enabled = title.isNotBlank() && content.isNotBlank(),
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
private fun SecureNoteEntryScreenPreview() {
    SecureNoteEntryScreen(
        onSave = {},
        onCancel = {},
    )
}
