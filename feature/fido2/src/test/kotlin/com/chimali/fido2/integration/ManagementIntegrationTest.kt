package com.chimali.fido2.integration

import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.domain.time.TimeProvider
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.RpId
import com.chimali.core.domain.valueobject.UserId
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.repository.CredentialRepository
import com.chimali.fido2.domain.usecase.DeleteAllCredentialsUseCase
import com.chimali.fido2.domain.usecase.DeleteCredentialUseCase
import com.chimali.fido2.domain.usecase.GetAllCredentialsUseCase
import com.chimali.fido2.domain.usecase.SearchCredentialsUseCase
import com.chimali.fido2.presentation.management.CredentialManagementIntent
import com.chimali.fido2.presentation.management.CredentialManagementViewModel
import io.mockk.coEvery
import io.mockk.mockk
import java.security.KeyPairGenerator
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach

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

    private val testDispatcher = UnconfinedTestDispatcher()

    private companion object {
        private const val EC_KEY_SIZE_256 = 256
        private const val AAGUID_SIZE_16 = 16
        private const val EXPECTED_CREDENTIALS_2 = 2
    }

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        repository = mockk()

        // getPagedCredentials returns Outcome.Success(List<PasskeyCredential>)
        coEvery { repository.getPagedCredentials(any(), any()) } answers {
            val limit = firstArg<Long>()
            val offset = secondArg<Long>()
            val list = credentials.value.drop(offset.toInt()).take(limit.toInt())
            Outcome.Success(list)
        }

        // searchCredentials returns empty by default
        coEvery { repository.searchCredentials(any()) } returns emptyFlow()

        coEvery { repository.deleteCredential(any()) } answers {
            val arg = firstArg<Any>()
            val targetEncoded =
                if (arg is String) {
                    arg
                } else {
                    (arg as com.chimali.core.domain.valueobject.CredentialId).encoded
                }
            credentials.update { list ->
                val filtered = list.filterNot { it.id.encoded == targetEncoded }
                filtered
            }
            Outcome.Success(Unit)
        }

        // deleteAllCredentials wipes in-memory list
        coEvery { repository.deleteAllCredentials(any()) } answers {
            credentials.value = emptyList()
            Outcome.Success(Unit)
        }

        val getAllUseCase = GetAllCredentialsUseCase(repository)
        val searchUseCase = SearchCredentialsUseCase(repository)
        val deleteUseCase = DeleteCredentialUseCase(repository)
        val deleteAllUseCase = DeleteAllCredentialsUseCase(repository)

        viewModel =
            CredentialManagementViewModel(
                getAllUseCase,
                searchUseCase,
                deleteUseCase,
                deleteAllUseCase,
            )
    }

    @AfterEach
    fun tearDown() {
        io.mockk.clearMocks(repository)
        Dispatchers.resetMain()
    }

    private fun createDummyCredential(
        idString: String,
        rpId: String = "https://example.com",
    ): PasskeyCredential {
        val credId = CredentialId.fromEncoded(idString)
        val keyPair = KeyPairGenerator.getInstance("EC").apply { initialize(EC_KEY_SIZE_256) }.generateKeyPair()
        return PasskeyCredential(
            id = credId,
            rpId = RpId(rpId),
            userId = UserId("test_user_id"),
            userName = "testuser",
            userDisplayName = "Test User",
            publicKey = keyPair.public,
            privateKeyAlias = "test_alias",
            signCount = 0L,
            createdAt = TimeProvider().now(),
            lastUsedAt = TimeProvider().now(),
            aaguid = ByteArray(AAGUID_SIZE_16),
            credentialId = credId.toByteArray(),
        )
    }

    // ── Credential Loading ───────────────────────────────────────────────────

    @Test
    fun `setCredentials populates state with credentials`() =
        runTest {
            val cred1 = createDummyCredential("dGVzdF9jcmVkZW50aWFsXzAx", "https://example.com")
            val cred2 = createDummyCredential("dGVzdF9jcmVkZW50aWFsXzAy", "https://google.com")

            viewModel.setCredentials(listOf(cred1, cred2))

            val state = viewModel.state.value
            assertEquals(EXPECTED_CREDENTIALS_2, state.credentials.size)
            assertFalse(state.isLoading)
        }

    @Test
    fun `empty credentials list results in empty state`() =
        runTest {
            viewModel.setCredentials(emptyList())

            val state = viewModel.state.value
            assertTrue(state.credentials.isEmpty())
        }

    // ── Credential Selection ─────────────────────────────────────────────────

    @Test
    fun `selecting credential updates selectedCredential state`() =
        runTest {
            val cred = createDummyCredential("dGVzdF9jcmVkZW50aWFsXzAx")
            viewModel.setCredentials(listOf(cred))

            viewModel.onIntent(CredentialManagementIntent.SelectCredential(cred))
            val state = viewModel.state.value

            assertEquals(cred.id, state.selectedCredential?.id)
        }

    @Test
    fun `dismiss clears dialog state`() =
        runTest {
            val cred = createDummyCredential("dGVzdF9jcmVkZW50aWFsXzAx")
            viewModel.setCredentials(listOf(cred))
            viewModel.onIntent(CredentialManagementIntent.ShowDeleteDialog(cred))
            viewModel.onIntent(CredentialManagementIntent.ShowDeleteAllDialog)
            advanceUntilIdle()

            viewModel.onIntent(CredentialManagementIntent.DismissDialog)
            val state = viewModel.state.value

            assertNull(state.credentialToDelete)
            assertFalse(state.showDeleteAllWarning)
        }

    // ── Delete Single Credential ─────────────────────────────────────────────

    @Test
    fun `show delete dialog sets credentialToDelete`() =
        runTest {
            val cred = createDummyCredential("dGVzdF9jcmVkZW50aWFsXzAx")
            viewModel.setCredentials(listOf(cred))

            viewModel.onIntent(CredentialManagementIntent.ShowDeleteDialog(cred))
            val state = viewModel.state.value

            assertEquals(cred.id, state.credentialToDelete?.id)
        }

    @Test
    fun `confirm delete removes credential from repository`() =
        runTest {
            val cred1 = createDummyCredential("dGVzdF9jcmVkZW50aWFsXzAx", "https://example.com")
            val cred2 = createDummyCredential("dGVzdF9jcmVkZW50aWFsXzAy", "https://google.com")
            credentials.value = listOf(cred1, cred2)
            viewModel.setCredentials(credentials.value)

            viewModel.onIntent(CredentialManagementIntent.ShowDeleteDialog(cred1))
            viewModel.onIntent(CredentialManagementIntent.ConfirmDelete(cred1.id))
            advanceUntilIdle()

            assertEquals(1, credentials.value.size)
            assertEquals(
                "dGVzdF9jcmVkZW50aWFsXzAy",
                credentials.value
                    .first()
                    .id.encoded,
            )
        }

    @Test
    fun `delete failure sets error state`() =
        runTest(UnconfinedTestDispatcher()) {
            coEvery { repository.deleteCredential(any()) } returns
                Outcome.Error(DomainError.UnknownError("Database error"))

            val cred = createDummyCredential("dGVzdF9jcmVkZW50aWFsXzAx")
            viewModel.setCredentials(listOf(cred))

            viewModel.onIntent(CredentialManagementIntent.ConfirmDelete(cred.id))

            assertNotNull(viewModel.state.value.error)
        }

    // ── Delete All Credentials ───────────────────────────────────────────────

    @Test
    fun `show delete all dialog sets warning flag`() =
        runTest {
            viewModel.onIntent(CredentialManagementIntent.ShowDeleteAllDialog)
            assertTrue(viewModel.state.value.showDeleteAllWarning)
        }

    @Test
    fun `confirm delete all empties repository`() =
        runTest {
            val cred1 = createDummyCredential("dGVzdF9jcmVkZW50aWFsXzAx")
            val cred2 = createDummyCredential("dGVzdF9jcmVkZW50aWFsXzAy")
            credentials.value = listOf(cred1, cred2)
            viewModel.setCredentials(credentials.value)

            viewModel.onIntent(CredentialManagementIntent.ShowDeleteAllDialog)
            viewModel.onIntent(CredentialManagementIntent.ConfirmDeleteAll)
            advanceUntilIdle()

            assertTrue(credentials.value.isEmpty())
        }

    @Test
    fun `delete all failure sets error state`() =
        runTest(UnconfinedTestDispatcher()) {
            coEvery { repository.deleteAllCredentials(any()) } returns
                Outcome.Error(DomainError.UnknownError("Wipe failed"))

            viewModel.setCredentials(listOf(createDummyCredential("dGVzdF9jcmVkZW50aWFsXzAx")))

            viewModel.onIntent(CredentialManagementIntent.ConfirmDeleteAll)

            assertNotNull(viewModel.state.value.error)
        }

    // ── Full Flow ────────────────────────────────────────────────────────────

    @Test
    fun `full management flow - add select delete wipe`() =
        runTest {
            val cred1 = createDummyCredential("dGVzdF9jcmVkZW50aWFsXzAx", "https://example.com")
            val cred2 = createDummyCredential("dGVzdF9jcmVkZW50aWFsXzAy", "https://google.com")
            credentials.value = listOf(cred1, cred2)
            viewModel.setCredentials(credentials.value)
            advanceUntilIdle()

            assertEquals(EXPECTED_CREDENTIALS_2, viewModel.state.value.credentials.size)

            // Select
            viewModel.onIntent(CredentialManagementIntent.SelectCredential(cred1))
            assertEquals(cred1, viewModel.state.value.selectedCredential)

            // Dismiss (clears dialogs only)
            viewModel.onIntent(CredentialManagementIntent.DismissDialog)
            assertNull(viewModel.state.value.credentialToDelete)
            assertFalse(viewModel.state.value.showDeleteAllWarning)

            // Delete single
            viewModel.onIntent(CredentialManagementIntent.ShowDeleteDialog(cred1))
            viewModel.onIntent(CredentialManagementIntent.ConfirmDelete(cred1.id))
            advanceUntilIdle()
            assertEquals(1, credentials.value.size)
            assertEquals(
                "dGVzdF9jcmVkZW50aWFsXzAy",
                credentials.value
                    .first()
                    .id.encoded,
            )

            // Wipe all
            viewModel.onIntent(CredentialManagementIntent.ShowDeleteAllDialog)
            assertTrue(viewModel.state.value.showDeleteAllWarning)
            viewModel.onIntent(CredentialManagementIntent.ConfirmDeleteAll)
            advanceUntilIdle()
            assertTrue(credentials.value.isEmpty())
        }
}
