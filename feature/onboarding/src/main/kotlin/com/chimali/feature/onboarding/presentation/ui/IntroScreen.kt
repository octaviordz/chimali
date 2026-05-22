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

@Composable
fun IntroScreen(
    onContinue: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Welcome to Chimali",
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = LegibilityType.AtkinsonFontFamily,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = "Choose the features you want Chimali to unlock for you.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 12.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            OutlinedButton(onClick = onCancel) {
                Text("Cancel")
            }
            androidx.compose.foundation.layout
                .Spacer(Modifier.width(12.dp))
            Button(onClick = onContinue) {
                Text("Continue")
            }
        }
    }
}
