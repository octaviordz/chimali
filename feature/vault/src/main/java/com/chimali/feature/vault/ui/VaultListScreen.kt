package com.chimali.feature.vault.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chimali.feature.vault.api.VaultItem
import com.chimali.feature.vault.api.VaultType
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultListScreen(
    items: List<VaultItem>,
    labels: List<LabelUiModel>,
    selectedLabelId: UUID?,
    onItemClick: (VaultItem) -> Unit,
    onAddClick: () -> Unit,
    onLabelFilterClick: (UUID?) -> Unit,
    onManageLabelsClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Credentials Vault", modifier = Modifier.semantics { heading() }) },
                actions = {
                    IconButton(onClick = onManageLabelsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Manage Labels")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(Icons.Default.Add, contentDescription = "Add Item")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Label Filter Bar
            ScrollableTabRow(
                selectedTabIndex = if (selectedLabelId == null) 0 else labels.indexOfFirst { it.id == selectedLabelId } + 1,
                edgePadding = 16.dp,
                divider = {},
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedLabelId == null,
                    onClick = { onLabelFilterClick(null) },
                    text = { Text("All") }
                )
                labels.forEach { label ->
                    Tab(
                        selected = selectedLabelId == label.id,
                        onClick = { onLabelFilterClick(label.id) },
                        text = { Text(label.name) }
                    )
                }
            }

            if (items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text("No items found.")
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items) { item ->
                        VaultItemRow(item = item, onClick = { onItemClick(item) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
fun VaultItemRow(item: VaultItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp)
            .semantics(mergeDescendants = true) { },
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(text = item.title, style = MaterialTheme.typography.bodyLarge)
            Text(text = item.type.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun VaultListScreenPreview() {
    val sampleItems = listOf(
        VaultItem(
            id = UUID.randomUUID(),
            type = VaultType.PASSWORD,
            title = "Sample Login",
            payload = "payload".toByteArray(),
            crdtState = "crdt".toByteArray(),
            dateCreated = "2023-01-01",
            dateModified = "2023-01-01",
            lastBackedUpAt = null,
            identityId = UUID.randomUUID()
        ),
        VaultItem(
            id = UUID.randomUUID(),
            type = VaultType.NOTE,
            title = "Sample Note",
            payload = "payload".toByteArray(),
            crdtState = "crdt".toByteArray(),
            dateCreated = "2023-01-01",
            dateModified = "2023-01-01",
            lastBackedUpAt = null,
            identityId = UUID.randomUUID()
        )
    )
    val sampleLabels = listOf(
        LabelUiModel(UUID.randomUUID(), "Work", "#FFC107"),
        LabelUiModel(UUID.randomUUID(), "Personal", "#4CAF50")
    )

    VaultListScreen(
        items = sampleItems,
        labels = sampleLabels,
        selectedLabelId = null,
        onItemClick = {},
        onAddClick = {},
        onLabelFilterClick = {},
        onManageLabelsClick = {}
    )
}
