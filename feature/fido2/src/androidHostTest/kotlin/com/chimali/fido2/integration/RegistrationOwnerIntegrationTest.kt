package com.chimali.fido2.integration

import com.chimali.core.common.annotation.Req
import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.domain.model.CredentialKeyDescriptor
import com.chimali.core.domain.valueobject.RpId
import com.chimali.core.domain.valueobject.UserId
import com.chimali.core.security.api.MasterSeedOwnerKind
import com.chimali.core.security.api.MasterSeedProvider
import com.chimali.fido2.domain.model.MakeCredentialOptions
import com.chimali.fido2.domain.model.MakeCredentialResult
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.model.PublicKeyCredentialParameters
import com.chimali.fido2.domain.model.PublicKeyCredentialRpEntity
import com.chimali.fido2.domain.model.PublicKeyCredentialUserEntity
import com.chimali.fido2.domain.model.SensitiveCredentialText
import com.chimali.fido2.domain.service.BiometricStrength
import com.chimali.fido2.domain.service.UserVerificationAvailability
import com.chimali.fido2.domain.service.UserVerificationService
import com.chimali.fido2.domain.usecase.RegisterCredentialUseCase
import com.chimali.fido2.presentation.navigation.Fido2UiEvent
import com.chimali.fido2.presentation.navigation.Fido2UiEventBus
import com.chimali.fido2.presentation.viewmodel.RegistrationEffect
import com.chimali.fido2.presentation.viewmodel.RegistrationIntent
import com.chimali.fido2.presentation.viewmodel.RegistrationPromptViewModel
import com.chimali.fido2.presentation.viewmodel.RegistrationState
import com.chimali.fido2.testing.CapturedDiagnostics
import com.chimali.fido2.testing.testCredentialIdFromLabel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
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

@Req("NFR-SEC-041", "TR-AUTH-031")
@OptIn(ExperimentalCoroutinesApi::class)
class RegistrationOwnerIntegrationTest {
    private lateinit var registerCredentialUseCase: RegisterCredentialUseCase
    private lateinit var userVerificationService: UserVerificationService
    private lateinit var masterSeedProvider: MasterSeedProvider
    private lateinit var uiEventBus: Fido2UiEventBus
    private lateinit var viewModel: RegistrationPromptViewModel
    private val testDispatcher = StandardTestDispatcher()
    private var observedOwnerKind: MasterSeedOwnerKind? = null
    private var ownerReleased = false
    private var foregroundOwnerJob: Job? = null

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        registerCredentialUseCase = mockk()
        userVerificationService = mockk()
        masterSeedProvider = mockk()
        every { masterSeedProvider.sessionGeneration() } returns 1L
        every { masterSeedProvider.isSessionGenerationCurrent(any()) } returns true
        uiEventBus = Fido2UiEventBus()
        observedOwnerKind = null
        ownerReleased = false
        foregroundOwnerJob = null

        coEvery { userVerificationService.getUserVerificationAvailability() } returns
            UserVerificationAvailability(
                isBiometricAvailable = true,
                isPinAvailable = false,
                isDeviceLockAvailable = false,
                supportedBiometricTypes = listOf(com.chimali.fido2.domain.service.BiometricType.FINGERPRINT),
                maxPinLength = 8,
                minPinLength = 4,
                biometricStrength = BiometricStrength.STRONG,
            )
        coEvery {
            masterSeedProvider.withSessionOwner<Outcome<MakeCredentialResult, DomainError>>(any(), any(), any())
        } coAnswers {
            observedOwnerKind = secondArg()
            try {
                thirdArg<suspend (Long) -> Outcome<MakeCredentialResult, DomainError>>()(1L)
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
        viewModel =
            RegistrationPromptViewModel(
                registerCredentialUseCase,
                userVerificationService,
                uiEventBus,
                masterSeedProvider,
            )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @ParameterizedTest
    @ValueSource(strings = ["cancel", "external-cancel", "external-error"])
    fun `ended registration prompt cannot affect a fresh transport request`(mode: String) =
        runTest {
            val effects = mutableListOf<RegistrationEffect>()
            backgroundScope.launch(
                UnconfinedTestDispatcher(testScheduler),
            ) { viewModel.effects.collect { effects += it } }
            coEvery { registerCredentialUseCase(any(), true) } returns
                Outcome.Error(DomainError.OperationDenied("synthetic authorized decision"))
            val first = CompletableDeferred<Outcome<MakeCredentialResult, DomainError>>()
            uiEventBus.dispatch(Fido2UiEvent.RegistrationRequested(createOptions(), first))
            advanceUntilIdle()
            viewModel.handleIntent(RegistrationIntent.ConfirmRegistration)
            advanceUntilIdle()
            assertEquals(1L, effects.filterIsInstance<RegistrationEffect.LaunchSystemPrompt>().single().promptId)
            when (mode) {
                "cancel" -> viewModel.handleIntent(RegistrationIntent.CancelRegistration)
                "external-cancel" -> first.cancel()
                else -> first.complete(Outcome.Error(DomainError.OperationDenied("synthetic external decision")))
            }
            advanceUntilIdle()
            assertEquals(RegistrationState.Cancelled, viewModel.state.value)
            assertEquals(1L, effects.filterIsInstance<RegistrationEffect.CancelSystemPrompt>().single().promptId)
            assertTrue(first.isCompleted)
            assertTrue(ownerReleased)

            val next = CompletableDeferred<Outcome<MakeCredentialResult, DomainError>>()
            uiEventBus.dispatch(Fido2UiEvent.RegistrationRequested(createOptions(), next))
            advanceUntilIdle()
            viewModel.handleIntent(RegistrationIntent.Retry)
            advanceUntilIdle()
            viewModel.handleIntent(RegistrationIntent.ConfirmRegistration)
            advanceUntilIdle()
            viewModel.handleIntent(RegistrationIntent.UserVerificationFailed(1L, "obsolete failure"))
            viewModel.handleIntent(RegistrationIntent.UserVerificationSuccess(1L))
            advanceUntilIdle()
            assertEquals(RegistrationState.AwaitingUserVerification, viewModel.state.value)
            assertTrue(next.isActive)
            coVerify(exactly = 0) { registerCredentialUseCase(any(), any()) }
            assertEquals(2L, effects.filterIsInstance<RegistrationEffect.LaunchSystemPrompt>().last().promptId)

            viewModel.handleIntent(RegistrationIntent.UserVerificationSuccess(2L))
            advanceUntilIdle()
            assertTrue(next.await() is Outcome.Error)
            coVerify(exactly = 1) { registerCredentialUseCase(any(), true) }
            viewModel.handleIntent(RegistrationIntent.CancelRegistration)
            advanceUntilIdle()
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `retry cannot revive a completed registration request`() =
        runTest {
            coEvery { registerCredentialUseCase(any(), true) } returns
                Outcome.Error(DomainError.OperationDenied("synthetic terminal decision"))
            val response = CompletableDeferred<Outcome<MakeCredentialResult, DomainError>>()
            uiEventBus.dispatch(Fido2UiEvent.RegistrationRequested(createOptions(), response))
            advanceUntilIdle()
            viewModel.handleIntent(RegistrationIntent.ConfirmRegistration)
            advanceUntilIdle()
            viewModel.handleIntent(RegistrationIntent.UserVerificationSuccess(1L))
            advanceUntilIdle()
            assertTrue(response.isCompleted)
            assertTrue(viewModel.state.value is RegistrationState.Error)

            viewModel.handleIntent(RegistrationIntent.Retry)
            advanceUntilIdle()
            viewModel.handleIntent(RegistrationIntent.ConfirmRegistration)
            advanceUntilIdle()
            viewModel.handleIntent(RegistrationIntent.UserVerificationSuccess(2L))
            advanceUntilIdle()

            val state = viewModel.state.value
            assertTrue(state is RegistrationState.Error && !state.isRetryable)
            coVerify(exactly = 1) { registerCredentialUseCase(any(), true) }
            viewModel.handleIntent(RegistrationIntent.CancelRegistration)
            advanceUntilIdle()
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @ParameterizedTest
    @ValueSource(strings = ["success", "error", "verification-failed"])
    @OptIn(InternalForInheritanceCoroutinesApi::class)
    fun `competing terminal response prevents stale registration result`(mode: String) =
        runTest {
            val terminal = Outcome.Error(DomainError.OperationDenied("synthetic competing decision"))
            val delegate = CompletableDeferred<Outcome<MakeCredentialResult, DomainError>>()
            val response =
                object : CompletableDeferred<Outcome<MakeCredentialResult, DomainError>> by delegate {
                    override fun complete(value: Outcome<MakeCredentialResult, DomainError>): Boolean {
                        delegate.complete(terminal)
                        return false
                    }
                }
            var staleCredential: PasskeyCredential? = null
            val outcome: Outcome<MakeCredentialResult, DomainError> =
                if (mode == "success") {
                    staleCredential = testCredential("stale-registration-result")
                    Outcome.Success(mockCredentialResult(requireNotNull(staleCredential)))
                } else {
                    Outcome.Error(DomainError.OperationDenied("synthetic presenter decision"))
                }
            coEvery { registerCredentialUseCase(any(), true) } returns outcome
            uiEventBus.dispatch(Fido2UiEvent.RegistrationRequested(createOptions(), response))
            advanceUntilIdle()
            viewModel.handleIntent(RegistrationIntent.ConfirmRegistration)
            advanceUntilIdle()
            if (mode == "verification-failed") {
                viewModel.handleIntent(RegistrationIntent.UserVerificationFailed(1L, "synthetic rejection"))
            } else {
                viewModel.handleIntent(RegistrationIntent.UserVerificationSuccess(1L))
            }
            advanceUntilIdle()

            assertEquals(terminal, delegate.await())
            assertEquals(RegistrationState.Cancelled, viewModel.state.value)
            staleCredential?.let(::assertCredentialTextErased)
            coVerify(exactly = if (mode == "verification-failed") 0 else 1) { registerCredentialUseCase(any(), true) }
            assertTrue(ownerReleased)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `successful transport result and UI state own independent credential metadata`() =
        runTest {
            val transportCredential = testCredential("registration-transport-owner")
            val result =
                mockCredentialResult(transportCredential)
            coEvery { registerCredentialUseCase(any(), true) } returns Outcome.Success(result)
            val response = CompletableDeferred<Outcome<MakeCredentialResult, DomainError>>()
            uiEventBus.dispatch(Fido2UiEvent.RegistrationRequested(createOptions(), response))
            advanceUntilIdle()
            viewModel.handleIntent(RegistrationIntent.ConfirmRegistration)
            advanceUntilIdle()
            viewModel.handleIntent(RegistrationIntent.UserVerificationSuccess(1L))
            advanceUntilIdle()

            assertSame(result, (response.await() as Outcome.Success).data)
            val uiCredential = (viewModel.state.value as RegistrationState.Success).credential
            assertNotSame(transportCredential, uiCredential)
            assertCredentialTextOwned(transportCredential)
            assertCredentialTextOwned(uiCredential)

            result.eraseSensitiveText()
            assertTrue(
                result.attestationObject.clientData.originText
                    .all { it == '\u0000' },
            )
            assertCredentialTextErased(transportCredential)
            assertCredentialTextOwned(uiCredential)
            androidx.lifecycle.ViewModelStore().apply {
                put("registration", viewModel)
                clear()
            }
            assertCredentialTextErased(uiCredential)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @ParameterizedTest
    @ValueSource(strings = ["verify", "cancel", "replacement", "teardown"])
    fun `registration consent state owners are erased when replaced or terminated`(terminal: String) =
        runTest {
            viewModel.handleIntent(RegistrationIntent.InitRegistration(createOptions()))
            advanceUntilIdle()

            val consent = assertIs<RegistrationState.AwaitingUserConsent>(viewModel.state.value)
            val stateOwners = listOf(consent.rpId, consent.rpName, consent.userName, consent.userDisplayName)
            stateOwners.forEach { owner -> assertTrue(owner.length > 0) }

            when (terminal) {
                "verify" -> {
                    viewModel.handleIntent(RegistrationIntent.ConfirmRegistration)
                    advanceUntilIdle()
                    assertEquals(RegistrationState.AwaitingUserVerification, viewModel.state.value)
                }
                "cancel" -> {
                    viewModel.handleIntent(RegistrationIntent.CancelRegistration)
                    advanceUntilIdle()
                    assertEquals(RegistrationState.Cancelled, viewModel.state.value)
                }
                "replacement" -> {
                    viewModel.handleIntent(RegistrationIntent.InitRegistration(createOptions()))
                    advanceUntilIdle()
                    assertIs<RegistrationState.AwaitingUserConsent>(viewModel.state.value)
                }
                "teardown" -> {
                    androidx.lifecycle.ViewModelStore().apply {
                        put("registration-consent", viewModel)
                        clear()
                    }
                }
            }

            stateOwners.forEach(::assertOwnerErased)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `request dispatch and cancellation diagnostics omit RP and account input`() =
        runTest {
            val marker = "synthetic-private-rp"
            val options =
                createOptions().copy(
                    rp = PublicKeyCredentialRpEntity.create(RpId("$marker.example.com"), marker),
                    user =
                        PublicKeyCredentialUserEntity.create(
                            UserId.fromByteArray(marker.encodeToByteArray()),
                            marker,
                            marker,
                        ),
                )
            val response = CompletableDeferred<Outcome<MakeCredentialResult, DomainError>>()
            CapturedDiagnostics().use { logs ->
                uiEventBus.dispatch(Fido2UiEvent.RegistrationRequested(options, response))
                advanceUntilIdle()
                response.cancel()
                advanceUntilIdle()
                assertEquals(
                    RegistrationState.Cancelled,
                    viewModel.state.value,
                )
                assertTrue(logs.size > 0)
                logs.assertNoSensitiveValues(marker)
            }
            coVerify(exactly = 0) { registerCredentialUseCase(any(), any()) }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `obsolete presenter cleanup preserves a waiting successor request`() =
        runTest {
            val first = CompletableDeferred<Outcome<MakeCredentialResult, DomainError>>()
            uiEventBus.dispatch(Fido2UiEvent.RegistrationRequested(createOptions(), first))
            advanceUntilIdle()
            val successor = Fido2UiEvent.RegistrationRequested(createOptions(), CompletableDeferred())
            uiEventBus.dispatch(successor)
            advanceUntilIdle()
            first.cancel()
            advanceUntilIdle()

            assertSame(successor, uiEventBus.currentRegistrationRequest)
            val nextPresenter =
                RegistrationPromptViewModel(
                    registerCredentialUseCase,
                    userVerificationService,
                    uiEventBus,
                    masterSeedProvider,
                )
            advanceUntilIdle()
            assertTrue(nextPresenter.state.value is RegistrationState.AwaitingUserConsent)
            viewModel.handleIntent(RegistrationIntent.UserVerificationSuccess(1L))
            coVerify(exactly = 0) { registerCredentialUseCase(any(), any()) }
            successor.deferred.cancel()
            advanceUntilIdle()
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `duplicate success and stale failure cannot interrupt a processing registration`() =
        runTest {
            val entered = CompletableDeferred<Unit>()
            coEvery { registerCredentialUseCase(any(), true) } coAnswers {
                entered.complete(Unit)
                awaitCancellation()
            }
            val response = CompletableDeferred<Outcome<MakeCredentialResult, DomainError>>()
            uiEventBus.dispatch(Fido2UiEvent.RegistrationRequested(createOptions(), response))
            advanceUntilIdle()
            viewModel.handleIntent(RegistrationIntent.ConfirmRegistration)
            advanceUntilIdle()
            viewModel.handleIntent(RegistrationIntent.UserVerificationSuccess(1L))
            advanceUntilIdle()
            entered.await()
            viewModel.handleIntent(RegistrationIntent.UserVerificationSuccess(1L))
            viewModel.handleIntent(RegistrationIntent.UserVerificationFailed(1L, "obsolete failure"))
            advanceUntilIdle()

            assertEquals(RegistrationState.Processing, viewModel.state.value)
            assertTrue(response.isActive)
            coVerify(exactly = 1) { registerCredentialUseCase(any(), true) }
            response.cancel()
            advanceUntilIdle()
            assertTrue(ownerReleased)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `late verification after wallet fencing cannot enter a new registration owner`() =
        runTest {
            coEvery { registerCredentialUseCase(any(), any()) } returns
                Outcome.Error(DomainError.OperationDenied("synthetic terminal result"))
            val response = CompletableDeferred<Outcome<MakeCredentialResult, DomainError>>()
            uiEventBus.dispatch(Fido2UiEvent.RegistrationRequested(createOptions(), response))
            advanceUntilIdle()
            viewModel.handleIntent(RegistrationIntent.ConfirmRegistration)
            advanceUntilIdle()
            every { masterSeedProvider.isSessionGenerationCurrent(1L) } returns false
            viewModel.handleIntent(RegistrationIntent.UserVerificationSuccess(1L))
            advanceUntilIdle()

            coVerify(exactly = 0) { registerCredentialUseCase(any(), any()) }
            coVerify(exactly = 0) {
                masterSeedProvider.withSessionOwner<Outcome<MakeCredentialResult, DomainError>>(
                    any(),
                    MasterSeedOwnerKind.AUTHENTICATOR,
                    any(),
                )
            }
            assertTrue(response.isCancelled)
            assertEquals(RegistrationState.Cancelled, viewModel.state.value)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `external terminal error cancels pending verification ownership`() =
        runTest {
            val response = CompletableDeferred<Outcome<MakeCredentialResult, DomainError>>()
            uiEventBus.dispatch(Fido2UiEvent.RegistrationRequested(createOptions(), response))
            advanceUntilIdle()
            viewModel.handleIntent(RegistrationIntent.ConfirmRegistration)
            advanceUntilIdle()
            response.complete(Outcome.Error(DomainError.OperationDenied("synthetic terminal decision")))
            advanceUntilIdle()

            assertEquals(RegistrationState.Cancelled, viewModel.state.value)
            assertTrue(ownerReleased)
            viewModel.handleIntent(RegistrationIntent.UserVerificationSuccess(1L))
            coVerify(exactly = 0) { registerCredentialUseCase(any(), any()) }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `cancelled pending response cannot authorize registration on immediate dispatch`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            uiEventBus = Fido2UiEventBus()
            viewModel =
                RegistrationPromptViewModel(
                    registerCredentialUseCase,
                    userVerificationService,
                    uiEventBus,
                    masterSeedProvider,
                )
            coEvery { registerCredentialUseCase(any(), any()) } returns
                Outcome.Error(DomainError.OperationDenied("synthetic terminal result"))
            val response = CompletableDeferred<Outcome<MakeCredentialResult, DomainError>>()
            uiEventBus.dispatch(Fido2UiEvent.RegistrationRequested(createOptions(), response))
            viewModel.handleIntent(RegistrationIntent.ConfirmRegistration)
            response.cancel()
            viewModel.handleIntent(RegistrationIntent.UserVerificationSuccess(1L))
            advanceUntilIdle()

            coVerify(exactly = 0) { registerCredentialUseCase(any(), any()) }
            assertEquals(RegistrationState.Cancelled, viewModel.state.value)
            assertTrue(ownerReleased)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `cancelled request cannot be revived by delayed verification availability`() =
        runTest {
            val availability = CompletableDeferred<UserVerificationAvailability>()
            coEvery { userVerificationService.getUserVerificationAvailability() } coAnswers { availability.await() }
            val response = CompletableDeferred<Outcome<MakeCredentialResult, DomainError>>()
            uiEventBus.dispatch(Fido2UiEvent.RegistrationRequested(createOptions(), response))
            advanceUntilIdle()
            response.cancel()
            availability.complete(
                UserVerificationAvailability(
                    isBiometricAvailable = true,
                    isPinAvailable = false,
                    isDeviceLockAvailable = false,
                    supportedBiometricTypes = emptyList(),
                    maxPinLength = 8,
                    minPinLength = 4,
                    biometricStrength = BiometricStrength.STRONG,
                ),
            )
            advanceUntilIdle()

            assertEquals(RegistrationState.Cancelled, viewModel.state.value)
            coVerify(exactly = 0) { registerCredentialUseCase(any(), any()) }
            assertTrue(ownerReleased)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `cancelled CTAP registration response cancels its ViewModel owner`() =
        runTest {
            val options = createOptions()
            val response = CompletableDeferred<Outcome<MakeCredentialResult, DomainError>>()
            val useCaseEntered = CompletableDeferred<Unit>()
            coEvery { registerCredentialUseCase(options, true) } coAnswers {
                useCaseEntered.complete(Unit)
                awaitCancellation()
            }

            uiEventBus.dispatch(Fido2UiEvent.RegistrationRequested(options, response))
            advanceUntilIdle()
            viewModel.handleIntent(RegistrationIntent.ConfirmRegistration)
            advanceUntilIdle()
            viewModel.handleIntent(RegistrationIntent.UserVerificationSuccess(1L))
            advanceUntilIdle()
            useCaseEntered.await()

            assertEquals(MasterSeedOwnerKind.AUTHENTICATOR, observedOwnerKind)
            response.cancel()
            advanceUntilIdle()

            assertTrue(ownerReleased)
            assertEquals(RegistrationState.Cancelled, viewModel.state.value)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `foreground loss cancels pending verification and completes CTAP without starting registration`() =
        runTest {
            val options = createOptions()
            val response = CompletableDeferred<Outcome<MakeCredentialResult, DomainError>>()
            uiEventBus.dispatch(Fido2UiEvent.RegistrationRequested(options, response))
            advanceUntilIdle()
            assertTrue(!response.isCompleted)

            masterSeedProvider.cancelSessionOwners(MasterSeedOwnerKind.FOREGROUND)
            advanceUntilIdle()

            val result = response.await()
            assertTrue(result is Outcome.Error)
            assertEquals(RegistrationState.Cancelled, viewModel.state.value)
            io.mockk.coVerify(exactly = 0) { registerCredentialUseCase(any(), any()) }
            assertTrue(ownerReleased)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `stale verification callback cannot authorize replacement registration`() =
        runTest {
            val options = createOptions()
            viewModel.handleIntent(RegistrationIntent.InitRegistration(options))
            advanceUntilIdle()
            viewModel.handleIntent(RegistrationIntent.ConfirmRegistration)
            advanceUntilIdle()
            viewModel.handleIntent(RegistrationIntent.CancelRegistration)
            advanceUntilIdle()

            viewModel.handleIntent(RegistrationIntent.InitRegistration(options))
            advanceUntilIdle()
            viewModel.handleIntent(RegistrationIntent.ConfirmRegistration)
            advanceUntilIdle()
            viewModel.handleIntent(RegistrationIntent.UserVerificationSuccess(promptId = 1L))
            advanceUntilIdle()

            assertEquals(
                RegistrationState.AwaitingUserVerification,
                viewModel.state.value,
            )
            coVerify(exactly = 0) { registerCredentialUseCase(any(), any()) }
        }

    private fun createOptions(): MakeCredentialOptions =
        MakeCredentialOptions.create(
            rp = PublicKeyCredentialRpEntity.create(RpId("https://example.com"), "Example"),
            user =
                PublicKeyCredentialUserEntity.create(
                    UserId.fromByteArray("user-1".encodeToByteArray()),
                    "user",
                    "User",
                ),
            challenge = ByteArray(32),
            pubKeyCredParams = PublicKeyCredentialParameters.createES256P256(),
            selectedAlgId = CredentialKeyDescriptor.COSE_ES256,
        )

    private fun testCredential(label: String): PasskeyCredential =
        PasskeyCredential.createTest(
            id = testCredentialIdFromLabel(label),
            rpId = RpId("synthetic.example"),
            userName = "synthetic-$label",
        )

    private fun mockCredentialResult(ownerCredential: PasskeyCredential): MakeCredentialResult =
        MakeCredentialResult(
            mockk<com.chimali.fido2.domain.model.AttestationObject> {
                every { clientData } returns
                    com.chimali.fido2.domain.model.ClientData.create(
                        challenge = ByteArray(32),
                        origin = "https://synthetic.example",
                    )
            },
            ownerCredential,
        )

    private fun assertCredentialTextOwned(credential: PasskeyCredential) {
        listOf(credential.rpId, credential.userId, credential.userName, credential.userDisplayName).forEach { owner ->
            val copy = owner.copyCharacters()
            try {
                assertTrue(copy.any { it != '\u0000' })
            } finally {
                copy.fill('\u0000')
            }
        }
    }

    private fun assertCredentialTextErased(credential: PasskeyCredential) {
        listOf(credential.rpId, credential.userId, credential.userName, credential.userDisplayName).forEach { owner ->
            val copy = owner.copyCharacters()
            try {
                assertTrue(copy.all { it == '\u0000' })
            } finally {
                copy.fill('\u0000')
            }
        }
    }

    private fun assertOwnerErased(owner: SensitiveCredentialText) {
        val copy = owner.copyCharacters()
        try {
            assertTrue(copy.all { it == '\u0000' })
        } finally {
            copy.fill('\u0000')
        }
    }
}
