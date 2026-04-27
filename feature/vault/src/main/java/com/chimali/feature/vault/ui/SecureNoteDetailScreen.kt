package com.chimali.feature.vault.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chimali.feature.vault.internal.payload.SecureNotePayload

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("FunctionNaming")
@Composable
fun SecureNoteDetailScreen(
    payload: SecureNotePayload,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DetailRow(label = "Content", value = "***") // Placeholder for concealed content reveal

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
                contentPadding = PaddingValues(vertical = 16.dp, horizontal = 24.dp)
            ) {
                Text("Back to Vault")
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
fun SecureNoteDetailScreenPreview() {
    SecureNoteDetailScreen(
        payload = SecureNotePayload(
            title = "Secret Recipe",
            content = "This is the secret recipe for the best cookies.".toCharArray(),
            customFields = emptyList()
        ),
        onEdit = {},
        onDelete = {},
        onBack = {}
    )
}
