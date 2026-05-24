package app.chimali

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.chimali.ui.reflow.ReflowScreen
import app.chimali.ui.settings.SettingsScreen
import app.chimali.ui.slideshow.SlideshowScreen
import app.chimali.ui.theme.ChimaliTheme
import app.chimali.ui.theme.LocalAppDimensions
import app.chimali.ui.transform.TransformScreen

sealed class AppDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    data object Transform : AppDestination("transform", "Transform", Icons.Default.Home)
    data object Reflow : AppDestination("reflow", "Reflow", Icons.Default.Build)
    data object Slideshow : AppDestination("slideshow", "Slideshow", Icons.Default.ViewCarousel)
    data object Settings : AppDestination("settings", "Settings", Icons.Default.Settings)
}

@Composable
@Preview
fun AppLegacy() {
    // 1. Wrap with your custom theme layout provider to evaluate responsive breakpoints
    ChimaliTheme {
        LegacyScaffold()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegacyScaffold() {
    val navController = rememberNavController()

    // 2. Fetch the platform layout metadata from the theme engine
    val dimensions = LocalAppDimensions.current

    // State tracker for top bar drop-down overflow item visibility
    var showOverflowMenu by remember { mutableStateOf(false) }

    // Always fetch the real destination from the BackStack to fix synchronization bugs on back button press
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: AppDestination.Transform.route

    val baseDestinations = listOf(
        AppDestination.Transform,
        AppDestination.Reflow,
        AppDestination.Slideshow,
    )

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            // Render the core destinations for all screen form factors
            baseDestinations.forEach { destination ->
                item(
                    selected = currentRoute == destination.route,
                    onClick = {
                        navController.navigate(destination.route) {
                            launchSingleTop = true
                            restoreState = true
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                        }
                    },
                    icon = { Icon(destination.icon, null) },
                    label = { Text(destination.label) }
                )
            }

            // SPECIAL OVERFLOW CONDITION: Only append settings directly to the side panel if screen layout is NOT compact
            if (!dimensions.isCompactLayout) {
                item(
                    selected = currentRoute == AppDestination.Settings.route,
                    onClick = {
                        navController.navigate(AppDestination.Settings.route) {
                            launchSingleTop = true
                            restoreState = true
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                        }
                    },
                    icon = { Icon(AppDestination.Settings.icon, null) },
                    label = { Text(AppDestination.Settings.label) }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Chimali") },
                    actions = {
                        // SPECIAL OVERFLOW ACTION: Only mount the Action Bar triple-dot menu on small screens
                        if (dimensions.isCompactLayout) {
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                            }

                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Settings") },
                                    onClick = {
                                        showOverflowMenu = false
                                        navController.navigate(AppDestination.Settings.route) {
                                            launchSingleTop = true
                                        }
                                    }
                                )
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { /* FAB Action */ },
                ) {
                    Icon(Icons.Default.Add, null)
                }
            },
            floatingActionButtonPosition = FabPosition.End
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = AppDestination.Transform.route,
                modifier = Modifier.fillMaxSize().padding(innerPadding)
            ) {
                composable(AppDestination.Transform.route) { TransformScreen() }
                composable(AppDestination.Reflow.route) { ReflowScreen() }
                composable(AppDestination.Slideshow.route) { SlideshowScreen() }
                composable(AppDestination.Settings.route) { SettingsScreen() }
            }
        }
    }
}
