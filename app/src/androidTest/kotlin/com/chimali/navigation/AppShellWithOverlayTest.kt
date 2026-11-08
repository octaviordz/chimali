package com.chimali.navigation

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AppShellWithOverlayTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun ceremonyRequestDismissesSettingsWithoutDisposingMainShell() {
        var mainShellMounted = false
        var dispatchAuthenticationRequest: (() -> Unit)? = null
        var settingsVisible by mutableStateOf(true)
        var authenticationPromptVisible by mutableStateOf(false)

        composeRule.setContent {
            AppShellWithOverlay(
                overlayVisible = settingsVisible,
                onOverlayBack = { settingsVisible = false },
                mainContent = {
                    DisposableEffect(Unit) {
                        mainShellMounted = true
                        onDispose { mainShellMounted = false }
                    }
                    SideEffect {
                        dispatchAuthenticationRequest = {
                            authenticationPromptVisible = true
                            settingsVisible = false
                        }
                    }
                    if (authenticationPromptVisible) {
                        Text("Authentication verification prompt")
                    } else {
                        Text("Authenticator shell")
                    }
                },
                overlayContent = { Text("Settings overlay") },
            )
        }

        composeRule.runOnIdle {
            assertTrue(mainShellMounted)
        }
        composeRule.onNodeWithText("Settings overlay").assertIsDisplayed()
        composeRule.runOnIdle { dispatchAuthenticationRequest?.invoke() }
        composeRule.onNodeWithText("Authentication verification prompt").assertIsDisplayed()
        composeRule.runOnIdle { assertTrue(mainShellMounted) }
    }

    @Test
    fun systemBackClosesSettingsAndRestoresPendingCeremonyPrompt() {
        var settingsVisible by mutableStateOf(true)
        var pendingCeremonyIdentity: Any? = null
        var mainShellDisposed = false
        var navController: NavHostController? = null

        composeRule.setContent {
            val controller = rememberNavController()
            SideEffect { navController = controller }
            AppShellWithOverlay(
                overlayVisible = settingsVisible,
                onOverlayBack = { settingsVisible = false },
                mainContent = {
                    DisposableEffect(Unit) {
                        onDispose { mainShellDisposed = true }
                    }
                    NavHost(
                        navController = controller,
                        startDestination = "home",
                    ) {
                        composable("home") { Text("Authenticator home") }
                        composable("consent") {
                            val ceremonyEntry = controller.currentBackStackEntry
                            SideEffect { pendingCeremonyIdentity = ceremonyEntry }
                            Text("Pending authentication consent")
                        }
                    }
                },
                overlayContent = { Text("Settings overlay") },
            )
        }

        composeRule.runOnIdle {
            navController!!.navigate("consent")
        }
        composeRule.onNodeWithText("Settings overlay").assertIsDisplayed()
        composeRule.runOnIdle {
            assertTrue(pendingCeremonyIdentity != null)
        }
        val originalCeremonyIdentity = pendingCeremonyIdentity
        composeRule.runOnIdle {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.onNodeWithText("Pending authentication consent").assertIsDisplayed()
        composeRule.runOnIdle {
            assertFalse(settingsVisible)
            assertFalse(mainShellDisposed)
            assertSame(originalCeremonyIdentity, pendingCeremonyIdentity)
            assertEquals("consent", navController?.currentDestination?.route)
        }
    }
}
