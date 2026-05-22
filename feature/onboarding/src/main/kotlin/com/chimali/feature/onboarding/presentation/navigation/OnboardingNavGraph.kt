package com.chimali.feature.onboarding.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.chimali.feature.onboarding.presentation.ui.FeatureSelectionScreen
import com.chimali.feature.onboarding.presentation.ui.IntroScreen
import com.chimali.feature.onboarding.presentation.viewmodel.OnboardingViewModel
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun OnboardingNavGraph(
    onFinish: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    viewModel: OnboardingViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    NavHost(
        navController = navController,
        startDestination = OnboardingDestinations.INTRO_ROUTE,
        modifier = modifier,
    ) {
        composable(OnboardingDestinations.INTRO_ROUTE) {
            IntroScreen(
                onContinue = {
                    viewModel.onContinueFromIntro()
                    navController.navigate(OnboardingDestinations.FEATURE_SELECTION_ROUTE)
                },
                onCancel = onCancel,
            )
        }
        composable(OnboardingDestinations.FEATURE_SELECTION_ROUTE) {
            FeatureSelectionScreen(
                state = state,
                onVaultToggle = viewModel::onVaultToggled,
                onPasskeyToggle = viewModel::onPasskeyToggled,
                onBack = {
                    viewModel.onBackToIntro()
                    navController.popBackStack()
                },
                onFinish = {
                    coroutineScope.launch {
                        if (viewModel.finishOnboarding()) {
                            onFinish()
                        }
                    }
                },
            )
        }
    }
}
