package com.chimali.feature.onboarding.presentation.viewmodel

import com.chimali.core.common.datastore.UserPreferences
import com.chimali.core.common.datastore.createUserPreferencesDataStore
import com.chimali.feature.onboarding.presentation.model.OnboardingStep
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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

    @Test
    fun `finishOnboarding stores authenticator as default when only passkey is enabled`() =
        runTest {
            viewModel.onContinueFromIntro()
            viewModel.onPasskeyToggled(true)

            assertTrue(viewModel.finishOnboarding())

            val storedPreferences = dataStore.data.first()
            assertFalse(storedPreferences.vaultFeatureEnabled)
            assertTrue(storedPreferences.passkeyAuthenticatorFeatureEnabled)
            assertEquals("fido2/home", storedPreferences.lastVisitedMainScreen)
        }

    @Test
    fun `finishOnboarding rejects continuing without a feature`() =
        runTest {
            viewModel.onContinueFromIntro()

            assertFalse(viewModel.finishOnboarding())
            assertEquals("Select at least one feature to continue.", viewModel.state.value.errorMessage)
            assertFalse(viewModel.state.value.isSaving)
        }

    @Test
    fun `back to intro clears selection error`() =
        runTest {
            viewModel.onContinueFromIntro()
            assertFalse(viewModel.finishOnboarding())
            assertEquals("Select at least one feature to continue.", viewModel.state.value.errorMessage)

            viewModel.onBackToIntro()

            assertEquals(OnboardingStep.Intro, viewModel.state.value.step)
            assertEquals(null, viewModel.state.value.errorMessage)
        }

    @Test
    fun `finishOnboarding reports storage errors and clears saving state`() =
        runTest {
            viewModel = OnboardingViewModel(FailingDataStore())
            viewModel.onVaultToggled(true)

            assertFalse(viewModel.finishOnboarding())
            assertEquals("disk is unavailable", viewModel.state.value.errorMessage)
            assertFalse(viewModel.state.value.isSaving)
            assertTrue(viewModel.state.value.vaultSelected)
        }

    private class FailingDataStore : androidx.datastore.core.DataStore<UserPreferences> {
        override val data: Flow<UserPreferences> = flowOf(UserPreferences())

        override suspend fun updateData(transform: suspend (t: UserPreferences) -> UserPreferences): UserPreferences =
            throw IOException("disk is unavailable")
    }
}
