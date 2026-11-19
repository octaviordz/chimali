package com.chimali.fido2.presentation.management

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelStore
import com.chimali.core.common.result.Outcome
import com.chimali.core.domain.model.CredentialKeyDescriptor
import com.chimali.core.domain.time.TimeProvider
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.RpId
import com.chimali.core.domain.valueobject.UserId
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.usecase.DeleteCredentialUseCase
import com.chimali.fido2.domain.usecase.GetAllCredentialsUseCase
import com.chimali.fido2.domain.usecase.SearchCredentialsUseCase
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import java.security.PublicKey
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CredentialListScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val getAllCredentialsUseCase: GetAllCredentialsUseCase = mockk()
    private val searchCredentialsUseCase: SearchCredentialsUseCase = mockk()
    private val deleteCredentialUseCase: DeleteCredentialUseCase = mockk()
    private val credentialFixtures = mutableListOf<PasskeyCredential>()
    private val publicKeyFixtures = mutableListOf<PublicKey>()
    private val viewModels = mutableListOf<CredentialManagementViewModel>()
    private val viewModelStore = ViewModelStore()

    @After
    fun clearCredentialFixtures() {
        viewModels.forEach {
            it.onIntent(CredentialManagementIntent.ScreenStopped)
        }
        viewModelStore.clear()
        credentialFixtures.forEach { credential ->
            credential.eraseSensitiveText()
            credential.aaguid.fill(0)
            assertTrue(credential.rpId.all { it == '\u0000' })
            assertTrue(credential.userId.all { it == '\u0000' })
            assertTrue(credential.userName.all { it == '\u0000' })
            assertTrue(credential.userDisplayName.all { it == '\u0000' })
            assertTrue(credential.credentialId.all { it == 0.toByte() })
            assertTrue(credential.aaguid.all { it == 0.toByte() })
        }
        viewModels.clear()
        credentialFixtures.clear()
        publicKeyFixtures.forEach { clearMocks(it) }
        publicKeyFixtures.clear()
        clearMocks(getAllCredentialsUseCase, searchCredentialsUseCase, deleteCredentialUseCase)
    }

    private fun setupViewModel(): CredentialManagementViewModel =
        CredentialManagementViewModel(
            getAllCredentialsUseCase,
            searchCredentialsUseCase,
            deleteCredentialUseCase,
            null,
        ).also { viewModel ->
            viewModels += viewModel
            viewModelStore.put("credential-list-${viewModels.size}", viewModel)
        }

    private fun createCredential(): PasskeyCredential {
        val credentialIdBytes = byteArrayOf(1, 2, 3)
        val idBytes = ByteArray(CredentialId.MIN_CREDENTIAL_ID_BYTES)
        val aaguid = ByteArray(16)
        val id =
            try {
                CredentialId.fromByteArray(idBytes)
            } finally {
                idBytes.fill(0)
            }
        val publicKey = mockk<PublicKey>(relaxed = true)
        publicKeyFixtures += publicKey
        return PasskeyCredential(
            id = id,
            rpId = RpId("example.com"),
            userId = UserId("test_user_id"),
            userName = "testuser",
            userDisplayName = "Test User",
            publicKey = publicKey,
            signCount = 0L,
            createdAt = TimeProvider().now(),
            lastUsedAt = TimeProvider().now(),
            aaguid = aaguid,
            credentialId = credentialIdBytes,
            derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
        ).also(credentialFixtures::add)
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun screen_displaysEmptyState_whenNoCredentials() {
        coEvery { getAllCredentialsUseCase(any(), any()) } returns Outcome.Success(emptyList())

        val viewModel = setupViewModel()

        composeTestRule.setContent {
            CredentialListScreen(viewModel = viewModel, onNavigateUp = {})
        }

        composeTestRule.onNodeWithText("No passkeys found.").assertIsDisplayed()
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun screen_displaysCredentials_whenAvailable() {
        val mockCredential = createCredential()

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

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun screen_showsDeleteDialog_whenDeleteIconClicked() {
        val mockCredential = createCredential()

        coEvery { getAllCredentialsUseCase(any(), any()) } returns Outcome.Success(listOf(mockCredential))

        val viewModel = setupViewModel()

        composeTestRule.setContent {
            CredentialListScreen(viewModel = viewModel, onNavigateUp = {})
        }

        viewModel.onIntent(CredentialManagementIntent.ShowDeleteDialog(mockCredential))
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Delete Passkey?").assertIsDisplayed()
        composeTestRule
            .onNodeWithText(
                "This will permanently remove the passkey for testuser from this device.",
            ).assertIsDisplayed()

        composeTestRule.onNodeWithText("Delete").performClick()
        composeTestRule.onNodeWithText("Verify your identity to delete this passkey").assertIsDisplayed()
    }
}
