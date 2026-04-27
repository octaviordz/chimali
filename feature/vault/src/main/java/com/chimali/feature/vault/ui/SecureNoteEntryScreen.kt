package com.chimali.feature.vault.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
@Suppress("ModifierMissing", "FunctionNaming", "ForbiddenComment") // TODO: Add modifier parameter in follow-up refactor
@Composable
fun SecureNoteEntryScreen(
    onSave: (SecureNotePayload) -> Unit,
    onCancel: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    // Dynamic Custom Fields State
    val customFields = remember { mutableStateListOf<CustomField>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Secure Note") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Content") },
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    maxLines = 10
                )
            }

            // Render Dynamic Custom Fields
            items(customFields.size) { index ->
                val field = customFields[index]
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("${field.name}: *****")
                }
            }

            item {
                Button(onClick = {
                    customFields.add(CustomField("Secret Code", charArrayOf(), true))
                }) {
                    Text("Add Custom Field")
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onCancel) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val payload = SecureNotePayload(
                                title = title,
                                content = content.toCharArray(),
                                customFields = customFields.toList()
                            )
                            onSave(payload)
                        },
                        enabled = title.isNotBlank() && content.isNotBlank()
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Suppress(
    // TODO: Make internal once preview isolation is addressed
    "PreviewPublic",
    "FunctionNaming",
    "ForbiddenComment",
)
@Composable
fun SecureNoteEntryScreenPreview() {
    SecureNoteEntryScreen(
        onSave = {},
        onCancel = {}
    )
}
