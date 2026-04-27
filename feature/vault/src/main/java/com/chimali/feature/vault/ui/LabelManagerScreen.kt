package com.chimali.feature.vault.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.chimali.feature.vault.ui.model.LabelUiModel
import java.util.UUID

// Composable for managing labels

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("ModifierMissing", "FunctionNaming", "ForbiddenComment") // TODO: Add modifier parameter in follow-up refactor
@Composable
fun LabelManagerScreen(
    labels: List<LabelUiModel>,
    onCreateLabel: (name: String, colorHex: String) -> Unit,
    onDeleteLabel: (UUID) -> Unit,
    onBack: () -> Unit
) {
    var newLabelName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Labels") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
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
            // Create New Label Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newLabelName,
                    onValueChange = { newLabelName = it },
                    label = { Text("New Label Name") },
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        if (newLabelName.isNotBlank()) {
                            onCreateLabel(newLabelName, "#FF0000") // Placeholder exact color
                            newLabelName = ""
                        }
                    },
                    enabled = newLabelName.isNotBlank()
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Label")
                }
            }

            HorizontalDivider()

            // List of Existing Labels
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(labels) { label ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text(label.name)
                        IconButton(onClick = { onDeleteLabel(label.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Label")
                        }
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
fun LabelManagerScreenPreview() {
    val sampleLabels = remember {
        mutableStateListOf(
            LabelUiModel(UUID.randomUUID(), "Work", "#FFC107"),
            LabelUiModel(UUID.randomUUID(), "Personal", "#4CAF50"),
            LabelUiModel(UUID.randomUUID(), "Social", "#2196F3")
        )
    }

    LabelManagerScreen(
        labels = sampleLabels,
        onCreateLabel = { name, color ->
            sampleLabels.add(LabelUiModel(UUID.randomUUID(), name, color))
        },
        onDeleteLabel = { id ->
            sampleLabels.removeIf { it.id == id }
        },
        onBack = {}
    )
}

