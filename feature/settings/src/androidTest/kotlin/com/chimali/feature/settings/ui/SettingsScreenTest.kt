package com.chimali.feature.settings.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun retakeOnboardingButtonInvokesCallback() {
        var invoked = false

        composeRule.setContent {
            SettingsScreen(
                vaultEnabled = true,
                passkeyEnabled = false,
                onRetakeOnboarding = { invoked = true },
                onBack = {},
            )
        }

        composeRule.onNodeWithText("Re-take Onboarding").assertIsDisplayed().performClick()

        assertTrue(invoked)
    }

    @Test
    fun screenDisplaysEnabledAndDisabledFeatureSelections() {
        composeRule.setContent {
            SettingsScreen(
                vaultEnabled = true,
                passkeyEnabled = false,
                onRetakeOnboarding = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithText("Vault: Enabled").assertIsDisplayed()
        composeRule.onNodeWithText("Passkey Authenticator: Disabled").assertIsDisplayed()
    }

    @Test
    fun screenDisplaysBothFeaturesEnabled() {
        composeRule.setContent {
            SettingsScreen(
                vaultEnabled = true,
                passkeyEnabled = true,
                onRetakeOnboarding = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithText("Vault: Enabled").assertIsDisplayed()
        composeRule.onNodeWithText("Passkey Authenticator: Enabled").assertIsDisplayed()
    }

    @Test
    fun fidoBleSavedChoiceIsSelectedByDefault() {
        composeRule.setContent {
            SettingsScreen(
                vaultEnabled = true,
                passkeyEnabled = true,
                onRetakeOnboarding = {},
                onBack = {},
                bluetoothTransportSettings =
                    BluetoothTransportSettingsUiState(
                        savedTransportCode = "fido_ble",
                        processTransportCode = "fido_ble",
                        availabilityCode = "selected_not_started",
                        isLoading = false,
                        isSaving = false,
                        readError = null,
                        saveError = null,
                        restartRequired = false,
                    ),
            )
        }

        composeRule.onNodeWithText("FIDO Bluetooth LE (default)").assertIsSelected()
        composeRule.onNodeWithText("Bluetooth HID (Classic)").assertIsNotSelected()
        composeRule.onNodeWithText("Selected; not started").assertIsDisplayed()
    }

    @Test
    fun largeTextCanScrollToTransportStatusAndRemainingSettingsActions() {
        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, fontScale = 2f)) {
                SettingsScreen(
                    vaultEnabled = true,
                    passkeyEnabled = true,
                    onRetakeOnboarding = {},
                    onBack = {},
                    bluetoothTransportSettings =
                        BluetoothTransportSettingsUiState(
                            savedTransportCode = "fido_ble",
                            processTransportCode = "fido_ble",
                            availabilityCode = "selected_not_started",
                            isLoading = false,
                            isSaving = false,
                            readError = null,
                            saveError = null,
                            restartRequired = false,
                        ),
                )
            }
        }

        composeRule.onNodeWithText("Selected; not started").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Re-take Onboarding").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun loadingTransportSettingsDoNotInventAChoice() {
        composeRule.setContent {
            SettingsScreen(
                vaultEnabled = true,
                passkeyEnabled = true,
                onRetakeOnboarding = {},
                onBack = {},
                bluetoothTransportSettings =
                    BluetoothTransportSettingsUiState(
                        savedTransportCode = null,
                        processTransportCode = null,
                        availabilityCode = "loading",
                        isLoading = true,
                        isSaving = false,
                        readError = null,
                        saveError = null,
                        restartRequired = false,
                    ),
            )
        }

        composeRule.onNodeWithText("Loading transport settings…").assertIsDisplayed()
        composeRule.onNodeWithText("Process transport: Not resolved").assertIsDisplayed()
        composeRule.onNodeWithText("Checking availability").assertIsDisplayed()
    }

    @Test
    fun startingTransportIsNotPresentedAsActive() {
        composeRule.setContent {
            SettingsScreen(
                vaultEnabled = true,
                passkeyEnabled = true,
                onRetakeOnboarding = {},
                onBack = {},
                bluetoothTransportSettings =
                    BluetoothTransportSettingsUiState(
                        savedTransportCode = "fido_ble",
                        processTransportCode = "fido_ble",
                        availabilityCode = "starting",
                        isLoading = false,
                        isSaving = false,
                        readError = null,
                        saveError = null,
                        restartRequired = false,
                    ),
            )
        }

        composeRule.onNodeWithText("Selected for this process: FIDO Bluetooth LE").assertIsDisplayed()
        composeRule.onNodeWithText("Starting").assertIsDisplayed()
    }

    @Test
    fun bluetoothTransportOptionsShowSavedAndActiveStateSeparately() {
        var selectedCode: String? = null
        composeRule.setContent {
            SettingsScreen(
                vaultEnabled = true,
                passkeyEnabled = true,
                onRetakeOnboarding = {},
                onBack = {},
                bluetoothTransportSettings =
                    BluetoothTransportSettingsUiState(
                        savedTransportCode = "classic_hid",
                        processTransportCode = "fido_ble",
                        availabilityCode = "ready",
                        isLoading = false,
                        isSaving = false,
                        readError = null,
                        saveError = "write_failed",
                        restartRequired = true,
                    ),
                onSelectTransport = { selectedCode = it },
            )
        }

        composeRule.onNodeWithText("Bluetooth transport").assertIsDisplayed()
        composeRule.onNodeWithText("FIDO Bluetooth LE (default)").assertIsDisplayed()
        composeRule.onNodeWithText("Bluetooth HID (Classic)").assertIsDisplayed()
        composeRule.onNodeWithText("Bluetooth HID (Classic)").assertIsSelected()
        composeRule.onNodeWithText("FIDO Bluetooth LE (default)").assertIsNotSelected()
        composeRule.onNodeWithText("Active in this process: FIDO Bluetooth LE").assertIsDisplayed()
        composeRule.onNodeWithText("A restart is required to use the saved choice.").assertIsDisplayed()
        composeRule
            .onNodeWithText(
                "The choice could not be saved. The active transport is unchanged; retry when ready.",
            ).assertIsDisplayed()
        composeRule.onNodeWithText("Cancel pending change").assertIsDisplayed()
        assertTrue(selectedCode == null)
    }

    @Test
    fun pendingBleSelectionKeepsClassicDisplayedAsActive() {
        composeRule.setContent {
            SettingsScreen(
                vaultEnabled = true,
                passkeyEnabled = true,
                onRetakeOnboarding = {},
                onBack = {},
                bluetoothTransportSettings =
                    BluetoothTransportSettingsUiState(
                        savedTransportCode = "fido_ble",
                        processTransportCode = "classic_hid",
                        availabilityCode = "ready",
                        isLoading = false,
                        isSaving = false,
                        readError = null,
                        saveError = null,
                        restartRequired = true,
                    ),
            )
        }

        composeRule.onNodeWithText("FIDO Bluetooth LE (default)").assertIsSelected()
        composeRule.onNodeWithText("Bluetooth HID (Classic)").assertIsNotSelected()
        composeRule.onNodeWithText("Active in this process: Bluetooth HID (Classic)").assertIsDisplayed()
        composeRule.onNodeWithText("A restart is required to use the saved choice.").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel pending change").assertIsDisplayed()
    }

    @Test
    fun cancelPendingBluetoothChangeInvokesCallback() {
        var canceled = false
        composeRule.setContent {
            SettingsScreen(
                vaultEnabled = true,
                passkeyEnabled = true,
                onRetakeOnboarding = {},
                onBack = {},
                bluetoothTransportSettings =
                    BluetoothTransportSettingsUiState(
                        savedTransportCode = "classic_hid",
                        processTransportCode = "fido_ble",
                        availabilityCode = "ready",
                        isLoading = false,
                        isSaving = false,
                        readError = null,
                        saveError = null,
                        restartRequired = true,
                    ),
                onCancelPendingTransportChange = { canceled = true },
            )
        }

        composeRule.onNodeWithText("Cancel pending change").assertIsDisplayed().performClick()

        assertTrue(canceled)
    }

    @Test
    fun transportReadFailureKeepsSavedChoiceUnresolvedAndOffersRetry() {
        val retried = AtomicBoolean(false)
        composeRule.setContent {
            SettingsScreen(
                vaultEnabled = true,
                passkeyEnabled = true,
                onRetakeOnboarding = {},
                onBack = {},
                bluetoothTransportSettings =
                    BluetoothTransportSettingsUiState(
                        savedTransportCode = null,
                        processTransportCode = null,
                        availabilityCode = "preference_read_failed",
                        isLoading = false,
                        isSaving = false,
                        readError = "read_failed",
                        saveError = null,
                        restartRequired = false,
                    ),
                onRetryTransportSettings = { retried.set(true) },
            )
        }

        composeRule.onNodeWithText("Settings could not be read").assertIsDisplayed()
        composeRule.onNodeWithText("Process transport: Not resolved").assertIsDisplayed()
        composeRule
            .onNodeWithText(
                "Transport settings could not be read. Retry. If the problem continues, " +
                    "restart the app or contact support. " +
                    "This read attempt did not change your saved settings; " +
                    "do not clear app data.",
            ).assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()
        composeRule.waitUntil(timeoutMillis = 1_000) { retried.get() }

        assertTrue(retried.get())
    }

    @Test
    fun unavailableProcessChoiceIsNotLabeledActive() {
        composeRule.setContent {
            SettingsScreen(
                vaultEnabled = true,
                passkeyEnabled = true,
                onRetakeOnboarding = {},
                onBack = {},
                bluetoothTransportSettings =
                    BluetoothTransportSettingsUiState(
                        savedTransportCode = "fido_ble",
                        processTransportCode = "fido_ble",
                        availabilityCode = "unsupported",
                        isLoading = false,
                        isSaving = false,
                        readError = null,
                        saveError = null,
                        restartRequired = false,
                    ),
            )
        }

        composeRule.onNodeWithText("Selected for this process: FIDO Bluetooth LE").assertIsDisplayed()
        composeRule.onNodeWithText("Not supported on this device").assertIsDisplayed()
        composeRule.onNodeWithText("Active in this process: FIDO Bluetooth LE").assertDoesNotExist()
    }
}
