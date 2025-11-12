package co.xbab.chimali.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import co.xbab.chimali.R
import co.xbab.chimali.ui.navigation.AppNavHost
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChimaliApp() {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                NavigationDrawerItem(
                    label = { Text(text = stringResource(id = R.string.title_settings)) },
                    selected = false,
                    onClick = { 
                        navController.navigate("settings")
                        scope.launch { drawerState.close() }
                     }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(text = stringResource(id = R.string.app_name)) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(painter = painterResource(id = R.drawable.ic_menu), contentDescription = null)
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentDestination = navBackStackEntry?.destination
                    NavigationBarItem(
                        icon = { Icon(painterResource(id = R.drawable.ic_transform), contentDescription = null) },
                        label = { Text(stringResource(id = R.string.title_transform)) },
                        selected = currentDestination?.hierarchy?.any { it.route == "transform" } == true,
                        onClick = {
                            navController.navigate("transform") {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(painterResource(id = R.drawable.ic_reflow), contentDescription = null) },
                        label = { Text(stringResource(id = R.string.title_reflow)) },
                        selected = currentDestination?.hierarchy?.any { it.route == "reflow" } == true,
                        onClick = {
                            navController.navigate("reflow") {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(painterResource(id = R.drawable.ic_slideshow), contentDescription = null) },
                        label = { Text(stringResource(id = R.string.title_slideshow)) },
                        selected = currentDestination?.hierarchy?.any { it.route == "slideshow" } == true,
                        onClick = {
                            navController.navigate("slideshow") {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        ) { innerPadding ->
            AppNavHost(navController = navController, modifier = Modifier.padding(innerPadding))
        }
    }
}