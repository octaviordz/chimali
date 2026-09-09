package com.chimali.feature.vault.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.chimali.feature.vault.ui.model.LabelUiModel
import java.util.UUID

@Composable
fun LabelSelection(
    labels: List<LabelUiModel>,
    selectedIds: Set<UUID>,
    onToggle: (UUID) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(modifier = modifier) {
        labels.forEach { label ->
            FilterChip(
                enabled = enabled,
                selected = label.id in selectedIds,
                onClick = { onToggle(label.id) },
                label = { Text(label.name) },
            )
        }
    }
}
