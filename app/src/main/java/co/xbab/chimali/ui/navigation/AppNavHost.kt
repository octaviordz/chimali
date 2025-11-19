package co.xbab.chimali.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import co.xbab.chimali.responsive.ui.ResponsiveReflowScreen
import co.xbab.chimali.responsive.ui.ResponsiveSettingsScreen
import co.xbab.chimali.responsive.ui.ResponsiveSlideshowScreen
import co.xbab.chimali.responsive.ui.ResponsiveTransformScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = "transform",
        modifier = modifier
    ) {
        composable("transform") {
            ResponsiveTransformScreen(isExpanded = false, modifier = Modifier.fillMaxSize())
        }
        composable("reflow") {
            ResponsiveReflowScreen(modifier = Modifier.fillMaxSize())
        }
        composable("slideshow") {
            ResponsiveSlideshowScreen(modifier = Modifier.fillMaxSize())
        }
        composable("settings") {
            ResponsiveSettingsScreen(modifier = Modifier.fillMaxSize())
        }
    }
}