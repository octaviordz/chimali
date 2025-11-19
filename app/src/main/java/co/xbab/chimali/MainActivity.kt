package co.xbab.chimali

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import co.xbab.chimali.ui.ChimaliApp
import co.xbab.chimali.ui.theme.ChimaliTheme

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChimaliTheme {
                val windowSizeClass = calculateWindowSizeClass(this)
                var showResponsiveApp by remember { mutableStateOf(false) }

                if (showResponsiveApp) {
                    co.xbab.chimali.responsive.ResponsiveApp(
                        windowSize = windowSizeClass.widthSizeClass,
                        onBack = { showResponsiveApp = false }
                    )
                } else {
                    ChimaliApp(
                        windowSizeClass = windowSizeClass,
                        onLaunchResponsiveApp = { showResponsiveApp = true }
                    )
                }
            }
        }
    }
}