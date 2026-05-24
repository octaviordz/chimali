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
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.entryProvider
import androidx.savedstate.serialization.SavedStateConfiguration
import app.chimali.ui.reflow.ReflowScreen
import app.chimali.ui.settings.SettingsScreen
import app.chimali.ui.slideshow.SlideshowScreen
import app.chimali.ui.theme.ChimaliTheme
import app.chimali.ui.theme.LocalAppDimensions
import app.chimali.ui.transform.TransformScreen
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

data class DestinationMeta(
    val route: AppRoute,
    val label: String,
    val icon: ImageVector
)

@Composable
@Preview
fun App() {
    ChimaliTheme {
        MainScaffold()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold() {
    val navConfig = remember {
        SavedStateConfiguration {
            serializersModule = SerializersModule {
                polymorphic(NavKey::class) {
                    subclass(AppRoute.Transform::class, AppRoute.Transform.serializer())
                    subclass(AppRoute.Reflow::class, AppRoute.Reflow.serializer())
                    subclass(AppRoute.Slideshow::class, AppRoute.Slideshow.serializer())
                    subclass(AppRoute.Settings::class, AppRoute.Settings.serializer())
                }
            }
        }
    }

    val backStack = rememberNavBackStack(navConfig, AppRoute.Transform)

    val currentRoute = backStack.lastOrNull() ?: AppRoute.Transform

    val dimensions = LocalAppDimensions.current
    var showOverflowMenu by remember { mutableStateOf(false) }

    val baseDestinations = listOf(
        DestinationMeta(AppRoute.Transform, "Transform", Icons.Default.Home),
        DestinationMeta(AppRoute.Reflow, "Reflow", Icons.Default.Build),
        DestinationMeta(AppRoute.Slideshow, "Slideshow", Icons.Default.ViewCarousel)
    )

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            baseDestinations.forEach { destination ->
                item(
                    selected = currentRoute == destination.route,
                    onClick = {
                        // In Nav3 top level actions, replace or clear the list state sequence
                        if (currentRoute != destination.route) {
                            backStack.clear()
                            backStack.add(destination.route)
                        }
                    },
                    icon = { Icon(destination.icon, null) },
                    label = { Text(destination.label) }
                )
            }

            if (!dimensions.isCompactLayout) {
                item(
                    selected = currentRoute == AppRoute.Settings,
                    onClick = {
                        if (currentRoute != AppRoute.Settings) {
                            backStack.add(AppRoute.Settings)
                        }
                    },
                    icon = { Icon(Icons.Default.Settings, null) },
                    label = { Text("Settings") }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Chimali") },
                    actions = {
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
                                        if (currentRoute != AppRoute.Settings) {
                                            backStack.add(AppRoute.Settings)
                                        }
                                    }
                                )
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { /* FAB Action */ }) {
                    Icon(Icons.Default.Add, null)
                }
            }
        ) { innerPadding ->
            NavDisplay(
                backStack = backStack,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                entryProvider = entryProvider {
                    entry<AppRoute.Transform> { TransformScreen() }
                    entry<AppRoute.Reflow> { ReflowScreen() }
                    entry<AppRoute.Slideshow> { SlideshowScreen() }
                    entry<AppRoute.Settings> { SettingsScreen() }
                }
            )
        }
    }
}
