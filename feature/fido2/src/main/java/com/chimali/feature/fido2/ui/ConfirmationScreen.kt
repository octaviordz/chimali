package com.chimali.feature.fido2.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chimali.feature.fido2.api.PendingAuthRequest

// T023: High-legibility font for security-critical prompts (Constitution Principle VI)
// Falls back to default sans-serif if the resource is not yet bundled
private val SecurityFontFamily = FontFamily.Default // TODO: Replace with FontFamily(Font(R.font.atkinson_hyperlegible))

@Composable
fun ConfirmationScreen(
    request: PendingAuthRequest,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Authentication Request",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Account: ${request.userName}",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "Site: ${request.relyingPartyId}",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(32.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                OutlinedButton(onClick = onCancel) {
                    Text("Cancel")
                }
                Button(onClick = onConfirm) {
                    Text("Approve")
                }
            }
        }
    }
}
