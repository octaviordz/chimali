package com.chimali

import android.widget.Toast
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
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.compose.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.chimali.feature.fido2.api.FidoIntent
import com.chimali.feature.fido2.internal.FidoViewModel
import com.chimali.feature.fido2.ui.DeviceManagerScreen
import com.chimali.feature.fido2.ui.PairedDevice
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()
            val context = LocalContext.current
            
            // Required permissions for Bluetooth scanning and connectivity
            val bluetoothPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            } else {
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            }

            // Launcher for requesting permissions
            val launcher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { matches ->
                if (matches.values.all { it }) {
                    navController.navigate("fido_manager")
                }
            }

            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                onNavigateToFido = {
                                    if (bluetoothPermissions.isEmpty() || 
                                        bluetoothPermissions.all { 
                                            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED 
                                        }
                                    ) {
                                        navController.navigate("fido_manager")
                                    } else {
                                        launcher.launch(bluetoothPermissions)
                                    }
                                }
                            )
                        }
                        composable("fido_manager") {
                            val viewModel: FidoViewModel = hiltViewModel()
                            val state by viewModel.state.collectAsState()
                            
                            LaunchedEffect(Unit) {
                                viewModel.effect.collect { effect ->
                                    when (effect) {
                                        is com.chimali.feature.fido2.api.FidoEffect.ShowToast -> {
                                            Toast.makeText(context, effect.message, Toast.LENGTH_LONG).show()
                                        }
                                        com.chimali.feature.fido2.api.FidoEffect.RequestBiometric -> {
                                            // TODO: Implement biometric prompt
                                        }
                                    }
                                }
                            }

                            DeviceManagerScreen(
                                devices = state.pairedDevices,
                                availableDevices = state.discoveredDevices,
                                recentDevices = state.recentDevices,
                                isRefreshing = state.isRefreshing,
                                isBlePeripheralSupported = state.isBlePeripheralSupported,
                                onRefresh = { viewModel.onIntent(FidoIntent.RefreshDevices) },
                                onPair = { viewModel.onIntent(FidoIntent.PairDevice(it.address)) },
                                onConnect = { viewModel.onIntent(FidoIntent.ConnectDevice(it.address)) },
                                onDisconnect = { viewModel.onIntent(FidoIntent.DisconnectDevice(it.address)) },
                                onUnpair = { viewModel.onIntent(FidoIntent.UnpairDevice(it.address)) }
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
