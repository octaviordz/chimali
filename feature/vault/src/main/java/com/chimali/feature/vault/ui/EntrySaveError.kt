package com.chimali.feature.vault.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun EntrySaveError(
    message: String?,
    modifier: Modifier = Modifier,
) {
    message?.let { Text(it, modifier = modifier, color = MaterialTheme.colorScheme.error) }
}
