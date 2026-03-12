package com.chimali.fido2.presentation.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chimali.fido2.domain.model.MakeCredentialOptions
import com.chimali.fido2.domain.model.PublicKeyCredentialParameters
import com.chimali.fido2.domain.model.PublicKeyCredentialRpEntity
import com.chimali.fido2.domain.model.PublicKeyCredentialUserEntity
import com.chimali.fido2.presentation.viewmodel.Fido2HomeViewModel

/**
 * Development / QA screen housing test utilities that should not appear
 * in the production Authenticator dashboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevelopmentToolsScreen(
    viewModel: Fido2HomeViewModel = hiltViewModel()
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Development Tools", 
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    ) 
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.BugReport,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Test & Debug Utilities",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── Registration flow test trigger ────────────────────────────────
            OutlinedButton(
                onClick = {
                    val mockUserId = "user_${System.currentTimeMillis()}"
                    val mockOptions = MakeCredentialOptions.create(
                        rp = PublicKeyCredentialRpEntity.create("webauthn.io", "WebAuthn.io (Test)"),
                        user = PublicKeyCredentialUserEntity.create(
                            mockUserId.toByteArray(), mockUserId, "Chimali Test User"
                        ),
                        challenge = "challenge".toByteArray(),
                        pubKeyCredParams = PublicKeyCredentialParameters.createES256P256()
                    )
                    viewModel.testRegistration(mockOptions)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Text("Trigger Test Registration UI")
            }

            Text(
                text = "Simulates an incoming FIDO2 MakeCredential request from a PC host.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
