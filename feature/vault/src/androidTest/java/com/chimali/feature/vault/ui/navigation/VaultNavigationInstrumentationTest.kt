package com.chimali.feature.vault.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chimali.feature.vault.api.VaultType
import java.util.UUID
import org.junit.Rule
import org.junit.Test

class VaultNavigationInstrumentationTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun typeSpecificDetailRoutesResolveInAndroidNavigationRuntime() {
        val itemId = UUID.fromString("00000000-0000-0000-0000-000000000001")

        composeTestRule.setContent {
            val navController = rememberNavController()
            NavHost(navController = navController, startDestination = VaultDestinations.LIST_ROUTE) {
                composable(VaultDestinations.LIST_ROUTE) {
                    Text("list")
                }
                composable(
                    route = VaultDestinations.DETAIL_PASSWORD_ROUTE,
                    arguments = listOf(navArgument("id") { type = NavType.StringType }),
                ) {
                    Text("password-detail")
                }
                composable(
                    route = VaultDestinations.DETAIL_CARD_ROUTE,
                    arguments = listOf(navArgument("id") { type = NavType.StringType }),
                ) {
                    Text("card-detail")
                }
                composable(
                    route = VaultDestinations.DETAIL_NOTE_ROUTE,
                    arguments = listOf(navArgument("id") { type = NavType.StringType }),
                ) {
                    Text("note-detail")
                }
            }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                navController.navigate(VaultDestinations.detailRoute(VaultType.CREDIT_CARD, itemId))
            }
        }

        composeTestRule.onNodeWithText("card-detail").assertIsDisplayed()
    }
}
