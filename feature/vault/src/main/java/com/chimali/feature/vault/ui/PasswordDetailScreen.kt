package com.chimali.feature.vault.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chimali.feature.vault.internal.payload.PasswordPayload
import com.chimali.feature.vault.ui.components.LegibleSecretText
import com.chimali.feature.vault.ui.model.LegibilityFont
import com.chimali.feature.vault.ui.model.LegibilitySettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordDetailScreen(
    payload: PasswordPayload,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit
) {
    var isPasswordRevealed by remember { mutableStateOf(false) }
    
    // Default legibility settings - in a real app, these would come from user preferences
    val legibilitySettings = LegibilitySettings(
        fontType = LegibilityFont.Atkinson,
        useSemanticHighlighting = true,
        highlightNumbers = true,
        colorblindMode = false
    )
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(payload.title) },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
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
            DetailRow(label = "Username", value = String(payload.username))
            
            // Password row with reveal toggle and legible display
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Password", 
                        style = MaterialTheme.typography.labelMedium
                    )
                    IconButton(
                        onClick = { isPasswordRevealed = !isPasswordRevealed }
                    ) {
                        Icon(
                            imageVector = if (isPasswordRevealed) {
                                Icons.Default.VisibilityOff
                            } else {
                                Icons.Default.Visibility
                            },
                            contentDescription = if (isPasswordRevealed) {
                                "Hide password"
                            } else {
                                "Show password"
                            }
                        )
                    }
                }
                
                if (isPasswordRevealed) {
                    LegibleSecretText(
                        secret = String(payload.password),
                        isRevealed = true,
                        settings = legibilitySettings,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    BasicText(
                        text = "•".repeat(payload.password.size),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }
            }
            
            DetailRow(label = "Website", value = payload.uri)
            
            if (payload.notes != null) {
                DetailRow(label = "Notes", value = String(payload.notes))
            }

            payload.customFields?.forEach { field ->
                if (field.isConcealed) {
                    // Handle concealed custom fields with legible display
                    var isFieldRevealed by remember { mutableStateOf(false) }
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = field.name,
                                style = MaterialTheme.typography.labelMedium
                            )
                            IconButton(
                                onClick = { isFieldRevealed = !isFieldRevealed }
                            ) {
                                Icon(
                                    imageVector = if (isFieldRevealed) {
                                        Icons.Default.VisibilityOff
                                    } else {
                                        Icons.Default.Visibility
                                    },
                                    contentDescription = if (isFieldRevealed) {
                                        "Hide ${field.name}"
                                    } else {
                                        "Show ${field.name}"
                                    }
                                )
                            }
                        }
                        
                        if (isFieldRevealed) {
                            LegibleSecretText(
                                secret = String(field.value),
                                isRevealed = true,
                                settings = legibilitySettings,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            BasicText(
                                text = "•".repeat(field.value.size),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                        }
                    }
                } else {
                    DetailRow(
                        label = field.name,
                        value = String(field.value)
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Back to Vault")
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Preview(showBackground = true)
@Composable
fun PasswordDetailScreenPreview() {
    PasswordDetailScreen(
        payload = PasswordPayload(
            title = "Sample Login",
            username = "user@example.com".toCharArray(),
            password = "password".toCharArray(),
            uri = "https://example.com",
            notes = "This is a sample note.".toCharArray(),
            customFields = emptyList()
        ),
        onEdit = {},
        onDelete = {},
        onBack = {}
    )
}
