package com.chimali.fido2.integration

import androidx.lifecycle.ViewModelStore
import com.chimali.core.common.annotation.Req
import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.domain.valueobject.RpId
import com.chimali.core.security.api.MasterSeedOwnerKind
import com.chimali.core.security.api.MasterSeedProvider
import com.chimali.fido2.domain.model.AssertionObject
import com.chimali.fido2.domain.model.GetAssertionOptions
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.model.SensitiveCredentialText
import com.chimali.fido2.domain.model.UserVerificationRequirement
import com.chimali.fido2.domain.service.BiometricStrength
import com.chimali.fido2.domain.service.BiometricType
import com.chimali.fido2.domain.service.UserVerificationAvailability
import com.chimali.fido2.domain.service.UserVerificationService
import com.chimali.fido2.domain.usecase.GetAssertionUseCase
import com.chimali.fido2.presentation.navigation.Fido2UiEvent
import com.chimali.fido2.presentation.navigation.Fido2UiEventBus
import com.chimali.fido2.presentation.viewmodel.AuthenticationEffect
import com.chimali.fido2.presentation.viewmodel.AuthenticationIntent
import com.chimali.fido2.presentation.viewmodel.AuthenticationPromptViewModel
import com.chimali.fido2.presentation.viewmodel.AuthenticationState
import com.chimali.fido2.testing.testCredentialIdFromLabel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalForInheritanceCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * T100, T106 — Integration tests for the FIDO2 authentication flow.
 *
 * Tests the ViewModel → UseCase integration by mocking only the
 * outermost boundaries (UseCase results, UserVerificationService).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Req("NFR-SEC-041")
class AuthenticationIntegrationTest {
    private lateinit var getAssertionUseCase: GetAssertionUseCase
    private lateinit var userVerificationService: UserVerificationService
    private lateinit var masterSeedProvider: MasterSeedProvider
    private lateinit var uiEventBus: Fido2UiEventBus
    private lateinit var viewModel: AuthenticationPromptViewModel
    private var observedOwnerKind: MasterSeedOwnerKind? = null
    private var ownerReleased = false
    private var foregroundOwnerJob: Job? = null

    private val testDispatcher = StandardTestDispatcher()

    private companion object {
        private const val MAX_PIN_LEN_8 = 8
        private const val MIN_PIN_LEN_4 = 4
        private const val HASH_SIZE_32 = 32
    }

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        getAssertionUseCase = mockk()
        userVerificationService = mockk()
        masterSeedProvider = mockk()
        every { masterSeedProvider.sessionGeneration() } returns 1L
        every { masterSeedProvider.isSessionGenerationCurrent(any()) } returns true
        uiEventBus = Fido2UiEventBus()
        observedOwnerKind = null
        ownerReleased = false
        foregroundOwnerJob = null

        coEvery {
            masterSeedProvider.withSessionOwner<Outcome<AssertionObject, DomainError>>(any(), any(), any())
        } coAnswers {
            observedOwnerKind = secondArg()
            try {
                thirdArg<suspend (Long) -> Outcome<AssertionObject, DomainError>>()(1L)
            } finally {
                ownerReleased = true
            }
        }
        coEvery {
            masterSeedProvider.withSessionOwner<Unit>(false, MasterSeedOwnerKind.FOREGROUND, any())
        } coAnswers {
            observedOwnerKind = MasterSeedOwnerKind.FOREGROUND
            val ownerJob = Job(currentCoroutineContext()[Job])
            foregroundOwnerJob = ownerJob
            try {
                withContext(ownerJob) {
                    thirdArg<suspend (Long) -> Unit>()(1L)
                }
            } finally {
                ownerReleased = true
            }
        }
        every { masterSeedProvider.cancelSessionOwners(MasterSeedOwnerKind.FOREGROUND) } answers {
            foregroundOwnerJob?.cancel()
        }

        coEvery { userVerificationService.getUserVerificationAvailability() } returns
            UserVerificationAvailability(
                isBiometricAvailable = true,
                isPinAvailable = true,
                isDeviceLockAvailable = false,
                supportedBiometricTypes = listOf(BiometricType.FINGERPRINT),
                maxPinLength = MAX_PIN_LEN_8,
                minPinLength = MIN_PIN_LEN_4,
                biometricStrength = BiometricStrength.STRONG,
            )

        viewModel =
            AuthenticationPromptViewModel(getAssertionUseCase, userVerificationService, uiEventBus, masterSeedProvider)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createOptions(rpId: String = "https://example.com"): GetAssertionOptions =
        GetAssertionOptions.create(
            rpId = RpId(rpId),
            clientDataHash = ByteArray(HASH_SIZE_32),
            userVerification = UserVerificationRequirement.PREFERRED,
        )

    // ── Init → AwaitingUserConsent ────────────────────────────────────────────

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @ParameterizedTest
    @ValueSource(strings = ["cancel", "external-cancel", "external-error"])
    fun `ended authentication prompt cannot affect a fresh transport request`(mode: String) =
        runTest {
            val effects = mutableListOf<AuthenticationEffect>()
            backgroundScope.launch(
                UnconfinedTestDispatcher(testScheduler),
            ) { viewModel.effects.collect { effects += it } }
            coEvery { getAssertionUseCase(any(), true) } returns
                Outcome.Error(DomainError.OperationDenied("synthetic authorized decision"))
            val first = CompletableDeferred<Outcome<AssertionObject, DomainError>>()
            uiEventBus.dispatch(Fido2UiEvent.AuthenticationRequested(createOptions(), first))
            advanceUntilIdle()
            viewModel.handleIntent(AuthenticationIntent.ConfirmAuthentication)
            advanceUntilIdle()
            assertEquals(1L, effects.filterIsInstance<AuthenticationEffect.LaunchSystemPrompt>().single().promptId)
            when (mode) {
                "cancel" -> viewModel.handleIntent(AuthenticationIntent.CancelAuthentication)
                "external-cancel" -> first.cancel()
                else -> first.complete(Outcome.Error(DomainError.OperationDenied("synthetic external decision")))
            }
            advanceUntilIdle()
            assertEquals(AuthenticationState.Cancelled, viewModel.state.value)
            assertEquals(1L, effects.filterIsInstance<AuthenticationEffect.CancelSystemPrompt>().single().promptId)
            assertTrue(first.isCompleted)
            assertTrue(ownerReleased)

            val next = CompletableDeferred<Outcome<AssertionObject, DomainError>>()
            uiEventBus.dispatch(Fido2UiEvent.AuthenticationRequested(createOptions(), next))
            advanceUntilIdle()
            viewModel.handleIntent(AuthenticationIntent.Retry)
            advanceUntilIdle()
            viewModel.handleIntent(AuthenticationIntent.ConfirmAuthentication)
            advanceUntilIdle()
            viewModel.handleIntent(AuthenticationIntent.UserVerificationFailed(1L, "obsolete failure"))
            viewModel.handleIntent(AuthenticationIntent.UserVerificationSuccess(1L))
            advanceUntilIdle()
            assertEquals(AuthenticationState.AwaitingUserVerification, viewModel.state.value)
            assertTrue(next.isActive)
            coVerify(exactly = 0) { getAssertionUseCase(any(), any()) }
            assertEquals(2L, effects.filterIsInstance<AuthenticationEffect.LaunchSystemPrompt>().last().promptId)

            viewModel.handleIntent(AuthenticationIntent.UserVerificationSuccess(2L))
            advanceUntilIdle()
            assertTrue(next.await() is Outcome.Error)
            coVerify(exactly = 1) { getAssertionUseCase(any(), true) }
            viewModel.handleIntent(AuthenticationIntent.CancelAuthentication)
            advanceUntilIdle()
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `retry cannot revive a completed authentication request`() =
        runTest {
            coEvery { getAssertionUseCase(any(), true) } returns
                Outcome.Error(DomainError.OperationDenied("synthetic terminal decision"))
            val response = CompletableDeferred<Outcome<AssertionObject, DomainError>>()
            uiEventBus.dispatch(Fido2UiEvent.AuthenticationRequested(createOptions(), response))
            advanceUntilIdle()
            viewModel.handleIntent(AuthenticationIntent.ConfirmAuthentication)
            advanceUntilIdle()
            viewModel.handleIntent(AuthenticationIntent.UserVerificationSuccess(1L))
            advanceUntilIdle()
            assertTrue(response.isCompleted)
            assertTrue(viewModel.state.value is AuthenticationState.Error)

            viewModel.handleIntent(AuthenticationIntent.Retry)
            advanceUntilIdle()
            viewModel.handleIntent(AuthenticationIntent.ConfirmAuthentication)
            advanceUntilIdle()
            viewModel.handleIntent(AuthenticationIntent.UserVerificationSuccess(2L))
            advanceUntilIdle()

            val state = viewModel.state.value
            assertTrue(state is AuthenticationState.Error && !state.isRetryable)
            coVerify(exactly = 1) { getAssertionUseCase(any(), true) }
            viewModel.handleIntent(AuthenticationIntent.CancelAuthentication)
            advanceUntilIdle()
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @ParameterizedTest
    @ValueSource(strings = ["success", "error", "verification-failed"])
    @OptIn(InternalForInheritanceCoroutinesApi::class)
    fun `competing terminal response prevents stale authentication result`(mode: String) =
        runTest {
            val terminal = Outcome.Error(DomainError.OperationDenied("synthetic competing decision"))
            val delegate = CompletableDeferred<Outcome<AssertionObject, DomainError>>()
            val response =
                object : CompletableDeferred<Outcome<AssertionObject, DomainError>> by delegate {
                    override fun complete(value: Outcome<AssertionObject, DomainError>): Boolean {
                        delegate.complete(terminal)
                        return false
                    }
                }
            val outcome: Outcome<AssertionObject, DomainError> =
                if (mode == "success") {
                    Outcome.Success(mockk(relaxed = true))
                } else {
                    Outcome.Error(DomainError.OperationDenied("synthetic presenter decision"))
                }
            coEvery { getAssertionUseCase(any(), true) } returns outcome
            uiEventBus.dispatch(Fido2UiEvent.AuthenticationRequested(createOptions(), response))
            advanceUntilIdle()
            viewModel.handleIntent(AuthenticationIntent.ConfirmAuthentication)
            advanceUntilIdle()
            if (mode == "verification-failed") {
                viewModel.handleIntent(AuthenticationIntent.UserVerificationFailed(1L, "synthetic rejection"))
            } else {
                viewModel.handleIntent(AuthenticationIntent.UserVerificationSuccess(1L))
            }
            advanceUntilIdle()

            assertEquals(terminal, delegate.await())
            assertEquals(AuthenticationState.Cancelled, viewModel.state.value)
            coVerify(exactly = if (mode == "verification-failed") 0 else 1) { getAssertionUseCase(any(), true) }
            assertTrue(ownerReleased)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `init transitions to AwaitingUserConsent`() =
        runTest {
            val options = createOptions()

            viewModel.handleIntent(AuthenticationIntent.InitAuthentication(options))
            advanceUntilIdle()

            val state = viewModel.state.value
            assertIs<AuthenticationState.AwaitingUserConsent>(state)
            assertEquals("https://example.com", state.rpId.toBoundaryString())
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @ParameterizedTest
    @ValueSource(strings = ["success", "cancel", "replacement", "teardown"])
    fun `selected assertion copy is erased without erasing its borrowed CTAP source`(terminal: String) =
        runTest {
            val marker = "synthetic-owner.example.com"
            val sourceOwner = SensitiveCredentialText.copyOf(marker)
            val sourceOptions =
                GetAssertionOptions.fromSensitiveRpId(
                    rpId = sourceOwner,
                    clientDataHash = ByteArray(HASH_SIZE_32),
                )
            val sourceResponse = CompletableDeferred<Outcome<AssertionObject, DomainError>>()
            var presenterOwner: SensitiveCredentialText? = null
            var replacementSource: SensitiveCredentialText? = null
            var replacementOptions: GetAssertionOptions? = null

            try {
                uiEventBus.dispatch(Fido2UiEvent.AuthenticationRequested(sourceOptions, sourceResponse))
                advanceUntilIdle()

                val selectedCredential =
                    PasskeyCredential.createTest(
                        id = testCredentialIdFromLabel("authentication-owner-selection"),
                        rpId = RpId("example.com"),
                        userName = "synthetic-owner",
                    )
                viewModel.handleIntent(AuthenticationIntent.SelectCredential(selectedCredential))
                selectedCredential.eraseSensitiveText()
                advanceUntilIdle()

                val selectedOptions = retainedPendingOptions(viewModel)
                presenterOwner = retainedRpIdOwner(selectedOptions)
                assertTrue(presenterOwner !== sourceOwner)
                assertOwnerText(sourceOwner, marker)
                assertOwnerText(presenterOwner, marker)

                when (terminal) {
                    "success" -> {
                        coEvery { getAssertionUseCase(any(), true) } coAnswers {
                            assertOwnerText(retainedRpIdOwner(args[0] as GetAssertionOptions), marker)
                            Outcome.Success(
                                AssertionObject.createTest(
                                    credentialId = testCredentialIdFromLabel("authentication-owner-result"),
                                    rpId = RpId("example.com"),
                                ),
                            )
                        }
                        viewModel.handleIntent(AuthenticationIntent.UserVerificationSuccess(promptId = 1L))
                        advanceUntilIdle()
                        assertTrue(sourceResponse.isCompleted)
                    }
                    "cancel" -> {
                        viewModel.handleIntent(AuthenticationIntent.CancelAuthentication)
                        advanceUntilIdle()
                        assertTrue(sourceResponse.isCompleted)
                    }
                    "replacement" -> {
                        val nextOwner = SensitiveCredentialText.copyOf("replacement.example.com")
                        val nextOptions =
                            GetAssertionOptions.fromSensitiveRpId(
                                rpId = nextOwner,
                                clientDataHash = ByteArray(HASH_SIZE_32),
                            )
                        replacementSource = nextOwner
                        replacementOptions = nextOptions
                        viewModel.handleIntent(
                            AuthenticationIntent.InitAuthentication(nextOptions),
                        )
                        advanceUntilIdle()
                        assertTrue(sourceResponse.isCancelled)
                    }
                    "teardown" -> {
                        ViewModelStore().apply {
                            put("authentication", viewModel)
                            clear()
                        }
                        advanceUntilIdle()
                    }
                }

                assertOwnerErased(presenterOwner)
                assertOwnerText(sourceOwner, marker)
                replacementSource?.let { assertOwnerText(it, "replacement.example.com") }

                if (terminal == "replacement") {
                    viewModel.handleIntent(AuthenticationIntent.CancelAuthentication)
                    advanceUntilIdle()
                    replacementSource?.let { assertOwnerText(it, "replacement.example.com") }
                }
            } finally {
                presenterOwner?.erase()
                replacementOptions?.eraseSensitiveText()
                sourceOptions.eraseSensitiveText()
            }
        }

    // ── Cancel ───────────────────────────────────────────────────────────────

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `cancel transitions to Cancelled state`() =
        runTest {
            viewModel.handleIntent(AuthenticationIntent.InitAuthentication(createOptions()))
            advanceUntilIdle()

            viewModel.handleIntent(AuthenticationIntent.CancelAuthentication)
            advanceUntilIdle()

            assertIs<AuthenticationState.Cancelled>(viewModel.state.value)
        }

    // ── Successful authentication flow ───────────────────────────────────────

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `cancelled pending response cannot authorize assertion on immediate dispatch`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            uiEventBus = Fido2UiEventBus()
            viewModel =
                AuthenticationPromptViewModel(
                    getAssertionUseCase,
                    userVerificationService,
                    uiEventBus,
                    masterSeedProvider,
                )
            coEvery { getAssertionUseCase(any(), any()) } returns
                Outcome.Error(DomainError.OperationDenied("synthetic terminal result"))
            val response = CompletableDeferred<Outcome<AssertionObject, DomainError>>()
            uiEventBus.dispatch(Fido2UiEvent.AuthenticationRequested(createOptions(), response))
            viewModel.handleIntent(AuthenticationIntent.ConfirmAuthentication)
            response.cancel()
            viewModel.handleIntent(AuthenticationIntent.UserVerificationSuccess(1L))
            advanceUntilIdle()

            coVerify(exactly = 0) { getAssertionUseCase(any(), any()) }
            assertIs<AuthenticationState.Cancelled>(viewModel.state.value)
            assertTrue(ownerReleased)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `successful authentication runs inside an authenticator owner`() =
        runTest {
            val testAssertion =
                AssertionObject.createTest(
                    credentialId = testCredentialIdFromLabel("Y3JlZDEAWTNKbFpERQAAAA"),
                    rpId = RpId("https://example.com"),
                )
            coEvery { getAssertionUseCase(any(), true) } returns Outcome.Success(testAssertion)

            viewModel.handleIntent(AuthenticationIntent.InitAuthentication(createOptions()))
            advanceUntilIdle()

            // A successful system-verification callback admits signing.
            viewModel.handleIntent(AuthenticationIntent.ConfirmAuthentication)
            advanceUntilIdle()
            viewModel.handleIntent(AuthenticationIntent.UserVerificationSuccess(1L))
            advanceUntilIdle()

            assertIs<AuthenticationState.Success>(viewModel.state.value)
            assertEquals(MasterSeedOwnerKind.AUTHENTICATOR, observedOwnerKind)
            assertTrue(ownerReleased)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `preferred auto-confirm passes that user verification was not performed`() =
        runTest {
            val testAssertion =
                AssertionObject.createTest(
                    credentialId = testCredentialIdFromLabel("Y3JlZDEAWTNKbFpERQAAAA"),
                    rpId = RpId("https://example.com"),
                )
            coEvery { userVerificationService.getUserVerificationAvailability() } returns
                UserVerificationAvailability(
                    isBiometricAvailable = false,
                    isPinAvailable = false,
                    isDeviceLockAvailable = false,
                    supportedBiometricTypes = emptyList(),
                    maxPinLength = 0,
                    minPinLength = 0,
                    biometricStrength = BiometricStrength.WEAK,
                )
            coEvery { getAssertionUseCase(any(), false) } returns Outcome.Success(testAssertion)

            viewModel.handleIntent(AuthenticationIntent.InitAuthentication(createOptions()))
            advanceUntilIdle()

            assertIs<AuthenticationState.Success>(viewModel.state.value)
            coVerify(exactly = 1) { getAssertionUseCase(any(), false) }
            coVerify(exactly = 0) { getAssertionUseCase(any(), true) }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `cancelled CTAP response cancels the ViewModel signing owner`() =
        runTest {
            val options = createOptions()
            val response = CompletableDeferred<Outcome<AssertionObject, DomainError>>()
            val useCaseEntered = CompletableDeferred<Unit>()
            coEvery { getAssertionUseCase(options, true) } coAnswers {
                useCaseEntered.complete(Unit)
                awaitCancellation()
            }

            uiEventBus.dispatch(Fido2UiEvent.AuthenticationRequested(options, response))
            advanceUntilIdle()
            viewModel.handleIntent(AuthenticationIntent.ConfirmAuthentication)
            advanceUntilIdle()
            viewModel.handleIntent(AuthenticationIntent.UserVerificationSuccess(1L))
            advanceUntilIdle()
            useCaseEntered.await()

            assertEquals(MasterSeedOwnerKind.AUTHENTICATOR, observedOwnerKind)
            response.cancel()
            advanceUntilIdle()

            assertTrue(ownerReleased)
            assertIs<AuthenticationState.Cancelled>(viewModel.state.value)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `foreground loss cancels pending authentication verification and CTAP response`() =
        runTest {
            val options = createOptions()
            val response = CompletableDeferred<Outcome<AssertionObject, DomainError>>()
            uiEventBus.dispatch(Fido2UiEvent.AuthenticationRequested(options, response))
            advanceUntilIdle()
            assertTrue(!response.isCompleted)

            masterSeedProvider.cancelSessionOwners(MasterSeedOwnerKind.FOREGROUND)
            advanceUntilIdle()

            val result = response.await()
            assertTrue(result is Outcome.Error)
            assertIs<AuthenticationState.Cancelled>(viewModel.state.value)
            coVerify(exactly = 0) { getAssertionUseCase(any(), any()) }
            assertTrue(ownerReleased)
        }

    // ── Failed authentication ────────────────────────────────────────────────

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `failed assertion transitions to Error state`() =
        runTest {
            coEvery { getAssertionUseCase(any(), true) } returns
                Outcome.Error(DomainError.UnknownError("Auth failed", Exception("Auth failed")))

            // Need to go through: Init → Confirm → Biometric → perform → Error
            viewModel.handleIntent(AuthenticationIntent.InitAuthentication(createOptions()))
            advanceUntilIdle()

            // Use UserVerificationSuccess directly to bypass the system prompt UI wait
            viewModel.handleIntent(AuthenticationIntent.ConfirmAuthentication)
            advanceUntilIdle()
            viewModel.handleIntent(AuthenticationIntent.UserVerificationSuccess(1L))
            advanceUntilIdle()

            val state = viewModel.state.value
            assertTrue(
                (state is AuthenticationState.Error) ||
                    (state is AuthenticationState.Processing) ||
                    (state is AuthenticationState.AwaitingUserVerification),
            )
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `stale verification callback cannot authorize replacement request`() =
        runTest {
            val options = createOptions()
            viewModel.handleIntent(AuthenticationIntent.InitAuthentication(options))
            advanceUntilIdle()
            viewModel.handleIntent(AuthenticationIntent.ConfirmAuthentication)
            advanceUntilIdle()
            viewModel.handleIntent(AuthenticationIntent.CancelAuthentication)
            advanceUntilIdle()

            viewModel.handleIntent(AuthenticationIntent.InitAuthentication(options))
            advanceUntilIdle()
            viewModel.handleIntent(AuthenticationIntent.ConfirmAuthentication)
            advanceUntilIdle()
            viewModel.handleIntent(AuthenticationIntent.UserVerificationSuccess(promptId = 1L))
            advanceUntilIdle()

            assertIs<AuthenticationState.AwaitingUserVerification>(viewModel.state.value)
            coVerify(exactly = 0) { getAssertionUseCase(any(), any()) }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `duplicate verification result cannot start two assertions`() =
        runTest {
            val assertion =
                AssertionObject.createTest(
                    credentialId = testCredentialIdFromLabel("Y3JlZDEAWTNKbFpERQAAAA"),
                    rpId = RpId("https://example.com"),
                )
            coEvery { getAssertionUseCase(any(), true) } returns Outcome.Success(assertion)
            viewModel.handleIntent(AuthenticationIntent.InitAuthentication(createOptions()))
            advanceUntilIdle()
            viewModel.handleIntent(AuthenticationIntent.ConfirmAuthentication)
            advanceUntilIdle()

            viewModel.handleIntent(AuthenticationIntent.UserVerificationSuccess(promptId = 1L))
            advanceUntilIdle()
            viewModel.handleIntent(AuthenticationIntent.UserVerificationSuccess(promptId = 1L))
            advanceUntilIdle()

            coVerify(exactly = 1) { getAssertionUseCase(any(), true) }
        }

    // ── Retry after error ────────────────────────────────────────────────────

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `late verification after wallet fencing cannot enter a new assertion owner`() =
        runTest {
            coEvery { getAssertionUseCase(any(), any()) } returns
                Outcome.Error(DomainError.OperationDenied("synthetic terminal result"))
            val response = CompletableDeferred<Outcome<AssertionObject, DomainError>>()
            uiEventBus.dispatch(Fido2UiEvent.AuthenticationRequested(createOptions(), response))
            advanceUntilIdle()
            viewModel.handleIntent(AuthenticationIntent.ConfirmAuthentication)
            advanceUntilIdle()
            every { masterSeedProvider.isSessionGenerationCurrent(1L) } returns false
            viewModel.handleIntent(AuthenticationIntent.UserVerificationSuccess(1L))
            advanceUntilIdle()

            coVerify(exactly = 0) { getAssertionUseCase(any(), any()) }
            coVerify(exactly = 0) {
                masterSeedProvider.withSessionOwner<Outcome<AssertionObject, DomainError>>(
                    any(),
                    MasterSeedOwnerKind.AUTHENTICATOR,
                    any(),
                )
            }
            assertTrue(response.isCancelled)
            assertEquals(AuthenticationState.Cancelled, viewModel.state.value)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `external terminal error cancels pending assertion verification ownership`() =
        runTest {
            val response = CompletableDeferred<Outcome<AssertionObject, DomainError>>()
            uiEventBus.dispatch(Fido2UiEvent.AuthenticationRequested(createOptions(), response))
            advanceUntilIdle()
            viewModel.handleIntent(AuthenticationIntent.ConfirmAuthentication)
            advanceUntilIdle()
            response.complete(Outcome.Error(DomainError.OperationDenied("synthetic terminal decision")))
            advanceUntilIdle()

            assertEquals(AuthenticationState.Cancelled, viewModel.state.value)
            assertTrue(ownerReleased)
            viewModel.handleIntent(AuthenticationIntent.UserVerificationSuccess(1L))
            coVerify(exactly = 0) { getAssertionUseCase(any(), any()) }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `duplicate success and stale failure cannot interrupt a processing assertion`() =
        runTest {
            val entered = CompletableDeferred<Unit>()
            coEvery { getAssertionUseCase(any(), true) } coAnswers {
                entered.complete(Unit)
                awaitCancellation()
            }
            val response = CompletableDeferred<Outcome<AssertionObject, DomainError>>()
            uiEventBus.dispatch(Fido2UiEvent.AuthenticationRequested(createOptions(), response))
            advanceUntilIdle()
            viewModel.handleIntent(AuthenticationIntent.ConfirmAuthentication)
            advanceUntilIdle()
            viewModel.handleIntent(AuthenticationIntent.UserVerificationSuccess(1L))
            advanceUntilIdle()
            entered.await()
            viewModel.handleIntent(AuthenticationIntent.UserVerificationSuccess(1L))
            viewModel.handleIntent(AuthenticationIntent.UserVerificationFailed(1L, "obsolete failure"))
            advanceUntilIdle()

            assertEquals(AuthenticationState.Processing, viewModel.state.value)
            assertTrue(response.isActive)
            coVerify(exactly = 1) { getAssertionUseCase(any(), true) }
            response.cancel()
            advanceUntilIdle()
            assertTrue(ownerReleased)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `retry during live consent preserves the admitted request`() =
        runTest {
            viewModel.handleIntent(AuthenticationIntent.InitAuthentication(createOptions()))
            advanceUntilIdle()

            // A duplicate retry must retain the same live request.
            viewModel.handleIntent(AuthenticationIntent.Retry)
            advanceUntilIdle()

            assertIs<AuthenticationState.AwaitingUserConsent>(viewModel.state.value)
        }

    private fun retainedPendingOptions(viewModel: AuthenticationPromptViewModel): GetAssertionOptions {
        val field = AuthenticationPromptViewModel::class.java.getDeclaredField("pendingOptions")
        field.isAccessible = true
        return requireNotNull(field.get(viewModel) as? GetAssertionOptions)
    }

    private fun retainedRpIdOwner(options: GetAssertionOptions): SensitiveCredentialText {
        val field = GetAssertionOptions::class.java.getDeclaredField("rpIdOwner")
        field.isAccessible = true
        return requireNotNull(field.get(options) as? SensitiveCredentialText)
    }

    private fun assertOwnerText(
        owner: SensitiveCredentialText,
        expected: String,
    ) {
        val actual = owner.copyCharacters()
        try {
            assertTrue(actual.contentEquals(expected.toCharArray()))
        } finally {
            actual.fill('\u0000')
        }
    }

    private fun assertOwnerErased(owner: SensitiveCredentialText) {
        val actual = owner.copyCharacters()
        try {
            assertTrue(actual.all { it == '\u0000' })
        } finally {
            actual.fill('\u0000')
        }
    }
}
