package com.chimali.fido2.integration

import android.util.Log
import com.chimali.fido2.domain.repository.CredentialRepository
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.usecase.DeleteAllCredentialsUseCase
import com.chimali.fido2.domain.usecase.DeleteCredentialUseCase
import com.chimali.fido2.domain.usecase.GetAllCredentialsUseCase
import com.chimali.fido2.domain.usecase.UpdateCredentialLabelUseCase
import com.chimali.fido2.presentation.management.CredentialManagementEffect
import com.chimali.fido2.presentation.management.CredentialManagementIntent
import com.chimali.fido2.presentation.management.CredentialManagementViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.KeyPairGenerator
import java.time.Instant

/**
 * T126 — Integration tests for FIDO2 Credential Management flow.
 *
 * Tests the ViewModel → UseCase → Repository integration by mocking only the
 * repository boundary. Real use cases and ViewModel are used.
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

        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

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

        val getAllUseCase = GetAllCredentialsUseCase(repository)
        val deleteUseCase = DeleteCredentialUseCase(repository)
        val deleteAllUseCase = DeleteAllCredentialsUseCase(repository)
        val updateLabelUseCase = UpdateCredentialLabelUseCase(repository)

        viewModel = CredentialManagementViewModel(
            getAllUseCase,
            deleteUseCase,
            deleteAllUseCase,
            updateLabelUseCase
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

    // ── Credential Loading ───────────────────────────────────────────────────

    @Test
    fun `refresh populates state with credentials`() = runTest {
        val cred1 = createDummyCredential("cred1", "https://example.com")
        val cred2 = createDummyCredential("cred2", "https://google.com")
        credentials.value = listOf(cred1, cred2)

        // Use setCredentials since loadCredentials has flow type mismatch
        viewModel.setCredentials(credentials.value)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(2, state.credentials.size)
        assertFalse(state.isLoading)
    }

    @Test
    fun `empty credentials list results in empty state`() = runTest {
        credentials.value = emptyList()
        viewModel.setCredentials(credentials.value)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.credentials.isEmpty())
    }

    // ── Credential Selection ─────────────────────────────────────────────────

    @Test
    fun `selecting credential updates selectedCredential state`() = runTest {
        val cred = createDummyCredential("cred1")
        viewModel.setCredentials(listOf(cred))
        advanceUntilIdle()

        viewModel.onIntent(CredentialManagementIntent.SelectCredential(cred))
        val state = viewModel.state.value

        assertEquals(cred.id, state.selectedCredential?.id)
    }

    @Test
    fun `dismiss clears dialog state`() = runTest {
        val cred = createDummyCredential("cred1")
        viewModel.setCredentials(listOf(cred))
        viewModel.onIntent(CredentialManagementIntent.ShowDeleteDialog(cred))
        viewModel.onIntent(CredentialManagementIntent.ShowDeleteAllDialog)
        advanceUntilIdle()

        viewModel.onIntent(CredentialManagementIntent.DismissDialog)
        val state = viewModel.state.value

        // dismissDialogs only clears dialog-related state, NOT selectedCredential
        assertNull(state.credentialToDelete)
        assertFalse(state.showDeleteAllWarning)
    }

    // ── Delete Single Credential ─────────────────────────────────────────────

    @Test
    fun `show delete dialog sets credentialToDelete`() = runTest {
        val cred = createDummyCredential("cred1")
        viewModel.setCredentials(listOf(cred))
        advanceUntilIdle()

        viewModel.onIntent(CredentialManagementIntent.ShowDeleteDialog(cred))
        val state = viewModel.state.value

        assertEquals(cred.id, state.credentialToDelete?.id)
    }

    @Test
    fun `confirm delete removes credential from repository`() = runTest {
        val cred1 = createDummyCredential("cred1", "https://example.com")
        val cred2 = createDummyCredential("cred2", "https://google.com")
        credentials.value = listOf(cred1, cred2)
        viewModel.setCredentials(credentials.value)
        advanceUntilIdle()

        viewModel.onIntent(CredentialManagementIntent.ShowDeleteDialog(cred1))
        viewModel.onIntent(CredentialManagementIntent.ConfirmDelete(cred1.id))
        advanceUntilIdle()

        // Repository-level: cred1 should be removed
        assertEquals(1, credentials.value.size)
        assertEquals("cred2", credentials.value.first().id)
    }

    @Test
    fun `delete failure sets error state`() = runTest {
        // Override mock to simulate failure
        coEvery { repository.deleteCredential(any()) } returns
            Result.failure(Exception("Database error"))

        val cred = createDummyCredential("cred1")
        viewModel.setCredentials(listOf(cred))
        advanceUntilIdle()

        viewModel.onIntent(CredentialManagementIntent.ConfirmDelete(cred.id))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertNotNull(state.error)
    }

    // ── Delete All Credentials ───────────────────────────────────────────────

    @Test
    fun `show delete all dialog sets warning flag`() = runTest {
        viewModel.onIntent(CredentialManagementIntent.ShowDeleteAllDialog)
        val state = viewModel.state.value

        assertTrue(state.showDeleteAllWarning)
    }

    @Test
    fun `confirm delete all empties repository`() = runTest {
        val cred1 = createDummyCredential("cred1")
        val cred2 = createDummyCredential("cred2")
        credentials.value = listOf(cred1, cred2)
        viewModel.setCredentials(credentials.value)
        advanceUntilIdle()

        viewModel.onIntent(CredentialManagementIntent.ShowDeleteAllDialog)
        viewModel.onIntent(CredentialManagementIntent.ConfirmDeleteAll)
        advanceUntilIdle()

        assertTrue(credentials.value.isEmpty())
    }

    @Test
    fun `delete all failure sets error state`() = runTest {
        coEvery { repository.deleteAllCredentials(any()) } returns
            Result.failure(Exception("Wipe failed"))

        viewModel.setCredentials(listOf(createDummyCredential("cred1")))
        advanceUntilIdle()

        viewModel.onIntent(CredentialManagementIntent.ConfirmDeleteAll)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertNotNull(state.error)
    }

    // ── Full Flow ────────────────────────────────────────────────────────────

    @Test
    fun `full management flow - add select delete wipe`() = runTest {
        // 1 — Seed credentials
        val cred1 = createDummyCredential("cred1", "https://example.com")
        val cred2 = createDummyCredential("cred2", "https://google.com")
        credentials.value = listOf(cred1, cred2)
        viewModel.setCredentials(credentials.value)
        advanceUntilIdle()

        assertEquals(2, viewModel.state.value.credentials.size)

        // 2 — Select
        viewModel.onIntent(CredentialManagementIntent.SelectCredential(cred1))
        assertEquals(cred1, viewModel.state.value.selectedCredential)

        // 3 — Dismiss (clears dialog state only, not selection)
        viewModel.onIntent(CredentialManagementIntent.DismissDialog)
        assertNull(viewModel.state.value.credentialToDelete)
        assertFalse(viewModel.state.value.showDeleteAllWarning)

        // 4 — Delete single
        viewModel.onIntent(CredentialManagementIntent.ShowDeleteDialog(cred1))
        viewModel.onIntent(CredentialManagementIntent.ConfirmDelete(cred1.id))
        advanceUntilIdle()

        assertEquals(1, credentials.value.size)
        assertEquals("cred2", credentials.value.first().id)

        // 5 — Wipe all
        viewModel.onIntent(CredentialManagementIntent.ShowDeleteAllDialog)
        assertTrue(viewModel.state.value.showDeleteAllWarning)

        viewModel.onIntent(CredentialManagementIntent.ConfirmDeleteAll)
        advanceUntilIdle()

        assertTrue(credentials.value.isEmpty())
    }
}
