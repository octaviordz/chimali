package com.chimali.feature.vault.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chimali.feature.vault.internal.payload.CreditCardPayload

private const val CARD_NUMBER_CHUNK_SIZE = 4
private val SCREEN_PADDING = 16.dp
private val ROW_SPACING = 16.dp
private val HORIZONTAL_SPACING = 32.dp
private val BUTTON_VERTICAL_PADDING = 16.dp
private val BUTTON_HORIZONTAL_PADDING = 24.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditCardDetailScreen(
    payload: CreditCardPayload,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(payload.title) },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(SCREEN_PADDING),
            verticalArrangement = Arrangement.spacedBy(ROW_SPACING)
        ) {
            DetailRow(label = "Name on Card", value = String(payload.cardholderName))
            DetailRow(
                label = "Card Number",
                value = String(payload.cardNumber).chunked(CARD_NUMBER_CHUNK_SIZE).joinToString(" ")
            )
            Row(horizontalArrangement = Arrangement.spacedBy(HORIZONTAL_SPACING)) {
                DetailRow(label = "Expires", value = payload.expirationDate)
                DetailRow(label = "CVV", value = "***") // Placeholder for concealed CVV reveal
            }
            
            if (payload.notes != null) {
                DetailRow(label = "Notes", value = String(payload.notes))
            }

            payload.customFields?.forEach { field ->
                DetailRow(
                    label = field.name,
                    value = if (field.isConcealed) "***" else String(field.value)
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                contentPadding = PaddingValues(
                    vertical = BUTTON_VERTICAL_PADDING,
                    horizontal = BUTTON_HORIZONTAL_PADDING
                )
            ) {
                Text("Back to Vault")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CreditCardDetailScreenPreview() {
    CreditCardDetailScreen(
        payload = CreditCardPayload(
            title = "Personal Visa",
            cardholderName = "John Doe".toCharArray(),
            cardNumber = "1234567890123456".toCharArray(),
            expirationDate = "12/26",
            cvv = "123".toCharArray(),
            notes = "Sample card notes".toCharArray(),
            customFields = emptyList()
        ),
        onEdit = {},
        onDelete = {},
        onBack = {}
    )
}
