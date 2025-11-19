package co.xbab.chimali.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.width
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import co.xbab.chimali.R
import co.xbab.chimali.ui.navigation.AppNavHost
import co.xbab.chimali.ui.navigation.BottomBarContent
import co.xbab.chimali.ui.navigation.NavigationDrawerContent
import co.xbab.chimali.ui.theme.ChimaliTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChimaliApp(
    windowSizeClass: WindowSizeClass,
    onLaunchResponsiveApp: () -> Unit = {}
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val widthSizeClass = windowSizeClass.widthSizeClass
    val isExpandedScreen = widthSizeClass == WindowWidthSizeClass.Expanded
    val isMediumScreen = widthSizeClass == WindowWidthSizeClass.Medium

    if (isExpandedScreen) {
        // Expanded layout: Permanent Navigation Drawer
        PermanentNavigationDrawer(
            drawerContent = {
                PermanentDrawerSheet(Modifier.width(240.dp)) {
                    NavigationDrawerContent(navController) { /* No action needed to close */ }
                }
            }
        ) {
            AppContent(navController, snackbarHostState, isExpandedOrMedium = true, onLaunchResponsiveApp = onLaunchResponsiveApp)
        }
    } else if (isMediumScreen) {
        // Medium layout: Modal Navigation Drawer
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    NavigationDrawerContent(navController) { scope.launch { drawerState.close() } }
                }
            },
        ) {
            AppContent(navController, snackbarHostState, isExpandedOrMedium = true, onLaunchResponsiveApp = onLaunchResponsiveApp) {
                // Hamburger icon to open drawer
                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                    Icon(painterResource(id = R.drawable.ic_menu), contentDescription = stringResource(R.string.title_settings))
                }
            }
        }
    } else {
        // Compact layout: Bottom Navigation + Kebab menu
        AppContent(navController, snackbarHostState, isExpandedOrMedium = false, onLaunchResponsiveApp = onLaunchResponsiveApp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppContent(
    navController: NavHostController,
    snackbarHostState: SnackbarHostState,
    isExpandedOrMedium: Boolean,
    onLaunchResponsiveApp: () -> Unit = {},
    navigationIcon: @Composable () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.app_name)) },
                navigationIcon = navigationIcon,
                actions = {
                    // Button to launch Responsive App
                    IconButton(onClick = onLaunchResponsiveApp) {
                        Icon(
                            painter = painterResource(id = android.R.drawable.ic_menu_view), // Using a standard icon
                            contentDescription = "Responsive App"
                        )
                    }

                    // Show kebab menu only on compact screens
                    if (!isExpandedOrMedium) {
                        Box(modifier = Modifier.wrapContentSize(Alignment.TopStart)) {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_more_vert),
                                    contentDescription = stringResource(id = R.string.title_settings)
                                )
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
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
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    scope.launch { snackbarHostState.showSnackbar("Email sent") }
                }
            ) {
                Icon(
                    painter = painterResource(id = android.R.drawable.ic_dialog_email),
                    contentDescription = stringResource(id = R.string.send_email)
                )
            }
        },
        bottomBar = {
            // Show bottom bar only on compact screens
            if (!isExpandedOrMedium) {
                BottomBarContent(navController)
            }
        }
    ) { innerPadding ->
        AppNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding)
        )
    }
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Preview(showBackground = true)
@Composable
fun ChimaliAppPreview() {
    ChimaliTheme {
        ChimaliApp(windowSizeClass = WindowSizeClass.calculateFromSize(DpSize(400.dp, 800.dp)))
    }
}