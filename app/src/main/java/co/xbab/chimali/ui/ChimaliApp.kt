package co.xbab.chimali.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import co.xbab.chimali.R
import co.xbab.chimali.ui.navigation.AppNavHost
import co.xbab.chimali.ui.navigation.ChimaliNavigation
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChimaliApp(windowSizeClass: WindowSizeClass) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val widthSizeClass = windowSizeClass.widthSizeClass
    val isExpandedScreen = widthSizeClass == WindowWidthSizeClass.Expanded
    val isMediumScreen = widthSizeClass == WindowWidthSizeClass.Medium

    val isTopLevelDestination = currentDestination?.hierarchy?.any {
        it.route == "transform" || it.route == "reflow" || it.route == "slideshow"
    } == true

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.app_name)) },
                navigationIcon = {
                    if (!isTopLevelDestination) {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_arrow_back),
                                contentDescription = stringResource(id = R.string.abc_action_bar_up_description)
                            )
                        }
                    }
                },
                actions = {
                    if (isTopLevelDestination) {
                        var showMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_more_vert),
                                contentDescription = stringResource(id = R.string.title_settings)
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(id = R.string.title_settings)) },
                                onClick = {
                                    navController.navigate("settings")
                                    showMenu = false
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    scope.launch {
                        snackbarHostState.showSnackbar("Email sent")
                    }
                }
            ) {
                Icon(
                    painter = painterResource(id = android.R.drawable.ic_dialog_email),
                    contentDescription = stringResource(id = R.string.send_email)
                )
            }
        },
        bottomBar = {
            if (!isExpandedScreen && !isMediumScreen) { // Only for Compact screens
                ChimaliNavigation(
                    navController = navController,
                    isExpandedScreen = false,
                    isMediumScreen = false
                )
            }
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isExpandedScreen || isMediumScreen) {
                ChimaliNavigation(
                    navController = navController,
                    isExpandedScreen = isExpandedScreen,
                    isMediumScreen = isMediumScreen
                )
            }
            AppNavHost(
                navController = navController,
                modifier = Modifier.weight(1f) // This is critical
            )
        }
    }
}