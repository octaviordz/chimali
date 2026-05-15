package com.chimali.feature.vault.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chimali.feature.vault.api.VaultItem
import com.chimali.feature.vault.api.VaultType
import com.chimali.feature.vault.ui.model.LabelUiModel
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
    onManageLabelsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Credentials Vault", modifier = Modifier.semantics { heading() }) },
                actions = {
                    IconButton(onClick = onManageLabelsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Manage Labels")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(Icons.Default.Add, contentDescription = "Add Item")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Label Filter Bar
            ScrollableTabRow(
                selectedTabIndex =
                    if (selectedLabelId == null) {
                        0
                    } else {
                        labels.indexOfFirst { it.id == selectedLabelId } + 1
                    },
                edgePadding = 16.dp,
                divider = {},
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Tab(
                    selected = selectedLabelId == null,
                    onClick = { onLabelFilterClick(null) },
                    text = { Text("All") },
                )
                labels.forEach { label ->
                    Tab(
                        selected = selectedLabelId == label.id,
                        onClick = { onLabelFilterClick(label.id) },
                        text = { Text(label.name) },
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
fun VaultItemRow(
    item: VaultItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp)
                .semantics(mergeDescendants = true) { },
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(text = item.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = item.type.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun VaultListScreenPreview() {
    val date = "2023-01-01"
    val payload = "payload".toByteArray()
    val crdt = "crdt".toByteArray()

    val sampleItems =
        listOf(
            VaultItem(
                id = UUID.randomUUID(),
                type = VaultType.PASSWORD,
                title = "Sample Login",
                payload = payload,
                crdtState = crdt,
                dateCreated = date,
                dateModified = date,
                lastBackedUpAt = null,
                identityId = UUID.randomUUID(),
            ),
            VaultItem(
                id = UUID.randomUUID(),
                type = VaultType.NOTE,
                title = "Sample Note",
                payload = payload,
                crdtState = crdt,
                dateCreated = date,
                dateModified = date,
                lastBackedUpAt = null,
                identityId = UUID.randomUUID(),
            ),
        )
    val sampleLabels =
        listOf(
            LabelUiModel(UUID.randomUUID(), "Work", "#FFC107"),
            LabelUiModel(UUID.randomUUID(), "Personal", "#4CAF50"),
        )

    VaultListScreen(
        items = sampleItems,
        labels = sampleLabels,
        selectedLabelId = null,
        onItemClick = {},
        onAddClick = {},
        onLabelFilterClick = {},
        onManageLabelsClick = {},
    )
}
