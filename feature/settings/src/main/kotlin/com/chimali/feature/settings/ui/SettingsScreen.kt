package com.chimali.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chimali.core.ui.theme.LegibilityType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vaultEnabled: Boolean,
    passkeyEnabled: Boolean,
    onRetakeOnboarding: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text("Settings", modifier = Modifier.semantics { heading() }) })
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "Current selection",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = LegibilityType.AtkinsonFontFamily,
                    )
                    Text(text = "Vault: ${if (vaultEnabled) "Enabled" else "Disabled"}")
                    Text(text = "Passkey Authenticator: ${if (passkeyEnabled) "Enabled" else "Disabled"}")
                }
            }

            Text(
                text = "You can restart the onboarding flow to change which features Chimali uses by default.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = onBack) {
                    Text("Back")
                }
                Button(onClick = onRetakeOnboarding) {
                    Text("Re-take Onboarding")
                }
            }
        }
    }
}
