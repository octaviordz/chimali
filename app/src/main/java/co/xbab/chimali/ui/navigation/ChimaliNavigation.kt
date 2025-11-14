package co.xbab.chimali.ui.navigation

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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

@Composable
fun ChimaliNavigation(
    navController: NavController,
    isExpandedScreen: Boolean,
    isMediumScreen: Boolean
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val navigationItems = listOf(
        NavigationItem("transform", R.drawable.ic_transform, R.string.title_transform),
        NavigationItem("reflow", R.drawable.ic_reflow, R.string.title_reflow),
        NavigationItem("slideshow", R.drawable.ic_slideshow, R.string.title_slideshow)
    )

    if (isExpandedScreen) {
        PermanentDrawerSheet(modifier = Modifier.width(240.dp)) {
            Spacer(Modifier.height(12.dp))
            navigationItems.forEach { item ->
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
                    }
                )
            }
        }
    } else if (isMediumScreen) {
        NavigationRail {
            navigationItems.forEach { item ->
                NavigationRailItem(
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
    } else {
        NavigationBar {
            navigationItems.forEach { item ->
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
}

data class NavigationItem(val route: String, val icon: Int, val label: Int)