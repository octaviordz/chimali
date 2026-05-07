package com.chimali.fido2.presentation.management

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.chimali.core.common.result.Outcome
import com.chimali.core.domain.time.TimeProvider
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.RpId
import com.chimali.core.domain.valueobject.UserId
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.usecase.DeleteCredentialUseCase
import com.chimali.fido2.domain.usecase.GetAllCredentialsUseCase
import com.chimali.fido2.domain.usecase.SearchCredentialsUseCase
import io.mockk.coEvery
import io.mockk.mockk
import java.security.PublicKey
import org.junit.Rule
import org.junit.Test

class CredentialListScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val getAllCredentialsUseCase: GetAllCredentialsUseCase = mockk()
    private val searchCredentialsUseCase: SearchCredentialsUseCase = mockk()
    private val deleteCredentialUseCase: DeleteCredentialUseCase = mockk()

    private fun setupViewModel(): CredentialManagementViewModel =
        CredentialManagementViewModel(
            getAllCredentialsUseCase,
            searchCredentialsUseCase,
            deleteCredentialUseCase,
            null,
        )

    @Test
    fun screen_displaysEmptyState_whenNoCredentials() {
        coEvery { getAllCredentialsUseCase(any(), any()) } returns Outcome.Success(emptyList())

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
                id = CredentialId.fromEncoded("mock_id"),
                rpId = RpId("example.com"),
                userId = UserId("test_user_id"),
                userName = "testuser",
                userDisplayName = "Test User",
                publicKey = mockPublicKey,
                privateKeyAlias = "test_alias",
                signCount = 0L,
                createdAt = TimeProvider().now(),
                lastUsedAt = TimeProvider().now(),
                aaguid = ByteArray(16),
                credentialId = byteArrayOf(1, 2, 3),
            )

        coEvery { getAllCredentialsUseCase(any(), any()) } returns Outcome.Success(listOf(mockCredential))

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
                id = CredentialId.fromEncoded("mock_id"),
                rpId = RpId("example.com"),
                userId = UserId("test_user_id"),
                userName = "testuser",
                userDisplayName = "Test User",
                publicKey = mockPublicKey,
                privateKeyAlias = "test_alias",
                signCount = 0L,
                createdAt = TimeProvider().now(),
                lastUsedAt = TimeProvider().now(),
                aaguid = ByteArray(16),
                credentialId = byteArrayOf(1, 2, 3),
            )

        coEvery { getAllCredentialsUseCase(any(), any()) } returns Outcome.Success(listOf(mockCredential))

        val viewModel = setupViewModel()

        composeTestRule.setContent {
            CredentialListScreen(viewModel = viewModel, onNavigateUp = {})
        }

        composeTestRule.onNodeWithContentDescription("Delete credential").performClick()

        composeTestRule.onNodeWithText("Delete Passkey?").assertIsDisplayed()
        composeTestRule
            .onNodeWithText(
                "Are you sure you want to delete the passkey for testuser? This cannot be undone.",
            ).assertIsDisplayed()
    }
}
