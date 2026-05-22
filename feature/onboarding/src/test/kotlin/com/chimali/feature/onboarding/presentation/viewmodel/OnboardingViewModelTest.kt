package com.chimali.feature.onboarding.presentation.viewmodel

import com.chimali.core.common.datastore.UserPreferences
import com.chimali.core.common.datastore.createUserPreferencesDataStore
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var dataStore: androidx.datastore.core.DataStore<UserPreferences>
    private lateinit var viewModel: OnboardingViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val tempFile = Files.createTempDirectory("onboarding-viewmodel").resolve("user_preferences.pb")
        dataStore =
            createUserPreferencesDataStore(
                producePath = { tempFile.toString() },
            )
        viewModel = OnboardingViewModel(dataStore)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state disables continue until a feature is selected`() =
        runTest {
            val state = viewModel.state.value

            assertFalse(state.canContinue)
            assertFalse(state.vaultSelected)
            assertFalse(state.passkeySelected)
        }

    @Test
    fun `finishOnboarding persists selected features and default main screen`() =
        runTest {
            viewModel.onContinueFromIntro()
            viewModel.onVaultToggled(true)

            assertTrue(viewModel.state.value.canContinue)

            val saved = viewModel.finishOnboarding()
            assertTrue(saved)

            val storedPreferences = dataStore.data.first()
            assertTrue(storedPreferences.onboardingCompleted)
            assertTrue(storedPreferences.vaultFeatureEnabled)
            assertFalse(storedPreferences.passkeyAuthenticatorFeatureEnabled)
            assertEquals("vault/home", storedPreferences.lastVisitedMainScreen)
        }

    @Test
    fun `finishOnboarding stores authenticator as default when both features are enabled`() =
        runTest {
            viewModel.onContinueFromIntro()
            viewModel.onVaultToggled(true)
            viewModel.onPasskeyToggled(true)

            val saved = viewModel.finishOnboarding()
            assertTrue(saved)

            val storedPreferences = dataStore.data.first()
            assertEquals("fido2/home", storedPreferences.lastVisitedMainScreen)
        }
}
