package com.chimali.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import com.chimali.core.common.datastore.UserPreferences
import com.chimali.feature.onboarding.presentation.navigation.OnboardingNavGraph
import com.chimali.feature.settings.ui.SettingsScreen
import com.chimali.feature.vault.internal.VaultViewModel
import com.chimali.feature.vault.ui.VaultListScreen
import com.chimali.fido2.presentation.navigation.Fido2Destinations
import com.chimali.fido2.presentation.navigation.Fido2RegistrationNavGraph
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private enum class AppScreen {
    Loading,
    Onboarding,
    Shell,
    Settings,
}

@Composable
fun AppNavGraph(
    modifier: Modifier = Modifier,
    dataStore: DataStore<UserPreferences> = koinInject(),
) {
    val routingFlow =
        remember(dataStore) {
            dataStore.data
                .catch { exception ->
                    if (exception is CorruptionException) {
                        emit(UserPreferences())
                    } else {
                        throw exception
                    }
                }.map(AppRoutingState::from)
        }
    val routingState by routingFlow.collectAsState(initial = null)

    var screenOverride by rememberSaveable { mutableStateOf<AppScreen?>(null) }
    var onboardingReturnScreen by rememberSaveable { mutableStateOf(AppScreen.Shell) }

    val targetScreen =
        when {
            screenOverride != null -> screenOverride!!
            routingState == null -> AppScreen.Loading
            !routingState!!.onboardingCompleted -> AppScreen.Onboarding
            else -> AppScreen.Shell
        }

    when (targetScreen) {
        AppScreen.Loading -> {
            LoadingScreen(modifier = modifier)
        }

        AppScreen.Onboarding -> {
            OnboardingNavGraph(
                onFinish = {
                    screenOverride = null
                    onboardingReturnScreen = AppScreen.Shell
                },
                onCancel = {
                    screenOverride = null
                    screenOverride = onboardingReturnScreen
                },
                modifier = modifier,
            )
        }

        AppScreen.Shell -> {
            MainShell(
                routingState = routingState ?: AppRoutingState(false, false, false, ""),
                dataStore = dataStore,
                onOpenSettings = {
                    onboardingReturnScreen = AppScreen.Shell
                    screenOverride = AppScreen.Settings
                },
                modifier = modifier,
            )
        }

        AppScreen.Settings -> {
            SettingsScreen(
                vaultEnabled = routingState?.vaultEnabled ?: false,
                passkeyEnabled = routingState?.passkeyEnabled ?: false,
                onRetakeOnboarding = {
                    onboardingReturnScreen = AppScreen.Settings
                    screenOverride = AppScreen.Onboarding
                },
                onBack = {
                    screenOverride = null
                },
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun LoadingScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun MainShell(
    routingState: AppRoutingState,
    dataStore: DataStore<UserPreferences>,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dualFeatureSelection = routingState.hasVaultAndPasskeyEnabled
    var selectedRoute by rememberSaveable(routingState.lastVisitedMainScreen) {
        mutableStateOf(routingState.mainRoute)
    }

    LaunchedEffect(routingState.mainRoute) {
        selectedRoute = routingState.mainRoute
    }

    LaunchedEffect(dualFeatureSelection, selectedRoute) {
        val isValidMainRoute =
            selectedRoute == AppDestinations.VAULT_ROUTE || selectedRoute == Fido2Destinations.HOME_ROUTE
        if (dualFeatureSelection &&
            isValidMainRoute &&
            routingState.normalizedLastVisitedMainScreen() != selectedRoute
        ) {
            dataStore.updateData { preferences ->
                preferences.copy(lastVisitedMainScreen = selectedRoute)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (dualFeatureSelection || com.chimali.core.common.isDebug) {
                NavigationBar {
                    if (routingState.vaultEnabled) {
                        NavigationBarItem(
                            selected = selectedRoute == AppDestinations.VAULT_ROUTE,
                            onClick = { selectedRoute = AppDestinations.VAULT_ROUTE },
                            icon = { Icon(Icons.Filled.Folder, contentDescription = "Vault") },
                            label = { Text("Vault") },
                        )
                    }
                    if (routingState.passkeyEnabled) {
                        NavigationBarItem(
                            selected = selectedRoute == Fido2Destinations.HOME_ROUTE,
                            onClick = { selectedRoute = Fido2Destinations.HOME_ROUTE },
                            icon = { Icon(Icons.Filled.Security, contentDescription = "Passkey Authenticator") },
                            label = { Text("Authenticator") },
                        )
                    }
                    if (com.chimali.core.common.isDebug) {
                        NavigationBarItem(
                            selected = selectedRoute == Fido2Destinations.DEVELOPMENT_ROUTE,
                            onClick = { selectedRoute = Fido2Destinations.DEVELOPMENT_ROUTE },
                            icon = { Icon(Icons.Filled.BugReport, contentDescription = "Dev Tools") },
                            label = { Text("Dev Tools") },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (selectedRoute) {
                AppDestinations.VAULT_ROUTE -> {
                    VaultFeatureScreen(onOpenSettings = onOpenSettings)
                }

                Fido2Destinations.HOME_ROUTE -> {
                    AuthenticatorFeatureScreen(onOpenSettings = onOpenSettings)
                }

                Fido2Destinations.DEVELOPMENT_ROUTE -> {
                    com.chimali.fido2.presentation.ui
                        .DevelopmentToolsScreen()
                }

                else -> {
                    AuthenticatorFeatureScreen(onOpenSettings = onOpenSettings)
                }
            }
        }
    }
}

@Composable
private fun VaultFeatureScreen(onOpenSettings: () -> Unit) {
    val viewModel: VaultViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.processIntent(
            com.chimali.feature.vault.api.VaultIntent
                .LoadItems(),
        )
    }

    VaultListScreen(
        items = state.items,
        labels = emptyList(),
        selectedLabelId = null,
        onItemClick = { },
        onAddClick = { },
        onLabelFilterClick = { },
        onManageLabelsClick = { },
        onOpenSettings = onOpenSettings,
    )
}

@Composable
private fun AuthenticatorFeatureScreen(onOpenSettings: () -> Unit) {
    Fido2RegistrationNavGraph(
        onRegistrationComplete = { },
        onRegistrationCancelled = { },
        onAuthenticationComplete = { },
        onAuthenticationCancelled = { },
        startDestination = Fido2Destinations.HOME_ROUTE,
        onOpenSettings = onOpenSettings,
    )
}
