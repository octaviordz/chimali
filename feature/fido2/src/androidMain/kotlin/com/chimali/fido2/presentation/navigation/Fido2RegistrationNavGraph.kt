package com.chimali.fido2.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.chimali.core.common.result.map
import com.chimali.fido2.presentation.ui.AuthenticationPromptScreen
import com.chimali.fido2.presentation.ui.DevelopmentToolsScreen
import com.chimali.fido2.presentation.ui.RegistrationPromptScreen
import org.koin.compose.viewmodel.koinViewModel

// Routes defined in Fido2Destinations.kt

private sealed class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    object Authenticator : BottomNavItem(
        route = Fido2Destinations.HOME_ROUTE,
        label = "Authenticator",
        icon = Icons.Default.Security,
    )

    object DevTools : BottomNavItem(
        route = Fido2Destinations.DEVELOPMENT_ROUTE,
        label = "Dev Tools",
        icon = Icons.Default.BugReport,
    )
}

private val bottomNavItems =
    listOf(
        BottomNavItem.Authenticator,
        BottomNavItem.DevTools,
    )

@Suppress(
    // Past-tense lambda names match the existing public API contract
    "ParameterNaming",
    // Modifier placement is intentional for this nav graph signature
    "ComposableParamOrder",
    // Modifier is intentionally applied to the inner Box wrapping NavHost
    "ModifierNotUsedAtRoot",
    "FunctionNaming",
    "ForbiddenComment",
)
@Composable
fun Fido2RegistrationNavGraph(
    onRegistrationComplete: (credentialId: String) -> Unit,
    onRegistrationCancelled: () -> Unit,
    onAuthenticationComplete: (credentialId: String) -> Unit = {},
    onAuthenticationCancelled: () -> Unit = {},
    navController: NavHostController = rememberNavController(),
    startDestination: String = Fido2Destinations.HOME_ROUTE,
    modifier: Modifier = Modifier,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // The bottom bar is only visible on the top-level routes
    val showBottomBar = currentRoute in bottomNavItems.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                if (currentRoute != item.route) {
                                    navController.navigate(item.route) {
                                        // Pop up to HOME so back-stack doesn't grow indefinitely
                                        popUpTo(Fido2Destinations.HOME_ROUTE) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(imageVector = item.icon, contentDescription = item.label)
                            },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = modifier.padding(innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = startDestination,
            ) {
                composable(Fido2Destinations.HOME_ROUTE) { entry ->
                    val pairedViewModel: com.chimali.fido2.presentation.viewmodel.PairedDevicesViewModel =
                        koinViewModel(viewModelStoreOwner = entry)

                    com.chimali.fido2.presentation.ui.Fido2HomeScreen(
                        onManageCredentials = {
                            navController.navigate(Fido2Destinations.MANAGEMENT_ROUTE)
                        },
                        onRegisterRequest = {
                            navController.navigate(Fido2Destinations.REGISTRATION_ROUTE)
                        },
                        onAuthenticateRequest = {
                            navController.navigate(Fido2Destinations.AUTHENTICATION_ROUTE)
                        },
                        onEditDevice = { macAddress ->
                            navController.navigate("${Fido2Destinations.EDIT_PAIRED_DEVICE_ROUTE}/$macAddress")
                        },
                        pairedDevicesViewModel = pairedViewModel,
                    )
                }

                composable(Fido2Destinations.DEVELOPMENT_ROUTE) {
                    DevelopmentToolsScreen()
                }

                composable(Fido2Destinations.REGISTRATION_ROUTE) {
                    RegistrationPromptScreen(
                        onSuccess = { credentialId ->
                            navController.popBackStack()
                            onRegistrationComplete(credentialId)
                        },
                        onCancel = {
                            navController.popBackStack()
                            onRegistrationCancelled()
                        },
                    )
                }

                composable(Fido2Destinations.AUTHENTICATION_ROUTE) {
                    AuthenticationPromptScreen(
                        onSuccess = { credentialId ->
                            navController.popBackStack()
                            onAuthenticationComplete(credentialId)
                        },
                        onCancel = {
                            navController.popBackStack()
                            onAuthenticationCancelled()
                        },
                    )
                }

                composable(Fido2Destinations.MANAGEMENT_ROUTE) {
                    com.chimali.fido2.presentation.management.CredentialListScreen(
                        onNavigateUp = {
                            navController.popBackStack()
                        },
                    )
                }

                composable(
                    route = "${Fido2Destinations.EDIT_PAIRED_DEVICE_ROUTE}/{macAddress}",
                ) { backStackEntry ->
                    val macAddress = backStackEntry.arguments?.getString("macAddress") ?: ""

                    // Scope the ViewModel to the HOME_ROUTE so it's shared with PairedDevicesSection
                    // This ensures that onDeleteTriggered calls pendingRemove on the same instance
                    // that the list is observing, so the snackbar shows up when we pop back.
                    val parentBackStackEntry =
                        remember(backStackEntry) {
                            navController.getBackStackEntry(Fido2Destinations.HOME_ROUTE)
                        }
                    val viewModel: com.chimali.fido2.presentation.viewmodel.PairedDevicesViewModel =
                        koinViewModel(viewModelStoreOwner = parentBackStackEntry)

                    com.chimali.fido2.presentation.ui.EditPairedDeviceScreen(
                        macAddress = macAddress,
                        onNavigateUp = { navController.popBackStack() },
                        viewModel = viewModel,
                    )
                }
            }
        }
    }
}
