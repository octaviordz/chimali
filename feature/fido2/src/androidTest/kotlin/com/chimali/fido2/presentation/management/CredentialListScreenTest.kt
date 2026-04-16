package com.chimali.fido2.presentation.management

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.usecase.DeleteAllCredentialsUseCase
import com.chimali.fido2.domain.usecase.DeleteCredentialUseCase
import com.chimali.fido2.domain.usecase.GetAllCredentialsUseCase
import com.chimali.fido2.domain.usecase.UpdateCredentialLabelUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import java.security.PublicKey
import java.time.Instant

class CredentialListScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val getAllCredentialsUseCase: GetAllCredentialsUseCase = mockk()
    private val deleteCredentialUseCase: DeleteCredentialUseCase = mockk()
    private val deleteAllCredentialsUseCase: DeleteAllCredentialsUseCase = mockk()
    private val updateCredentialLabelUseCase: UpdateCredentialLabelUseCase = mockk()

    private fun setupViewModel(): CredentialManagementViewModel {
        return CredentialManagementViewModel(
            getAllCredentialsUseCase,
            deleteCredentialUseCase,
            deleteAllCredentialsUseCase,
            updateCredentialLabelUseCase,
        )
    }

    @Test
    fun screen_displaysEmptyState_whenNoCredentials() {
        coEvery { getAllCredentialsUseCase() } returns emptyFlow()

        val viewModel = setupViewModel()

        composeTestRule.setContent {
            CredentialListScreen(viewModel = viewModel, onNavigateUp = {})
        }

        composeTestRule.onNodeWithText("No passkeys found.").assertIsDisplayed()
    }

    @Test
    fun screen_displaysCredentials_whenAvailable() {
        val mockPublicKey = mockk<PublicKey>(relaxed = true)
        val mockCredential =
            PasskeyCredential(
                id = "mock_id",
                rpId = "example.com",
                userId = "test_user_id",
                userName = "testuser",
                userDisplayName = "Test User",
                publicKey = mockPublicKey,
                privateKeyAlias = "test_alias",
                signCount = 0L,
                createdAt = Instant.now(),
                lastUsedAt = Instant.now(),
                aaguid = ByteArray(16),
                credentialId = byteArrayOf(1, 2, 3),
            )

        coEvery { getAllCredentialsUseCase() } returns flowOf(mockCredential)

        val viewModel = setupViewModel()

        composeTestRule.setContent {
            CredentialListScreen(viewModel = viewModel, onNavigateUp = {})
        }

        // Wait for coroutines and UI to settle
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("testuser").assertIsDisplayed()
        composeTestRule.onNodeWithText("example.com").assertIsDisplayed()
    }

    @Test
    fun screen_showsDeleteDialog_whenDeleteIconClicked() {
        val mockPublicKey = mockk<PublicKey>(relaxed = true)
        val mockCredential =
            PasskeyCredential(
                id = "mock_id",
                rpId = "example.com",
                userId = "test_user_id",
                userName = "testuser",
                userDisplayName = "Test User",
                publicKey = mockPublicKey,
                privateKeyAlias = "test_alias",
                signCount = 0L,
                createdAt = Instant.now(),
                lastUsedAt = Instant.now(),
                aaguid = ByteArray(16),
                credentialId = byteArrayOf(1, 2, 3),
            )

        coEvery { getAllCredentialsUseCase() } returns flowOf(mockCredential)

        val viewModel = setupViewModel()

        composeTestRule.setContent {
            CredentialListScreen(viewModel = viewModel, onNavigateUp = {})
        }

        composeTestRule.onNodeWithContentDescription("Delete credential").performClick()

        composeTestRule.onNodeWithText("Delete Passkey?").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "Are you sure you want to delete the passkey for testuser? This cannot be undone.",
        ).assertIsDisplayed()
    }
}
