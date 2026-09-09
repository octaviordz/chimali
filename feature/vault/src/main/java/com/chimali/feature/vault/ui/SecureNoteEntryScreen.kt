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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
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
import com.chimali.feature.vault.ui.model.LabelUiModel
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecureNoteEntryScreen(
    onSave: (SecureNotePayload) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    initialPayload: SecureNotePayload? = null,
    isSaving: Boolean = false,
    saveSucceeded: Boolean = false,
    errorMessage: String? = null,
    labels: List<LabelUiModel> = emptyList(),
    initialLabelIds: Set<UUID> = emptySet(),
    onSaveWithLabels: ((SecureNotePayload, List<UUID>) -> Unit)? = null,
) {
    DisposableEffect(initialPayload) {
        onDispose { initialPayload?.clearMemory() }
    }

    val editable = !isSaving && !saveSucceeded
    val draft = remember(initialPayload) { SecureNoteDraft(initialPayload) }
    val title = draft.title
    val content = draft.content
    DisposableEffect(draft) {
        onDispose { draft.clear() }
    }

    val customFields =
        remember {
            mutableStateListOf<CustomField>().apply {
                addAll(copyDraftFields(initialPayload?.customFields))
            }
        }

    DisposableEffect(customFields) {
        onDispose { customFields.forEach { it.clearMemory() } }
    }

    SideEffect {
        if (saveSucceeded) {
            draft.clear()
            customFields.forEach { it.clearMemory() }
            customFields.clear()
            initialPayload?.clearMemory()
        }
    }

    var showDiscardConfirmDialog by remember { mutableStateOf(false) }
    var selectedLabelIds by remember { mutableStateOf(initialLabelIds) }

    val hasUnsavedChanges =
        draft.hasTextChanges(initialPayload) || customFields != initialPayload?.customFields.orEmpty()

    fun attemptCancel() {
        if (draft.isClosed) return
        if (hasUnsavedChanges || selectedLabelIds != initialLabelIds) {
            showDiscardConfirmDialog = true
        } else {
            draft.clear()
            customFields.forEach { it.clearMemory() }
            initialPayload?.clearMemory()
            onCancel()
        }
    }

    BackHandler {
        attemptCancel()
    }

    VaultInputPolicy {
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
                    EntrySaveError(errorMessage)
                    LabelSelection(labels, selectedLabelIds, { id ->
                        selectedLabelIds = if (id in selectedLabelIds) selectedLabelIds - id else selectedLabelIds + id
                    }, enabled = editable)
                }

                item {
                    OutlinedTextField(
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                        enabled = editable,
                        value = title.displayText(),
                        onValueChange = { title.replace(it) },
                        label = { Text("Title *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }

                item {
                    OutlinedTextField(
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                        enabled = editable,
                        value = content.displayText(),
                        onValueChange = { content.replace(it) },
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
                            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                            enabled = editable,
                            value = String(field.name),
                            onValueChange = { newName ->
                                if (!draft.isClosed && index in customFields.indices) {
                                    val current = customFields[index]
                                    current.name.fill('\u0000')
                                    customFields[index] = current.copy(name = newName.toCharArray())
                                }
                            },
                            label = { Text("Field Name") },
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                            enabled = editable,
                            value = String(field.value),
                            onValueChange = { newValue ->
                                if (!draft.isClosed && index in customFields.indices) {
                                    val current = customFields[index]
                                    current.value.fill('\u0000')
                                    customFields[index] = current.copy(value = newValue.toCharArray())
                                }
                            },
                            label = { Text("Value") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                item {
                    TextButton(enabled = editable, onClick = {
                        if (!draft.isClosed) customFields.add(CustomField("New Field", charArrayOf(), false))
                    }) {
                        Text("Add Custom Field")
                    }
                }

                item {
                    if (title.isBlank() || content.isBlank()) {
                        Text(
                            text = "Title and content are required.",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TextButton(onClick = { attemptCancel() }) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = save@{
                                if (draft.isClosed) return@save
                                val payload =
                                    SecureNotePayload(
                                        title = title.copyChars(),
                                        content = content.copyChars(),
                                        customFields = customFields.map { it.copyForEditing() },
                                    )
                                submitOwned(payload) { submission ->
                                    onSaveWithLabels?.invoke(submission, selectedLabelIds.toList())
                                        ?: onSave(submission)
                                }
                            },
                            enabled = title.isNotBlank() && content.isNotBlank() && !isSaving,
                        ) {
                            Text("Save")
                        }
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
                    onClick = discard@{
                        if (draft.isClosed) return@discard
                        showDiscardConfirmDialog = false
                        draft.clear()
                        customFields.forEach { it.clearMemory() }
                        initialPayload?.clearMemory()
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
