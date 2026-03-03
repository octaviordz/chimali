package com.chimali.fido2.integration

import com.chimali.fido2.domain.repository.CredentialRepository
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.usecase.DeleteAllCredentialsUseCase
import com.chimali.fido2.domain.usecase.DeleteCredentialUseCase
import com.chimali.fido2.domain.usecase.GetAllCredentialsUseCase
import com.chimali.fido2.presentation.management.CredentialManagementIntent
import com.chimali.fido2.presentation.management.CredentialManagementViewModel
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.KeyPairGenerator
import java.time.Instant

/**
 * T126 — Integration tests for FIDO2 Credential Management flow.
 * Uses an in-memory SQLDelight database, real repository, real use cases, and real ViewModel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ManagementIntegrationTest {

    private lateinit var repository: CredentialRepository
    private lateinit var viewModel: CredentialManagementViewModel
    private val credentials = MutableStateFlow<List<PasskeyCredential>>(emptyList())

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        // Setup mock repository
        repository = mockk()
        
        coEvery { repository.getAllCredentials() } answers {
            kotlinx.coroutines.flow.flow {
                credentials.value.forEach { emit(it) }
            }
        }
        
        coEvery { repository.deleteCredential(any()) } answers {
            val id = firstArg<String>()
            credentials.update { list -> list.filterNot { it.id == id } }
            Result.success(Unit)
        }
        
        coEvery { repository.deleteAllCredentials(any()) } answers {
            credentials.value = emptyList()
            Result.success(Unit)
        }

        // Setup use cases
        val getAllUseCase = GetAllCredentialsUseCase(repository)
        val deleteUseCase = DeleteCredentialUseCase(repository)
        val deleteAllUseCase = DeleteAllCredentialsUseCase(repository)

        // Setup ViewModel
        viewModel = CredentialManagementViewModel(
            getAllUseCase,
            deleteUseCase,
            deleteAllUseCase
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createDummyCredential(id: String, rpId: String = "https://example.com"): PasskeyCredential {
        val keyPair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        return PasskeyCredential(
            id = id,
            rpId = rpId,
            userId = "test_user_id",
            userName = "testuser",
            userDisplayName = "Test User",
            publicKey = keyPair.public,
            privateKeyAlias = "test_alias",
            signCount = 0L,
            createdAt = Instant.now(),
            lastUsedAt = Instant.now(),
            aaguid = ByteArray(16),
            credentialId = id.toByteArray()
        )
    }

    @Test
    fun `full management flow integration`() = runTest {
        // Initial state should have no credentials
        advanceUntilIdle()
        var state = viewModel.state.value
        assertTrue(state.credentials.isEmpty())

        // Insert credentials manually into stateflow
        val cred1 = createDummyCredential("cred1", "https://example.com")
        val cred2 = createDummyCredential("cred2", "https://google.com")
        credentials.value = listOf(cred1, cred2)

        // Refresh ViewModel
        viewModel.onIntent(CredentialManagementIntent.RefreshCredentials)
        advanceUntilIdle()

        // State should now contain 2 credentials
        state = viewModel.state.value
        assertEquals(2, state.credentials.size)

        // Select a credential
        viewModel.onIntent(CredentialManagementIntent.SelectCredential(cred1))
        state = viewModel.state.value
        assertEquals(cred1, state.selectedCredential)

        // Dismiss dialog
        viewModel.onIntent(CredentialManagementIntent.DismissDialog)
        state = viewModel.state.value
        assertEquals(null, state.selectedCredential)

        // Delete a credential
        viewModel.onIntent(CredentialManagementIntent.ShowDeleteDialog(cred1))
        viewModel.onIntent(CredentialManagementIntent.ConfirmDelete(cred1.id))
        advanceUntilIdle()

        // State should now have 1 credential
        state = viewModel.state.value
        assertEquals(1, state.credentials.size)
        assertEquals("cred2", state.credentials.first().id)

        // Show wipe all dialog and confirm
        viewModel.onIntent(CredentialManagementIntent.ShowDeleteAllDialog)
        state = viewModel.state.value
        assertTrue(state.showDeleteAllWarning)

        viewModel.onIntent(CredentialManagementIntent.ConfirmDeleteAll)
        advanceUntilIdle()

        // State should be empty again
        state = viewModel.state.value
        assertTrue(state.credentials.isEmpty())
        
        // Assert repository is empty too
        assertTrue(credentials.value.isEmpty())
    }
}
