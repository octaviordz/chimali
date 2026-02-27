package com.chimali

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.chimali.feature.fido2.ui.DeviceManagerScreen
import com.chimali.feature.fido2.ui.PairedDevice
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                onNavigateToFido = { navController.navigate("fido_manager") }
                            )
                        }
                        composable("fido_manager") {
                            // Placeholder for real device list from HidManager/ViewModel
                            DeviceManagerScreen(
                                devices = listOf(
                                    PairedDevice("00:11:22:33:44:55", "Laptop-Office", true),
                                    PairedDevice("AA:BB:CC:DD:EE:FF", "Home-PC", false)
                                ),
                                onDisconnect = { /* TODO */ },
                                onUnpair = { /* TODO */ }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(onNavigateToFido: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Welcome to Chimali!", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onNavigateToFido) {
            Text("FIDO2 Authenticator")
        }
    }
}
