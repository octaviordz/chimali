package co.xbab.chimali.responsive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import co.xbab.chimali.responsive.ui.ResponsiveReflowScreen
import co.xbab.chimali.responsive.ui.ResponsiveDestinations
import co.xbab.chimali.responsive.ui.ResponsiveSettingsScreen
import co.xbab.chimali.responsive.ui.ResponsiveSlideshowScreen
import co.xbab.chimali.responsive.ui.ResponsiveTransformScreen

@Composable
fun ResponsiveApp(
    windowSize: WindowWidthSizeClass,
    onBack: () -> Unit
) {
    var currentRoute by remember { mutableStateOf(ResponsiveDestinations.TRANSFORM_ROUTE) }

    val navigationItems = listOf(
        NavigationItem("Transform", Icons.Default.Home, ResponsiveDestinations.TRANSFORM_ROUTE),
        NavigationItem("Reflow", Icons.Default.Home, ResponsiveDestinations.REFLOW_ROUTE),
        NavigationItem("Slideshow", Icons.Default.Home, ResponsiveDestinations.SLIDESHOW_ROUTE),
        NavigationItem("Settings", Icons.Default.Settings, ResponsiveDestinations.SETTINGS_ROUTE)
    )

    when (windowSize) {
        WindowWidthSizeClass.Compact -> {
            ResponsiveAppBottomNav(
                currentRoute = currentRoute,
                onNavigate = { currentRoute = it },
                items = navigationItems,
                content = {
                    ResponsiveContent(currentRoute, isExpanded = false)
                }
            )
        }
        WindowWidthSizeClass.Medium -> {
            ResponsiveAppNavRail(
                currentRoute = currentRoute,
                onNavigate = { currentRoute = it },
                items = navigationItems,
                content = {
                    ResponsiveContent(currentRoute, isExpanded = false)
                }
            )
        }
        WindowWidthSizeClass.Expanded -> {
            ResponsiveAppPermanentDrawer(
                currentRoute = currentRoute,
                onNavigate = { currentRoute = it },
                items = navigationItems,
                content = {
                    ResponsiveContent(currentRoute, isExpanded = true)
                }
            )
        }
        else -> {
            ResponsiveAppBottomNav(
                currentRoute = currentRoute,
                onNavigate = { currentRoute = it },
                items = navigationItems,
                content = {
                    ResponsiveContent(currentRoute, isExpanded = false)
                }
            )
        }
    }
}

@Composable
fun ResponsiveContent(currentRoute: String, isExpanded: Boolean) {
    when (currentRoute) {
        ResponsiveDestinations.TRANSFORM_ROUTE -> ResponsiveTransformScreen(isExpanded)
        ResponsiveDestinations.REFLOW_ROUTE -> ResponsiveReflowScreen()
        ResponsiveDestinations.SLIDESHOW_ROUTE -> ResponsiveSlideshowScreen()
        ResponsiveDestinations.SETTINGS_ROUTE -> ResponsiveSettingsScreen()
    }
}

@Composable
fun ResponsiveAppBottomNav(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    items: List<NavigationItem>,
    content: @Composable () -> Unit
) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEach { item ->
                    NavigationBarItem(
                        selected = currentRoute == item.route,
                        onClick = { onNavigate(item.route) },
                        icon = { Icon(item.icon, contentDescription = item.title) },
                        label = { Text(item.title) }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            content()
        }
    }
}

@Composable
fun ResponsiveAppNavRail(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    items: List<NavigationItem>,
    content: @Composable () -> Unit
) {
    Row(modifier = Modifier.fillMaxSize()) {
        NavigationRail {
            items.forEach { item ->
                NavigationRailItem(
                    selected = currentRoute == item.route,
                    onClick = { onNavigate(item.route) },
                    icon = { Icon(item.icon, contentDescription = item.title) },
                    label = { Text(item.title) }
                )
            }
        }
        Box(modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}

@Composable
fun ResponsiveAppPermanentDrawer(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    items: List<NavigationItem>,
    content: @Composable () -> Unit
) {
    PermanentNavigationDrawer(
        drawerContent = {
            PermanentDrawerSheet {
                items.forEach { item ->
                    NavigationDrawerItem(
                        label = { Text(item.title) },
                        selected = currentRoute == item.route,
                        onClick = { onNavigate(item.route) },
                        icon = { Icon(item.icon, contentDescription = item.title) }
                    )
                }
            }
        }
    ) {
        content()
    }
}

data class NavigationItem(
    val title: String,
    val icon: ImageVector,
    val route: String
)
