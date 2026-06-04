package app.chimali

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScope
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import app.chimali.designsystem.theme.ChimaliTheme
import app.chimali.designsystem.theme.LocalAppDimensions
import app.chimali.ui.authenticator.AuthenticatorScreen
import app.chimali.ui.devTools.SlideshowScreen
import app.chimali.ui.settings.SettingsScreen
import app.chimali.ui.vault.VaultScreen
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.koin.compose.KoinApplication
import org.koin.dsl.koinConfiguration

@Composable
fun App() {
    KoinApplication(
        configuration = koinConfiguration(declaration = { modules(sharedAppModule) }),
        content = {
            ChimaliTheme {
                MainScaffold()
            }
        },
    )
}

data class DestinationMeta(
    val route: NavKey,
    val label: String,
    val contentDescription: String,
    val icon: ImageVector,
)

@Composable
fun MainScaffold(modifier: Modifier = Modifier) {
    val navConfig = rememberNavConfig()
    val backStack = rememberNavBackStack(navConfig, AppRoute.Authenticator)
    val currentRoute = backStack.lastOrNull() ?: AppRoute.Authenticator
    val dimensions = LocalAppDimensions.current

    val baseDestinations =
        remember {
            listOf(
                DestinationMeta(
                    AppRoute.Authenticator,
                    "Authenticator",
                    "Passkey Authenticator",
                    Icons.Filled.Security,
                ),
                DestinationMeta(AppRoute.Vault, "Vault", "Vault", Icons.Filled.Folder),
                DestinationMeta(AppRoute.DevTools, "Dev Tools", "Dev Tools", Icons.Filled.BugReport),
            )
        }

    NavigationSuiteScaffold(
        modifier = modifier,
        navigationSuiteItems = {
            appNavigationItems(
                destinations = baseDestinations,
                currentRoute = currentRoute,
                isCompact = dimensions.isCompactLayout,
                onNavigate = { route, clearStack ->
                    if (clearStack) backStack.clear()
                    backStack.add(route)
                },
            )
        },
    ) {
        Scaffold(
            topBar = {
                MainTopAppBar(
                    currentRoute = currentRoute,
                    isCompact = dimensions.isCompactLayout,
                    onNavigateToSettings = { backStack.add(AppRoute.Settings) },
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { /* FAB Action */ }) {
                    Icon(Icons.Default.Add, null)
                }
            },
        ) { innerPadding ->
            NavDisplay(
                backStack = backStack,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                entryProvider =
                    entryProvider {
                        appEntries(
                            onManagePasskeysClicked = { _ ->
                                if (currentRoute != AppRoute.Vault) {
                                    backStack.clear()
                                    backStack.add(AppRoute.Vault)
                                }
                            },
                        )
                    },
            )
        }
    }
}

@Composable
private fun rememberNavConfig(): SavedStateConfiguration =
    remember {
        SavedStateConfiguration {
            serializersModule =
                SerializersModule {
                    polymorphic(NavKey::class) {
                        subclass(AppRoute.Authenticator::class, AppRoute.Authenticator.serializer())
                        subclass(AppRoute.Vault::class, AppRoute.Vault.serializer())
                        subclass(AppRoute.DevTools::class, AppRoute.DevTools.serializer())
                        subclass(AppRoute.Settings::class, AppRoute.Settings.serializer())
                    }
                }
        }
    }

private fun NavigationSuiteScope.appNavigationItems(
    destinations: List<DestinationMeta>,
    currentRoute: NavKey,
    isCompact: Boolean,
    onNavigate: (route: NavKey, clearStack: Boolean) -> Unit,
) {
    destinations.forEach { destination ->
        item(
            selected = currentRoute == destination.route,
            onClick = {
// In Nav3 top level actions, replace or clear the list state sequence
                if (currentRoute != destination.route) {
                    onNavigate(destination.route, true)
                }
            },
            icon = { Icon(destination.icon, destination.contentDescription) },
            label = { Text(destination.label) },
        )
    }

    if (!isCompact) {
        item(
            selected = currentRoute == AppRoute.Settings,
            onClick = {
                if (currentRoute != AppRoute.Settings) {
                    onNavigate(AppRoute.Settings, false)
                }
            },
            icon = { Icon(Icons.Default.Settings, null) },
            label = { Text("Settings") },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTopAppBar(
    currentRoute: NavKey,
    isCompact: Boolean,
    onNavigateToSettings: () -> Unit,
) {
    var showOverflowMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = { Text("Chimali") },
        actions = {
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
                            if (currentRoute != AppRoute.Settings) {
                                onNavigateToSettings()
                            }
                        },
                    )
                }
            }
        },
    )
}

private fun EntryProviderScope<NavKey>.appEntries(onManagePasskeysClicked: (String) -> Unit) {
    entry<AppRoute.Authenticator> {
        AuthenticatorScreen(onManagePasskeysClicked = onManagePasskeysClicked)
    }
    entry<AppRoute.Vault> { VaultScreen() }
    entry<AppRoute.DevTools> { SlideshowScreen() }
    entry<AppRoute.Settings> { SettingsScreen() }
}

@Composable
@Preview
private fun AppPreview() {
    ChimaliTheme {
        MainScaffold()
    }
}
