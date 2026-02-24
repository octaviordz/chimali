package com.chimali.feature.vault.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chimali.feature.vault.internal.payload.CustomField
import com.chimali.feature.vault.internal.payload.PasswordPayload

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordEntryScreen(
    onSave: (PasswordPayload) -> Unit,
    onCancel: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var uri by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    
    // Dynamic Custom Fields State
    val customFields = remember { mutableStateListOf<CustomField>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Password") }
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
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = uri,
                    onValueChange = { uri = it },
                    label = { Text("Website / URI") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // Render Dynamic Custom Fields
            items(customFields.size) { index ->
                val field = customFields[index]
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("${field.name}: *****")
                    // Render concealed value mask or text based on isConcealed
                }
            }
            
            item {
                Button(onClick = {
                    // This is placeholder logic to add a new custom field for demo purposes
                    customFields.add(CustomField("New Field", charArrayOf(), false))
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
                            val payload = PasswordPayload(
                                title = title,
                                username = username.toCharArray(),
                                password = password.toCharArray(),
                                uri = uri,
                                notes = if (notes.isNotBlank()) notes.toCharArray() else null,
                                customFields = customFields.toList()
                            )
                            onSave(payload)
                        },
                        enabled = title.isNotBlank() && password.isNotBlank()
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
