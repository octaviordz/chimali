package com.chimali.fido2.presentation.management

import androidx.lifecycle.ViewModelStore
import com.chimali.core.common.annotation.Req
import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.domain.valueobject.RpId
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.model.SensitiveCredentialText
import com.chimali.fido2.domain.usecase.DeleteAllCredentialsUseCase
import com.chimali.fido2.domain.usecase.DeleteCredentialUseCase
import com.chimali.fido2.domain.usecase.GetAllCredentialsUseCase
import com.chimali.fido2.domain.usecase.SearchCredentialsUseCase
import com.chimali.fido2.testing.CapturedDiagnostics
import com.chimali.fido2.testing.testCredentialIdFromLabel
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach

@OptIn(ExperimentalCoroutinesApi::class)
@Req("NFR-SEC-041")
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

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `intent ShowDeleteDialog updates state with credential to delete`() =
        runTest {
            val credential = mockk<PasskeyCredential>()

            viewModel.onIntent(CredentialManagementIntent.ShowDeleteDialog(credential))

            assertEquals(credential, viewModel.state.value.credentialToDelete)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `intent DismissDialog clears dialog states`() =
        runTest {
            val credential =
                PasskeyCredential.createTest(
                    id = testCredentialIdFromLabel("management-dismiss-dialog"),
                    rpId = RpId("example.com"),
                    userName = "synthetic-account",
                )
            viewModel.onIntent(CredentialManagementIntent.SelectCredential(credential))
            viewModel.onIntent(CredentialManagementIntent.ShowDeleteDialog(credential))
            viewModel.onIntent(CredentialManagementIntent.ShowDeleteAllDialog)

            viewModel.onIntent(CredentialManagementIntent.DismissDialog)

            assertNull(viewModel.state.value.selectedCredential)
            assertNull(viewModel.state.value.credentialToDelete)
            assertFalse(viewModel.state.value.isShowDeleteAllWarning)
            assertCredentialTextErased(credential)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `replacing management credentials erases unreferenced previous owners`() =
        runTest {
            val previous =
                PasskeyCredential.createTest(
                    id = testCredentialIdFromLabel("management-owner-before-refresh"),
                    rpId = RpId("example.com"),
                    userName = "synthetic-before",
                )
            val next =
                PasskeyCredential.createTest(
                    id = testCredentialIdFromLabel("management-owner-after-refresh"),
                    rpId = RpId("example.com"),
                    userName = "synthetic-after",
                )

            viewModel.setCredentials(listOf(previous))
            viewModel.setCredentials(listOf(next))

            assertCredentialTextErased(previous)
            assertEquals(listOf(next), viewModel.state.value.credentials)
            next.eraseSensitiveText()
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `refresh replaces loaded credentials and erases prior owners`() =
        runTest {
            val previous = managementCredential("management-refresh-before")
            val refreshed = managementCredential("management-refresh-after")
            viewModel.setCredentials(listOf(previous))
            coEvery { getAllCredentialsUseCase(any<Long>(), any<Long>()) } returns
                Outcome.Success(listOf(refreshed))

            viewModel.onIntent(CredentialManagementIntent.RefreshCredentials)

            assertCredentialTextErased(previous)
            assertEquals(listOf(refreshed), viewModel.state.value.credentials)
            refreshed.eraseSensitiveText()
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `search replaces loaded credentials and erases prior owners`() =
        runTest(testDispatcher) {
            val previous = managementCredential("management-search-before")
            val searched = managementCredential("management-search-after")
            val query = "synthetic-management-search"
            viewModel.setCredentials(listOf(previous))
            coEvery { searchCredentialsUseCase(match { it.contentEquals(query) }) } returns flow { emit(searched) }

            viewModel.onIntent(CredentialManagementIntent.UpdateSearchQuery(query))
            advanceTimeBy(301L)
            advanceUntilIdle()

            assertCredentialTextErased(previous)
            assertEquals(listOf(searched), viewModel.state.value.credentials)
            searched.eraseSensitiveText()
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `search failure after an emitted credential erases the untransferred owner`() =
        runTest(testDispatcher) {
            val query = "synthetic-partial-search-error"
            val abandoned = managementCredential("partial-search-error-owner")
            coEvery { searchCredentialsUseCase(match { it.contentEquals(query) }) } returns
                flow {
                    emit(abandoned)
                    throw java.io.IOException("synthetic search failure")
                }

            viewModel.onIntent(CredentialManagementIntent.UpdateSearchQuery(query))
            advanceTimeBy(301L)
            advanceUntilIdle()

            assertCredentialTextErased(abandoned)
            assertTrue(
                viewModel.state.value.credentials
                    .isEmpty(),
            )
            assertEquals("Credential search failed", viewModel.state.value.error)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `query replacement cancels search and erases its staged credentials`() =
        runTest(testDispatcher) {
            val firstQuery = "synthetic-search-before-replacement"
            val secondQuery = "synthetic-search-after-replacement"
            val abandoned = managementCredential("cancelled-search-owner")
            val current = managementCredential("replacement-search-owner")
            coEvery { searchCredentialsUseCase(match { it.contentEquals(firstQuery) }) } returns
                flow {
                    emit(abandoned)
                    awaitCancellation()
                }
            coEvery { searchCredentialsUseCase(match { it.contentEquals(secondQuery) }) } returns flow { emit(current) }

            viewModel.onIntent(CredentialManagementIntent.UpdateSearchQuery(firstQuery))
            advanceTimeBy(301L)
            runCurrent()
            viewModel.onIntent(CredentialManagementIntent.UpdateSearchQuery(secondQuery))
            advanceTimeBy(301L)
            advanceUntilIdle()

            assertCredentialTextErased(abandoned)
            assertEquals(listOf(current), viewModel.state.value.credentials)
            current.eraseSensitiveText()
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `search query owners are erased after completion and replacement`() =
        runTest(testDispatcher) {
            val firstQuery = "synthetic-query-owner-before-replacement"
            val secondQuery = "synthetic-query-owner-after-replacement"
            val capturedQueryOwners = mutableListOf<SensitiveCredentialText>()
            coEvery { searchCredentialsUseCase(any()) } answers {
                val queryOwner = firstArg<SensitiveCredentialText>()
                capturedQueryOwners += queryOwner
                if (queryOwner.contentEquals(firstQuery)) {
                    flow {
                        emit(managementCredential("query-owner-first-result"))
                        awaitCancellation()
                    }
                } else {
                    flow { emit(managementCredential("query-owner-second-result")) }
                }
            }

            viewModel.onIntent(CredentialManagementIntent.UpdateSearchQuery(firstQuery))
            advanceTimeBy(301L)
            runCurrent()
            viewModel.onIntent(CredentialManagementIntent.UpdateSearchQuery(secondQuery))
            runCurrent()
            advanceTimeBy(301L)
            advanceUntilIdle()

            assertEquals(2, capturedQueryOwners.size)
            capturedQueryOwners.forEach { owner ->
                assertTrue((0 until owner.length).all { owner[it] == '\u0000' })
            }
            viewModel.state.value.credentials
                .forEach(PasskeyCredential::eraseSensitiveText)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `retained search query flow owner is erased on replacement and view model teardown`() =
        runTest(testDispatcher) {
            viewModel.onIntent(CredentialManagementIntent.UpdateSearchQuery("synthetic-retained-query-before"))
            val replacedOwner = retainedSearchQueryOwner()

            viewModel.onIntent(CredentialManagementIntent.UpdateSearchQuery("synthetic-retained-query-after"))
            assertCredentialTextErased(replacedOwner)
            val currentOwner = retainedSearchQueryOwner()

            ViewModelStore().apply {
                put("management", viewModel)
                clear()
            }

            assertCredentialTextErased(currentOwner)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `refresh cancels active search and erases its staged credentials`() =
        runTest(testDispatcher) {
            val query = "synthetic-search-cancelled-by-refresh"
            val abandoned = managementCredential("refresh-cancelled-search-owner")
            coEvery { searchCredentialsUseCase(match { it.contentEquals(query) }) } returns
                flow {
                    emit(abandoned)
                    awaitCancellation()
                }

            viewModel.onIntent(CredentialManagementIntent.UpdateSearchQuery(query))
            advanceTimeBy(301L)
            runCurrent()
            viewModel.onIntent(CredentialManagementIntent.RefreshCredentials)
            runCurrent()

            assertCredentialTextErased(abandoned)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `page completing after search is erased and cannot replace search results`() =
        runTest(testDispatcher) {
            val delayedPage = CompletableDeferred<Outcome<List<PasskeyCredential>, DomainError>>()
            val query = "synthetic-search-fences-page"
            val stale = managementCredential("stale-page-after-search")
            val searched = managementCredential("current-search-result")
            coEvery { getAllCredentialsUseCase(any<Long>(), any<Long>()) } coAnswers { delayedPage.await() }
            coEvery { searchCredentialsUseCase(match { it.contentEquals(query) }) } returns flow { emit(searched) }

            viewModel.onIntent(CredentialManagementIntent.RefreshCredentials)
            viewModel.onIntent(CredentialManagementIntent.UpdateSearchQuery(query))
            advanceTimeBy(301L)
            advanceUntilIdle()
            delayedPage.complete(Outcome.Success(listOf(stale)))
            advanceUntilIdle()

            assertCredentialTextErased(stale)
            assertEquals(listOf(searched), viewModel.state.value.credentials)
            searched.eraseSensitiveText()
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `older page completing after refresh cannot replace the current page`() =
        runTest(testDispatcher) {
            val firstPage = CompletableDeferred<Outcome<List<PasskeyCredential>, DomainError>>()
            val stale = managementCredential("stale-page-after-refresh")
            val current = managementCredential("current-refresh-result")
            var requestCount = 0
            coEvery { getAllCredentialsUseCase(any<Long>(), any<Long>()) } coAnswers {
                requestCount += 1
                if (requestCount == 1) firstPage.await() else Outcome.Success(listOf(current))
            }

            viewModel.onIntent(CredentialManagementIntent.RefreshCredentials)
            viewModel.onIntent(CredentialManagementIntent.RefreshCredentials)
            advanceUntilIdle()
            firstPage.complete(Outcome.Success(listOf(stale)))
            advanceUntilIdle()

            assertCredentialTextErased(stale)
            assertEquals(listOf(current), viewModel.state.value.credentials)
            current.eraseSensitiveText()
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `view model teardown cancels search and erases staged credentials`() =
        runTest(testDispatcher) {
            val query = "synthetic-search-teardown"
            val abandoned = managementCredential("teardown-search-owner")
            coEvery { searchCredentialsUseCase(match { it.contentEquals(query) }) } returns
                flow {
                    emit(abandoned)
                    awaitCancellation()
                }

            viewModel.onIntent(CredentialManagementIntent.UpdateSearchQuery(query))
            advanceTimeBy(301L)
            runCurrent()
            ViewModelStore().apply {
                put("management", viewModel)
                clear()
            }
            runCurrent()

            assertCredentialTextErased(abandoned)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `replacing selected and dialog credentials erases prior detached owners`() =
        runTest {
            val previousSelection = managementCredential("management-selection-before")
            val nextSelection = managementCredential("management-selection-after")
            val previousDialog = managementCredential("management-dialog-before")
            val nextDialog = managementCredential("management-dialog-after")

            viewModel.onIntent(CredentialManagementIntent.SelectCredential(previousSelection))
            viewModel.onIntent(CredentialManagementIntent.SelectCredential(nextSelection))
            viewModel.onIntent(CredentialManagementIntent.ShowDeleteDialog(previousDialog))
            viewModel.onIntent(CredentialManagementIntent.ShowDeleteDialog(nextDialog))

            assertCredentialTextErased(previousSelection)
            assertCredentialTextErased(previousDialog)
            assertEquals(nextSelection, viewModel.state.value.selectedCredential)
            assertEquals(nextDialog, viewModel.state.value.credentialToDelete)
            viewModel.onIntent(CredentialManagementIntent.DismissDialog)
            assertCredentialTextErased(nextSelection)
            assertCredentialTextErased(nextDialog)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `view model clearing erases unique credential owners from every state slot`() =
        runTest {
            val credential = managementCredential("management-on-cleared")
            viewModel.setCredentials(listOf(credential))
            viewModel.onIntent(CredentialManagementIntent.SelectCredential(credential))
            viewModel.onIntent(CredentialManagementIntent.ShowDeleteDialog(credential))

            ViewModelStore().apply {
                put("management", viewModel)
                clear()
            }

            assertCredentialTextErased(credential)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `search query markers are omitted from diagnostics and search error state`() =
        runTest(testDispatcher) {
            val marker = "synthetic-search-account-marker"
            coEvery { searchCredentialsUseCase(match { it.contentEquals(marker) }) } returns
                flow { throw java.io.IOException(marker) }

            CapturedDiagnostics().use { diagnostics ->
                viewModel.onIntent(CredentialManagementIntent.UpdateSearchQuery(marker))
                advanceTimeBy(301L)
                advanceUntilIdle()

                diagnostics.assertNoSensitiveValues(marker)
                assertTrue(diagnostics.size > 0)
                assertEquals("Credential search failed", viewModel.state.value.error)
            }
        }

    private fun assertCredentialTextErased(credential: PasskeyCredential) {
        listOf(credential.rpId, credential.userId, credential.userName, credential.userDisplayName).forEach { owner ->
            val copy = owner.copyCharacters()
            try {
                assertContentEquals(CharArray(copy.size), copy)
            } finally {
                copy.fill('\u0000')
            }
        }
    }

    private fun assertCredentialTextErased(owner: SensitiveCredentialText) {
        val copy = owner.copyCharacters()
        try {
            assertContentEquals(CharArray(copy.size), copy)
        } finally {
            copy.fill('\u0000')
        }
    }

    private fun retainedSearchQueryOwner(): SensitiveCredentialText {
        val searchQueryField =
            CredentialManagementViewModel::class.java
                .getDeclaredField("searchQuery")
                .apply { isAccessible = true }
        val queryFlow = searchQueryField.get(viewModel) as kotlinx.coroutines.flow.StateFlow<*>
        val request = requireNotNull(queryFlow.value)
        val queryField = request.javaClass.getDeclaredField("query").apply { isAccessible = true }
        return queryField.get(request) as SensitiveCredentialText
    }

    private fun managementCredential(label: String): PasskeyCredential =
        PasskeyCredential.createTest(
            id = testCredentialIdFromLabel(label),
            rpId = RpId("example.com"),
            userName = label,
        )

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `successful search omits query marker from diagnostics`() =
        runTest(testDispatcher) {
            val marker = "synthetic-success-search-marker"
            coEvery { searchCredentialsUseCase(match { it.contentEquals(marker) }) } returns flow { }

            CapturedDiagnostics().use { diagnostics ->
                viewModel.onIntent(CredentialManagementIntent.UpdateSearchQuery(marker))
                advanceTimeBy(301L)
                advanceUntilIdle()

                diagnostics.assertNoSensitiveValues(marker)
                assertNull(viewModel.state.value.error)
                assertFalse(viewModel.state.value.isLoading)
            }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `cancelled search preserves cancellation without exposing query marker`() =
        runTest(testDispatcher) {
            val marker = "synthetic-cancelled-search-marker"
            coEvery { searchCredentialsUseCase(match { it.contentEquals(marker) }) } returns
                flow { throw CancellationException(marker) }

            CapturedDiagnostics().use { diagnostics ->
                viewModel.onIntent(CredentialManagementIntent.UpdateSearchQuery(marker))
                advanceTimeBy(301L)
                advanceUntilIdle()

                diagnostics.assertNoSensitiveValues(marker)
                assertNull(viewModel.state.value.error)
            }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `intent ConfirmDelete successfully deletes and emits toast effect`() =
        runTest {
            coEvery { deleteCredentialUseCase(testCredentialIdFromLabel("dGVzdF9pZABkR1Z6ZEY5cFpB")) } returns
                Outcome.Success(Unit)

            val effects = mutableListOf<CredentialManagementEffect>()
            val job =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.effect.toList(effects)
                }

            viewModel.onIntent(
                CredentialManagementIntent.ConfirmDelete(testCredentialIdFromLabel("dGVzdF9pZABkR1Z6ZEY5cFpB")),
            )
            advanceUntilIdle()

            assertTrue(effects.isNotEmpty())
            val effect = effects.last()
            assertTrue(effect is CredentialManagementEffect.ShowToast)
            assertEquals("Credential deleted", (effect as CredentialManagementEffect.ShowToast).message)
            assertNull(viewModel.state.value.error)

            job.cancel()
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `intent ConfirmDelete failure updates state with error`() =
        runTest(UnconfinedTestDispatcher()) {
            val credentialId = testCredentialIdFromLabel("dGVzdF9pZABkR1Z6ZEY5cFpB")
            coEvery { deleteCredentialUseCase.invoke(credentialId) } returns
                Outcome.Error(DomainError.UnknownError("Error"))

            viewModel.onIntent(CredentialManagementIntent.ConfirmDelete(credentialId))

            assertEquals("Failed to delete credential", viewModel.state.value.error)
            assertFalse(viewModel.state.value.isLoading)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
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

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `intent PendingDelete adds id to pendingDeleteIds and emits removal event`() =
        runTest {
            val credential =
                PasskeyCredential.createTest(
                    id = testCredentialIdFromLabel("dGVzdF9pZABkR1Z6ZEY5cFpB"),
                    rpId = RpId("example.com"),
                    userName = "alice",
                )
            val removalEvents = mutableListOf<PasskeyCredential>()
            val job =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.removalEvents.toList(removalEvents)
                }

            viewModel.onIntent(CredentialManagementIntent.PendingDelete(credential))

            assertTrue(
                viewModel.state.value.pendingDeleteIds
                    .contains(testCredentialIdFromLabel("dGVzdF9pZABkR1Z6ZEY5cFpB")),
            )
            assertEquals(credential, removalEvents.first())
            job.cancel()
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `intent UndoDelete removes id from pendingDeleteIds`() =
        runTest {
            val credential =
                PasskeyCredential.createTest(
                    id = testCredentialIdFromLabel("dGVzdF9pZABkR1Z6ZEY5cFpB"),
                    rpId = RpId("example.com"),
                    userName = "alice",
                )
            viewModel.onIntent(CredentialManagementIntent.PendingDelete(credential))
            assertTrue(
                viewModel.state.value.pendingDeleteIds
                    .contains(testCredentialIdFromLabel("dGVzdF9pZABkR1Z6ZEY5cFpB")),
            )

            viewModel.onIntent(
                CredentialManagementIntent.UndoDelete(testCredentialIdFromLabel("dGVzdF9pZABkR1Z6ZEY5cFpB")),
            )

            assertFalse(
                viewModel.state.value.pendingDeleteIds
                    .contains(testCredentialIdFromLabel("dGVzdF9pZABkR1Z6ZEY5cFpB")),
            )
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `intent CommitDelete calls use case and clears pending id`() =
        runTest {
            coEvery { deleteCredentialUseCase(testCredentialIdFromLabel("dGVzdF9pZABkR1Z6ZEY5cFpB")) } returns
                Outcome.Success(Unit)
            val credential =
                PasskeyCredential.createTest(
                    id = testCredentialIdFromLabel("dGVzdF9pZABkR1Z6ZEY5cFpB"),
                    rpId = RpId("example.com"),
                    userName = "alice",
                )
            viewModel.onIntent(CredentialManagementIntent.PendingDelete(credential))

            viewModel.onIntent(
                CredentialManagementIntent.CommitDelete(testCredentialIdFromLabel("dGVzdF9pZABkR1Z6ZEY5cFpB")),
            )
            advanceUntilIdle()

            assertFalse(
                viewModel.state.value.pendingDeleteIds
                    .contains(testCredentialIdFromLabel("dGVzdF9pZABkR1Z6ZEY5cFpB")),
            )
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `intent LoadNextPage increments offset and appends credentials`() =
        runTest {
            val pageSizeInt = PAGE_SIZE.toInt()
            val page1 =
                List(pageSizeInt) { i ->
                    PasskeyCredential.createTest(
                        id = testCredentialIdFromLabel("MV8kaQBNVjhrYVEAAAAAAA"),
                        rpId = RpId("example1.com"),
                        userName = "u1",
                    )
                }
            val page2 =
                listOf(
                    PasskeyCredential.createTest(
                        id = testCredentialIdFromLabel("MgBNZwAAAAAAAAAAAAAAAA"),
                        rpId = RpId("example2.com"),
                        userName = "u2",
                    ),
                )

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
            assertTrue(
                newViewModel.state.value.credentials
                    .any { it.id == testCredentialIdFromLabel("MgBNZwAAAAAAAAAAAAAAAA") },
            )
            assertFalse(newViewModel.state.value.isPaginating)
        }
}
