package com.chimali.feature.onboarding.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chimali.core.ui.theme.LegibilityType
import com.chimali.feature.onboarding.presentation.model.OnboardingUiState

@Composable
fun FeatureSelectionScreen(
    state: OnboardingUiState,
    onVaultToggle: (Boolean) -> Unit,
    onPasskeyToggle: (Boolean) -> Unit,
    onBack: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Select your features",
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = LegibilityType.AtkinsonFontFamily,
            modifier = Modifier.semantics { heading() },
        )

        FeatureToggleRow(
            title = "Vault",
            description = "Store and manage passwords, cards, and notes.",
            checked = state.vaultSelected,
            onCheckedChange = onVaultToggle,
        )

        FeatureToggleRow(
            title = "Passkey Authenticator",
            description = "Use Chimali as a FIDO2 / WebAuthn authenticator.",
            checked = state.passkeySelected,
            onCheckedChange = onPasskeyToggle,
        )

        state.errorMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            OutlinedButton(onClick = onBack) {
                Text("Back")
            }
            androidx.compose.foundation.layout
                .Spacer(Modifier.width(12.dp))
            Button(
                onClick = onFinish,
                enabled = state.canContinue && !state.isSaving,
            ) {
                Text(if (state.isSaving) "Saving…" else "Finish")
            }
        }
    }
}

@Composable
private fun FeatureToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
        Column(modifier = Modifier.padding(top = 4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
