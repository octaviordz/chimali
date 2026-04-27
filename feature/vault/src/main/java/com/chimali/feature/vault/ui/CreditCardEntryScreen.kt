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
import com.chimali.feature.vault.internal.payload.CreditCardPayload
import com.chimali.feature.vault.internal.payload.CustomField

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("ModifierMissing", "FunctionNaming", "ForbiddenComment") // TODO: Add modifier parameter in follow-up refactor
@Composable
fun CreditCardEntryScreen(
    onSave: (CreditCardPayload) -> Unit,
    onCancel: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var cardholderName by remember { mutableStateOf("") }
    var cardNumber by remember { mutableStateOf("") }
    var expirationDate by remember { mutableStateOf("") }
    var cvv by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    // Dynamic Custom Fields State
    val customFields = remember { mutableStateListOf<CustomField>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Credit Card") }
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
                    label = { Text("Title (e.g., Amex Gold)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = cardholderName,
                    onValueChange = { cardholderName = it },
                    label = { Text("Name on Card") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = cardNumber,
                    onValueChange = { cardNumber = it },
                    label = { Text("Card Number") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = expirationDate,
                        onValueChange = { expirationDate = it },
                        label = { Text("Exp (MM/YY)") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = cvv,
                        onValueChange = { cvv = it },
                        label = { Text("CVV") },
                        modifier = Modifier.weight(1f)
                    )
                }
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
                }
            }

            item {
                Button(onClick = {
                    customFields.add(CustomField("Bank Phone", charArrayOf(), false))
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
                            val payload = CreditCardPayload(
                                title = title,
                                cardholderName = cardholderName.toCharArray(),
                                cardNumber = cardNumber.toCharArray(),
                                expirationDate = expirationDate,
                                cvv = cvv.toCharArray(),
                                notes = if (notes.isNotBlank()) notes.toCharArray() else null,
                                customFields = customFields.toList()
                            )
                            onSave(payload)
                        },
                        enabled = title.isNotBlank() && cardNumber.isNotBlank()
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Suppress(
    // TODO: Make internal once preview isolation is addressed
    "PreviewPublic",
    "FunctionNaming",
    "ForbiddenComment",
)
@Preview(showBackground = true)
@Composable
fun CreditCardEntryScreenPreview() {
    CreditCardEntryScreen(
        onSave = {},
        onCancel = {}
    )
}
