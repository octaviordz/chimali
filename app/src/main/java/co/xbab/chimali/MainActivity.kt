package co.xbab.chimali

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import co.xbab.chimali.ui.ChimaliApp
import co.xbab.chimali.ui.theme.ChimaliTheme

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChimaliTheme {
                val windowSizeClass = calculateWindowSizeClass(this)
                ChimaliApp(windowSizeClass)
            }
        }
    }
}