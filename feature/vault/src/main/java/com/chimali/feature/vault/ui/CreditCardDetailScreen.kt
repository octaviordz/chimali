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
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chimali.feature.vault.internal.payload.CreditCardPayload
import com.chimali.feature.vault.ui.components.LegibleSecretText
import com.chimali.feature.vault.ui.model.LegibilityFont
import com.chimali.feature.vault.ui.model.LegibilitySettings

private const val CARD_NUMBER_CHUNK_SIZE = 4
private const val LAST_DIGITS_COUNT = 4
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
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isNumberRevealed by remember { mutableStateOf(false) }
    var isCvvRevealed by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val legibilitySettings =
        LegibilitySettings(
            fontType = LegibilityFont.Atkinson,
            useSemanticHighlighting = true,
            highlightNumbers = true,
            colorblindMode = false,
        )

    DisposableEffect(payload) {
        onDispose {
            payload.clearMemory()
        }
    }

    BackHandler {
        onBack()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(String(payload.title)) },
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
                    .padding(SCREEN_PADDING),
            verticalArrangement = Arrangement.spacedBy(ROW_SPACING),
        ) {
            DetailRow(label = "Name on Card", value = String(payload.cardholderName))

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "Card Number", style = MaterialTheme.typography.labelMedium)
                    IconButton(onClick = { isNumberRevealed = !isNumberRevealed }) {
                        val icon = if (isNumberRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility
                        Icon(
                            imageVector = icon,
                            contentDescription = if (isNumberRevealed) "Hide card number" else "Show card number",
                        )
                    }
                }
                if (isNumberRevealed) {
                    LegibleSecretText(
                        secret = payload.cardNumber,
                        groupSize = CARD_NUMBER_CHUNK_SIZE,
                        isRevealed = true,
                        settings = legibilitySettings,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        text =
                            "•••• •••• •••• " +
                                String(
                                    payload.cardNumber,
                                    maxOf(0, payload.cardNumber.size - LAST_DIGITS_COUNT),
                                    minOf(payload.cardNumber.size, LAST_DIGITS_COUNT),
                                ),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(HORIZONTAL_SPACING),
            ) {
                DetailRow(
                    label = "Expires",
                    value = String(payload.expirationDate),
                    modifier = Modifier.weight(1f),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = "CVV", style = MaterialTheme.typography.labelMedium)
                        IconButton(onClick = { isCvvRevealed = !isCvvRevealed }) {
                            val cvvIcon = if (isCvvRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility
                            Icon(
                                imageVector = cvvIcon,
                                contentDescription = if (isCvvRevealed) "Hide CVV" else "Show CVV",
                            )
                        }
                    }
                    Text(
                        text = if (isCvvRevealed) String(payload.cvv) else "•••",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            if (payload.notes != null) {
                DetailRow(label = "Notes", value = String(payload.notes))
            }

            payload.customFields?.forEach { field ->
                DetailRow(
                    label = String(field.name),
                    value = if (field.isConcealed) "***" else String(field.value),
                )
            }

            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                contentPadding =
                    PaddingValues(
                        vertical = BUTTON_VERTICAL_PADDING,
                        horizontal = BUTTON_HORIZONTAL_PADDING,
                    ),
            ) {
                Text("Back to Vault")
            }
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Credit Card") },
            text = {
                Text(
                    "Are you sure you want to delete '${String(payload.title)}'? This action cannot be undone.",
                )
            },
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

@Preview(showBackground = true)
@Composable
private fun CreditCardDetailScreenPreview() {
    CreditCardDetailScreen(
        payload =
            CreditCardPayload(
                title = "Personal Visa",
                cardholderName = "John Doe".toCharArray(),
                cardNumber = "1234567890123456".toCharArray(),
                expirationDate = "12/26",
                cvv = "123".toCharArray(),
                notes = "Primary card".toCharArray(),
                customFields = emptyList(),
            ),
        onEdit = {},
        onDelete = {},
        onBack = {},
    )
}
