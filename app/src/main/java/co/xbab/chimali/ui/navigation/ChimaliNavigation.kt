package co.xbab.chimali.ui.navigation

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import co.xbab.chimali.R

// Data class for a navigation item
data class NavigationItem(val route: String, val icon: Int, val label: Int)

// List of main destinations for the bottom bar
val mainDestinations = listOf(
    NavigationItem("transform", R.drawable.ic_transform, R.string.title_transform),
    NavigationItem("reflow", R.drawable.ic_reflow, R.string.title_reflow),
    NavigationItem("slideshow", R.drawable.ic_slideshow, R.string.title_slideshow)
)

// The single "Settings" destination
val settingsDestination = NavigationItem("settings", R.drawable.ic_settings, R.string.title_settings)


/**
 * The content of the modal or permanent navigation drawer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationDrawerContent(
    navController: NavController,
    onDrawerItemClick: () -> Unit
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Combine main destinations and the settings destination for the drawer
    val drawerDestinations = mainDestinations + settingsDestination

    Spacer(Modifier.height(12.dp))
    drawerDestinations.forEach { item ->
        NavigationDrawerItem(
            icon = { Icon(painterResource(id = item.icon), contentDescription = null) },
            label = { Text(stringResource(id = item.label)) },
            selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
            onClick = {
                navController.navigate(item.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
                onDrawerItemClick() // Close the drawer
            }
        )
    }
}

/**
 * The content of the bottom navigation bar for compact screens.
 */
@Composable
fun BottomBarContent(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationBar {
        mainDestinations.forEach { item ->
            NavigationBarItem(
                icon = { Icon(painterResource(id = item.icon), contentDescription = null) },
                label = { Text(stringResource(id = item.label)) },
                selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}