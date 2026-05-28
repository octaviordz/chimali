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
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScope
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.chimali.designsystem.theme.ChimaliTheme
import app.chimali.designsystem.theme.LocalAppDimensions
import app.chimali.ui.authenticator.AuthenticatorScreen
import app.chimali.ui.devTools.SlideshowScreen
import app.chimali.ui.settings.SettingsScreen
import app.chimali.ui.vault.VaultScreen

@Composable
fun AppLegacy() {
// 1. Wrap with your custom theme layout provider to evaluate responsive breakpoints
    ChimaliTheme {
        LegacyScaffold()
    }
}

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
fun LegacyScaffold(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

// 2. Fetch the platform layout metadata from the theme engine
    val dimensions = LocalAppDimensions.current

// State tracker for top bar drop-down overflow item visibility
    var showOverflowMenu by remember { mutableStateOf(false) }

// Always fetch the real destination from the BackStack to fix synchronization bugs on back button press
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: AppDestination.Transform.route

    val baseDestinations =
        remember {
            listOf(
                AppDestination.Transform,
                AppDestination.Reflow,
                AppDestination.Slideshow,
            )
        }

    NavigationSuiteScaffold(
        modifier = modifier,
        navigationSuiteItems = {
// Render the core destinations for all screen form factors
            legacyNavigationItems(
                destinations = baseDestinations,
                currentRoute = currentRoute,
                isCompact = dimensions.isCompactLayout,
                onNavigate = { route ->
                    navController.navigate(route) {
                        launchSingleTop = true
                        restoreState = true
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                    }
                },
            )
        },
    ) {
        Scaffold(
            topBar = {
                LegacyTopAppBar(
                    isCompact = dimensions.isCompactLayout,
                    onNavigateToSettings = {
                        navController.navigate(AppDestination.Settings.route) {
                            launchSingleTop = true
                        }
                    },
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { /* FAB Action */ }) {
                    Icon(Icons.Default.Add, null)
                }
            },
            floatingActionButtonPosition = FabPosition.End,
        ) { innerPadding ->
            LegacyNavHost(
                navController = navController,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
        }
    }
}

private fun NavigationSuiteScope.legacyNavigationItems(
    destinations: List<AppDestination>,
    currentRoute: String,
    isCompact: Boolean,
    onNavigate: (route: String) -> Unit,
) {
    destinations.forEach { destination ->
        item(
            selected = currentRoute == destination.route,
            onClick = { onNavigate(destination.route) },
            icon = { Icon(destination.icon, null) },
            label = { Text(destination.label) },
        )
    }

// SPECIAL OVERFLOW CONDITION: Only append settings directly to the side panel if screen layout is NOT compact
    if (!isCompact) {
        item(
            selected = currentRoute == AppDestination.Settings.route,
            onClick = { onNavigate(AppDestination.Settings.route) },
            icon = { Icon(AppDestination.Settings.icon, null) },
            label = { Text(AppDestination.Settings.label) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LegacyTopAppBar(
    isCompact: Boolean,
    onNavigateToSettings: () -> Unit,
) {
    var showOverflowMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = { Text("Chimali") },
        actions = {
// SPECIAL OVERFLOW ACTION: Only mount the Action Bar triple-dot menu on small screens
            if (isCompact) {
                IconButton(onClick = { showOverflowMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                }

                DropdownMenu(
                    expanded = showOverflowMenu,
                    onDismissRequest = { showOverflowMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Settings") },
                        onClick = {
                            showOverflowMenu = false
                            onNavigateToSettings()
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun LegacyNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = AppDestination.Transform.route,
        modifier = modifier,
    ) {
        composable(AppDestination.Transform.route) { AuthenticatorScreen() }
        composable(AppDestination.Reflow.route) { VaultScreen() }
        composable(AppDestination.Slideshow.route) { SlideshowScreen() }
        composable(AppDestination.Settings.route) { SettingsScreen() }
    }
}

@Composable
@Preview
private fun AppLegacyPreview() {
    ChimaliTheme {
        LegacyScaffold()
    }
}
