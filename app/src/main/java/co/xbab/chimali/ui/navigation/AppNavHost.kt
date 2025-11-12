package co.xbab.chimali.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import co.xbab.chimali.ui.reflow.ReflowScreen
import co.xbab.chimali.ui.settings.SettingsScreen
import co.xbab.chimali.ui.slideshow.SlideshowScreen
import co.xbab.chimali.ui.transform.TransformScreen

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
            TransformScreen()
        }
        composable("reflow") {
            ReflowScreen()
        }
        composable("slideshow") {
            SlideshowScreen()
        }
        composable("settings") {
            SettingsScreen()
        }
    }
}