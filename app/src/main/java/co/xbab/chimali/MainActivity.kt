package co.xbab.chimali

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import co.xbab.chimali.ui.ChimaliApp
import co.xbab.chimali.ui.theme.ChimaliTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChimaliTheme {
                ChimaliApp()
            }
        }
    }
}