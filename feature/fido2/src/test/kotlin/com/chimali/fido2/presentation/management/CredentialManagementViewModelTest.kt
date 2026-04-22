package com.chimali.fido2.presentation.management

import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.usecase.DeleteAllCredentialsUseCase
import com.chimali.fido2.domain.usecase.DeleteCredentialUseCase
import com.chimali.fido2.domain.usecase.GetAllCredentialsUseCase
import com.chimali.fido2.domain.usecase.SearchCredentialsUseCase
import com.chimali.fido2.domain.usecase.UpdateCredentialLabelUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
import kotlin.test.*
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CredentialManagementViewModelTest {
    private lateinit var getAllCredentialsUseCase: GetAllCredentialsUseCase
    private lateinit var searchCredentialsUseCase: SearchCredentialsUseCase
    private lateinit var deleteCredentialUseCase: DeleteCredentialUseCase
    private lateinit var deleteAllCredentialsUseCase: DeleteAllCredentialsUseCase
    private lateinit var updateCredentialLabelUseCase: UpdateCredentialLabelUseCase
    private lateinit var viewModel: CredentialManagementViewModel

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        getAllCredentialsUseCase = mockk()
        searchCredentialsUseCase = mockk()
        deleteCredentialUseCase = mockk()
        deleteAllCredentialsUseCase = mockk()
        updateCredentialLabelUseCase = mockk()

        // Default mock for loadCredentials on init
        coEvery { getAllCredentialsUseCase() } returns flowOf()

        viewModel =
            CredentialManagementViewModel(
                getAllCredentialsUseCase,
                searchCredentialsUseCase,
                deleteCredentialUseCase,
                deleteAllCredentialsUseCase,
                updateCredentialLabelUseCase,
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
            coEvery { deleteCredentialUseCase("test_id") } returns Result.success(Unit)

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
            coEvery { deleteCredentialUseCase.invoke(any()) } returns Result.failure(Exception("Error"))

            viewModel.onIntent(CredentialManagementIntent.ConfirmDelete("test_id"))

            assertEquals("Failed to delete credential", viewModel.state.value.error)
            assertFalse(viewModel.state.value.isLoading)
        }

    @Test
    fun `intent ConfirmDeleteAll successfully deletes all and emits toast effect`() =
        runTest {
            coEvery { deleteAllCredentialsUseCase() } returns Result.success(Unit)

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
}
