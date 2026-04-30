package com.chimali.fido2.presentation.management

import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.usecase.DeleteAllCredentialsUseCase
import com.chimali.fido2.domain.usecase.DeleteCredentialUseCase
import com.chimali.fido2.domain.usecase.GetAllCredentialsUseCase
import com.chimali.fido2.domain.usecase.SearchCredentialsUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach

@OptIn(ExperimentalCoroutinesApi::class)
class CredentialManagementViewModelTest {
    private lateinit var getAllCredentialsUseCase: GetAllCredentialsUseCase
    private lateinit var searchCredentialsUseCase: SearchCredentialsUseCase
    private lateinit var deleteCredentialUseCase: DeleteCredentialUseCase
    private lateinit var deleteAllCredentialsUseCase: DeleteAllCredentialsUseCase
    private lateinit var viewModel: CredentialManagementViewModel

    private val testDispatcher = UnconfinedTestDispatcher()

    private companion object {
        private const val PAGE_SIZE = 20L
    }

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        getAllCredentialsUseCase = mockk()
        searchCredentialsUseCase = mockk()
        deleteCredentialUseCase = mockk()
        deleteAllCredentialsUseCase = mockk()

        // Default mock for loadCredentials on init
        coEvery { getAllCredentialsUseCase(any<Long>(), any<Long>()) } returns Outcome.Success(emptyList())

        viewModel =
            CredentialManagementViewModel(
                getAllCredentialsUseCase,
                searchCredentialsUseCase,
                deleteCredentialUseCase,
                deleteAllCredentialsUseCase,
            )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `intent ShowDeleteDialog updates state with credential to delete`() =
        runTest {
            val credential = mockk<PasskeyCredential>()

            viewModel.onIntent(CredentialManagementIntent.ShowDeleteDialog(credential))

            assertEquals(credential, viewModel.state.value.credentialToDelete)
        }

    @Test
    fun `intent DismissDialog clears dialog states`() =
        runTest {
            val credential = mockk<PasskeyCredential>()
            viewModel.onIntent(CredentialManagementIntent.SelectCredential(credential))
            viewModel.onIntent(CredentialManagementIntent.ShowDeleteDialog(credential))
            viewModel.onIntent(CredentialManagementIntent.ShowDeleteAllDialog)

            viewModel.onIntent(CredentialManagementIntent.DismissDialog)

            assertNull(viewModel.state.value.selectedCredential)
            assertNull(viewModel.state.value.credentialToDelete)
            assertFalse(viewModel.state.value.showDeleteAllWarning)
        }

    @Test
    fun `intent ConfirmDelete successfully deletes and emits toast effect`() =
        runTest {
            coEvery { deleteCredentialUseCase("test_id") } returns Outcome.Success(Unit)

            val effects = mutableListOf<CredentialManagementEffect>()
            val job =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.effect.toList(effects)
                }

            viewModel.onIntent(CredentialManagementIntent.ConfirmDelete("test_id"))
            advanceUntilIdle()

            assertTrue(effects.isNotEmpty())
            val effect = effects.last()
            assertTrue(effect is CredentialManagementEffect.ShowToast)
            assertEquals("Credential deleted", (effect as CredentialManagementEffect.ShowToast).message)
            assertNull(viewModel.state.value.error)

            job.cancel()
        }

    @Test
    fun `intent ConfirmDelete failure updates state with error`() =
        runTest(UnconfinedTestDispatcher()) {
            coEvery { deleteCredentialUseCase.invoke(any()) } returns Outcome.Error(DomainError.UnknownError("Error"))

            viewModel.onIntent(CredentialManagementIntent.ConfirmDelete("test_id"))

            assertEquals("Failed to delete credential", viewModel.state.value.error)
            assertFalse(viewModel.state.value.isLoading)
        }

    @Test
    fun `intent ConfirmDeleteAll successfully deletes all and emits toast effect`() =
        runTest {
            coEvery { deleteAllCredentialsUseCase() } returns Outcome.Success(Unit)

            val effects = mutableListOf<CredentialManagementEffect>()
            val job =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.effect.toList(effects)
                }

            viewModel.onIntent(CredentialManagementIntent.ConfirmDeleteAll)
            advanceUntilIdle()

            assertTrue(effects.isNotEmpty())
            val effect = effects.last()
            assertTrue(effect is CredentialManagementEffect.ShowToast)
            assertEquals("All credentials deleted", (effect as CredentialManagementEffect.ShowToast).message)
            assertNull(viewModel.state.value.error)

            job.cancel()
        }

    @Test
    fun `intent PendingDelete adds id to pendingDeleteIds and emits removal event`() =
        runTest {
            val credential =
                PasskeyCredential.createTest(id = "test_id", rpId = "example.com", userName = "alice")
            val removalEvents = mutableListOf<PasskeyCredential>()
            val job =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.removalEvents.toList(removalEvents)
                }

            viewModel.onIntent(CredentialManagementIntent.PendingDelete(credential))

            assertTrue(viewModel.state.value.pendingDeleteIds.contains("test_id"))
            assertEquals(credential, removalEvents.first())
            job.cancel()
        }

    @Test
    fun `intent UndoDelete removes id from pendingDeleteIds`() =
        runTest {
            val credential =
                PasskeyCredential.createTest(id = "test_id", rpId = "example.com", userName = "alice")
            viewModel.onIntent(CredentialManagementIntent.PendingDelete(credential))
            assertTrue(viewModel.state.value.pendingDeleteIds.contains("test_id"))

            viewModel.onIntent(CredentialManagementIntent.UndoDelete("test_id"))

            assertFalse(viewModel.state.value.pendingDeleteIds.contains("test_id"))
        }

    @Test
    fun `intent CommitDelete calls use case and clears pending id`() =
        runTest {
            coEvery { deleteCredentialUseCase("test_id") } returns Outcome.Success(Unit)
            val credential =
                PasskeyCredential.createTest(id = "test_id", rpId = "example.com", userName = "alice")
            viewModel.onIntent(CredentialManagementIntent.PendingDelete(credential))

            viewModel.onIntent(CredentialManagementIntent.CommitDelete("test_id"))
            advanceUntilIdle()

            assertFalse(viewModel.state.value.pendingDeleteIds.contains("test_id"))
        }

    @Test
    fun `intent LoadNextPage increments offset and appends credentials`() =
        runTest {
            val pageSizeInt = PAGE_SIZE.toInt()
            val page1 =
                List(pageSizeInt) { i ->
                    PasskeyCredential.createTest(id = "1_$i", rpId = "example1.com", userName = "u1")
                }
            val page2 =
                listOf(PasskeyCredential.createTest(id = "2", rpId = "example2.com", userName = "u2"))

            // Re-initialize with paginated mocks
            coEvery {
                getAllCredentialsUseCase(any<Long>(), any<Long>())
            } returns Outcome.Success(emptyList()) // fallback
            coEvery { getAllCredentialsUseCase(PAGE_SIZE, 0L) } returns Outcome.Success(page1)
            coEvery { getAllCredentialsUseCase(PAGE_SIZE, PAGE_SIZE) } returns Outcome.Success(page2)

            val newViewModel =
                CredentialManagementViewModel(
                    getAllCredentialsUseCase,
                    searchCredentialsUseCase,
                    deleteCredentialUseCase,
                    deleteAllCredentialsUseCase,
                )
            advanceUntilIdle()

            assertEquals(pageSizeInt, newViewModel.state.value.credentials.size)

            newViewModel.onIntent(CredentialManagementIntent.LoadNextPage)
            advanceUntilIdle()

            val expectedTotal = pageSizeInt + 1
            assertEquals(expectedTotal, newViewModel.state.value.credentials.size)
            assertTrue(newViewModel.state.value.credentials.any { it.id == "2" })
            assertFalse(newViewModel.state.value.isPaginating)
        }
}
