// Host-only stub for the production release measurement logger called during disconnect.
@file:Suppress("ForbiddenImport")

package com.chimali.fido2.data.transport

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import com.chimali.core.common.annotation.Req
import com.chimali.core.common.datastore.EncryptionWrapper
import com.chimali.core.common.datastore.UserPreferences
import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.domain.model.CredentialSummary
import com.chimali.core.domain.valueobject.RpId
import com.chimali.core.domain.valueobject.UserId
import com.chimali.core.events.Fido2EventBus
import com.chimali.core.security.api.HdkKeyPair
import com.chimali.core.security.api.ImportMnemonicResult
import com.chimali.core.security.api.MasterSeedGenerator
import com.chimali.core.security.api.MasterSeedOwnerKind
import com.chimali.core.security.api.MasterSeedProvider
import com.chimali.core.security.impl.AesGcmEncryptedMetadataService
import com.chimali.core.security.impl.HmacMetadataLookupTokenService
import com.chimali.fido2.bluetooth.BluetoothHidDeviceWrapper
import com.chimali.fido2.bluetooth.CTAPHID_CANCEL
import com.chimali.fido2.bluetooth.CTAPHID_CBOR
import com.chimali.fido2.bluetooth.CTAPHID_INIT
import com.chimali.fido2.bluetooth.CTAPHID_MSG
import com.chimali.fido2.bluetooth.CTAPHID_PING
import com.chimali.fido2.bluetooth.CtapHidMessage
import com.chimali.fido2.bluetooth.HID_PACKET_SIZE
import com.chimali.fido2.bluetooth.HidConnectionState
import com.chimali.fido2.bluetooth.HidReportParser
import com.chimali.fido2.bluetooth.INIT_BCNTH_OFFSET
import com.chimali.fido2.bluetooth.INIT_BCNTL_OFFSET
import com.chimali.fido2.bluetooth.INIT_CMD_OFFSET
import com.chimali.fido2.bluetooth.INIT_DATA_OFFSET
import com.chimali.fido2.ctap2.Ctap2GetAssertionHandler
import com.chimali.fido2.ctap2.Ctap2MakeCredentialHandler
import com.chimali.fido2.ctap2.Ctap2ResponseBuilder
import com.chimali.fido2.data.crypto.CborCodec
import com.chimali.fido2.data.crypto.WalletMasterSeedProvider
import com.chimali.fido2.domain.coordinator.PairedDeviceEventCoordinator
import com.chimali.fido2.domain.model.AuthenticatorSelectionCriteria
import com.chimali.fido2.domain.model.MakeCredentialOptions
import com.chimali.fido2.domain.model.MakeCredentialResult
import com.chimali.fido2.domain.model.PublicKeyCredentialParameters
import com.chimali.fido2.domain.model.PublicKeyCredentialRpEntity
import com.chimali.fido2.domain.model.PublicKeyCredentialUserEntity
import com.chimali.fido2.domain.model.UserVerificationRequirement
import com.chimali.fido2.domain.service.AuthenticatorInfoProvider
import com.chimali.fido2.domain.service.BiometricStrength
import com.chimali.fido2.domain.service.CeremonyLock
import com.chimali.fido2.domain.service.UserVerificationAvailability
import com.chimali.fido2.domain.service.UserVerificationService
import com.chimali.fido2.domain.usecase.GetAssertionUseCase
import com.chimali.fido2.domain.usecase.RegisterCredentialUseCase
import com.chimali.fido2.presentation.navigation.Fido2UiEvent
import com.chimali.fido2.presentation.navigation.Fido2UiEventBus
import com.chimali.fido2.presentation.viewmodel.AuthenticationEffect
import com.chimali.fido2.presentation.viewmodel.AuthenticationIntent
import com.chimali.fido2.presentation.viewmodel.AuthenticationPromptViewModel
import com.chimali.fido2.presentation.viewmodel.AuthenticationState
import com.chimali.fido2.presentation.viewmodel.RegistrationEffect
import com.chimali.fido2.presentation.viewmodel.RegistrationIntent
import com.chimali.fido2.presentation.viewmodel.RegistrationPromptViewModel
import com.chimali.fido2.presentation.viewmodel.RegistrationState
import com.chimali.fido2.testing.stubSensitiveRequestCompatibility
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

/** Actual transport/handler/presenter cancellation with virtual time and synthetic outer boundaries. */
@OptIn(ExperimentalCoroutinesApi::class)
internal abstract class BluetoothHidTransportLifecycleTestSupport {
    /** Checks assertion owner, prompt and bus terminal cleanup for the selected cancellation phase. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    internal fun assertAssertionTerminated(
        fixture: Fixture,
        phase: String,
        events: List<Fido2UiEvent.AuthenticationRequested>,
        effects: List<AuthenticationEffect>,
    ) {
        assertFalse(fixture.lock.isLocked)
        assertEquals(null, fixture.bus.currentAuthenticationRequest)
        if (phase != "bootstrap" && phase != "candidates") {
            assertEquals(AuthenticationState.Cancelled, fixture.presenter.state.value)
            assertTrue(events.single().deferred.isCancelled)
            assertTrue(events.single().userInteractionCompleted.isCompleted)
        }
        if (phase == "verification") {
            assertEquals(1L, effects.filterIsInstance<AuthenticationEffect.CancelSystemPrompt>().single().promptId)
        }
        if (phase == "availability" || phase == "confirmation") assertTrue(fixture.availabilityCancelled)
        if (phase == "candidates") assertTrue(fixture.lookupCancelled)
        if (phase == "processing") assertTrue(fixture.processingCancelled)
    }

    /** Advances beyond the CTAP deadline and proves no keepalive survives request termination. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    internal suspend fun TestScope.assertKeepaliveStopped(fixture: Fixture) {
        val keepalives = fixture.sent.count { it.contentEquals(byteArrayOf(0x3B)) }
        advanceTimeBy(DEADLINE_MILLIS)
        runCurrent()
        assertEquals(keepalives, fixture.sent.count { it.contentEquals(byteArrayOf(0x3B)) })
    }

    /** Drives an assertion to the named owner/UI stage before its cancellation regression. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    internal suspend fun TestScope.prepareAssertionStage(
        fixture: Fixture,
        phase: String,
    ) {
        fixture.transport.dispatchMessage(request())
        runCurrent()
        advanceTimeBy(BOOTSTRAP_MILLIS)
        runCurrent()
        when (phase) {
            "bootstrap" -> assertEquals(1, fixture.activeOwnerCount())
            "candidates" -> assertTrue(fixture.lookupStarted)
            "availability" -> assertEquals(AuthenticationState.Idle, fixture.presenter.state.value)
            else -> assertTrue(fixture.presenter.state.value is AuthenticationState.AwaitingUserConsent)
        }
        if (phase == "confirmation" || phase == "verification" || phase == "processing") {
            fixture.presenter.handleIntent(AuthenticationIntent.ConfirmAuthentication)
            runCurrent()
            if (phase == "confirmation") {
                assertTrue(fixture.presenter.state.value is AuthenticationState.AwaitingUserConsent)
            } else {
                assertEquals(AuthenticationState.AwaitingUserVerification, fixture.presenter.state.value)
            }
            if (phase == "processing") {
                fixture.presenter.handleIntent(AuthenticationIntent.UserVerificationSuccess(1L))
                runCurrent()
                advanceTimeBy(BOOTSTRAP_MILLIS)
                runCurrent()
                assertTrue(fixture.processingStarted)
            }
        }
    }

    /** Runs a lifecycle test against deterministic dispatchers with terminal fixture cleanup. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    internal suspend fun TestScope.withFixture(
        phase: String,
        productionProvider: Boolean = false,
        sendQueueDispatcher: CoroutineDispatcher? = null,
        useRealWrapper: Boolean = false,
        operation: suspend (Fixture) -> Unit,
    ) {
        mockkStatic(Log::class)
        every { Log.println(any<Int>(), any<String>(), any<String>()) } returns 0
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val fixture = Fixture(phase, productionProvider, useRealWrapper)
        fixture.transport.workerDispatcher = dispatcher
        fixture.transport.sendQueueDispatcher = sendQueueDispatcher
        if (!useRealWrapper) {
            fixture.transport.stopSendQueueForTest()
            fixture.transport.restartSendQueueForTest()
        }
        fixture.transport.monotonicNanos = { testScheduler.currentTime * NANOS_PER_MILLISECOND }
        fixture.transport.ceremonyTimeoutMillis = DEADLINE_MILLIS
        try {
            if (useRealWrapper) {
                fixture.startAdvertising()
                fixture.transport.startTransportWorkersForTest()
            } else {
                assertTrue(fixture.transport.connect().isSuccess)
            }
            runCurrent()
            operation(fixture)
        } finally {
            fixture.presenter.handleIntent(AuthenticationIntent.CancelAuthentication)
            fixture.registrationPresenter.handleIntent(RegistrationIntent.CancelRegistration)
            fixture.transport.disconnect()
            fixture.provider.clearSession()
            runCurrent()
            Dispatchers.resetMain()
            unmockkStatic(Log::class)
        }
    }

    /** Creates one synthetic CTAPHID_INIT request report. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    internal fun initRequestReport(nonce: ByteArray): ByteArray =
        requestReport(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()), CTAPHID_INIT, nonce)

    /** Extracts the host CID assigned in a CTAPHID_INIT response payload. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    internal fun allocatedCidFromInitResponse(report: ByteArray): ByteArray =
        report.copyOfRange(INIT_DATA_OFFSET + 8, INIT_DATA_OFFSET + 12)

    /** Creates one synthetic CTAPHID_CBOR request report. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    internal fun cborRequestReport(
        cid: ByteArray,
        payload: ByteArray,
    ): ByteArray = requestReport(cid, CTAPHID_CBOR, payload)

    /** Encodes a synthetic CTAPHID request into one initial HID report. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    internal fun requestReport(
        cid: ByteArray,
        command: Int,
        payload: ByteArray,
    ): ByteArray = HidReportParser().encodeResponse(CtapHidMessage(cid, command, payload)).single()

    /** Owns the mocks and test-only state used by lifecycle regression cases. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    internal class Fixture(
        phase: String,
        productionProvider: Boolean,
        private val useRealWrapper: Boolean,
        initialWrapperState: HidConnectionState = HidConnectionState.Advertising,
    ) {
        val useCase = mockk<GetAssertionUseCase>()
        val registrationUseCase = mockk<RegisterCredentialUseCase>()
        val bus = Fido2UiEventBus()
        val derivedSeeds = mutableListOf<ByteArray>()
        val lookupTokens = HmacMetadataLookupTokenService()
        val metadata = AesGcmEncryptedMetadataService()
        val production: WalletMasterSeedProvider? = if (productionProvider) productionProvider(phase) else null
        val provider: MasterSeedProvider = production ?: TrackingMasterSeedProvider(phase == "bootstrap")
        val reportParser = HidReportParser()
        val authenticatorInfoProvider = mockk<AuthenticatorInfoProvider>()
        var wrapperGeneration = 0L
        val wrapper: BluetoothHidDeviceWrapper =
            if (useRealWrapper) {
                BluetoothHidDeviceWrapper(mockk<Context>(relaxed = true))
            } else {
                mockk(relaxed = true)
            }
        private val wrapperState = MutableStateFlow(initialWrapperState)
        private val hostDevice = mockk<BluetoothDevice>(relaxed = true)
        private val realHidDevice = if (useRealWrapper) mockk<BluetoothHidDevice>(relaxed = true) else null
        val ownedIncomingReports = mutableListOf<ByteArray>()
        val registrationHandler = mockk<Ctap2MakeCredentialHandler>(relaxed = true)

        fun activeOwnerCount(): Int =
            production?.let { (it.field("activeOwners") as Map<*, *>).size }
                ?: (provider as TrackingMasterSeedProvider).activeOwners

        fun transportSessionGeneration(): Long = transport.sessionGenerationForTest()

        fun startAdvertising() {
            val callback = wrapper.field("hidCallback") as BluetoothHidDevice.Callback
            callback.onAppStatusChanged(null, true)
        }

        fun connectHost() {
            val callback = wrapper.field("hidCallback") as BluetoothHidDevice.Callback
            callback.onConnectionStateChanged(hostDevice, BluetoothProfile.STATE_CONNECTED)
        }

        fun disconnectHost() {
            val callback = wrapper.field("hidCallback") as BluetoothHidDevice.Callback
            callback.onConnectionStateChanged(hostDevice, BluetoothProfile.STATE_DISCONNECTED)
        }

        fun sendHostReport(report: ByteArray) = wrapper.enqueueIncomingReportFromDevice(hostDevice, report)

        private fun productionProvider(phase: String): WalletMasterSeedProvider {
            val generator = mockk<MasterSeedGenerator>()
            every { generator.deriveSeed(any()) } answers {
                ByteArray(64) { (it + 1).toByte() }.also { derivedSeeds += it }
            }
            val encryption = mockk<EncryptionWrapper>()
            every { encryption.decrypt(any()) } answers { firstArg<ByteArray>().copyOf() }
            val preferences =
                UserPreferences(
                    encryptedWalletSeed = Base64.getEncoder().encodeToString("synthetic-host-mnemonic".toByteArray()),
                )
            val store =
                object : DataStore<UserPreferences> {
                    override val data =
                        flow {
                            if (phase == "bootstrap") awaitCancellation()
                            delay(BOOTSTRAP_MILLIS)
                            emit(preferences)
                        }

                    override suspend fun updateData(
                        transform: suspend (t: UserPreferences) -> UserPreferences,
                    ): UserPreferences = error("Read-only synthetic recovery fixture")
                }
            return WalletMasterSeedProvider(generator, store, encryption, lookupTokens, metadata)
        }

        val responses = mockk<Ctap2ResponseBuilder>(relaxed = true)
        val lock = CeremonyLock()
        val sent = mutableListOf<ByteArray>()
        var reportObserver: ((ByteArray) -> Boolean)? = null
        var availabilityCancelled = false
        var lookupStarted = false
        var lookupCancelled = false
        var processingStarted = false
        var processingCancelled = false
        val presenter: AuthenticationPromptViewModel
        val registrationPresenter: RegistrationPromptViewModel
        val transport: BluetoothHidTransportImpl

        init {
            val verification = mockk<UserVerificationService>()
            var availabilityCalls = 0
            val availability =
                UserVerificationAvailability(
                    isBiometricAvailable = true,
                    isPinAvailable = false,
                    isDeviceLockAvailable = false,
                    supportedBiometricTypes = emptyList(),
                    maxPinLength = 8,
                    minPinLength = 4,
                    biometricStrength = BiometricStrength.STRONG,
                )
            coEvery { verification.getUserVerificationAvailability() } coAnswers {
                availabilityCalls++
                if (phase == "availability" || (phase == "confirmation" && availabilityCalls > 1)) {
                    try {
                        awaitCancellation()
                    } finally {
                        availabilityCancelled = true
                    }
                }
                availability
            }
            coEvery { useCase.findCandidateSummaries(any()) } coAnswers {
                lookupStarted = true
                if (phase == "candidates") {
                    try {
                        awaitCancellation()
                    } finally {
                        lookupCancelled = true
                    }
                }
                listOf(mockk<CredentialSummary> { every { eraseSensitiveText() } returns Unit })
            }
            coEvery { useCase(any(), true) } coAnswers {
                processingStarted = true
                try {
                    awaitCancellation()
                } finally {
                    processingCancelled = true
                }
            }
            coEvery { registrationUseCase(any(), true) } coAnswers {
                processingStarted = true
                try {
                    awaitCancellation()
                } finally {
                    processingCancelled = true
                }
            }
            coEvery { registrationHandler.handle(any()) } coAnswers {
                val options = syntheticMakeCredentialOptions()
                val response = CompletableDeferred<Outcome<MakeCredentialResult, DomainError>>()
                bus.dispatch(Fido2UiEvent.RegistrationRequested(options, response))
                try {
                    response.await()
                    emptyList()
                } finally {
                    response.cancel()
                    options.rp.eraseSensitiveText()
                    options.user.eraseSensitiveText()
                }
            }
            val codec = mockk<CborCodec>()
            codec.stubSensitiveRequestCompatibility()
            every { codec.decodeFromFido2Format(any()) } returns
                mapOf(
                    "1" to "example.com",
                    "2" to ByteArray(32),
                    "5" to mapOf("uv" to true),
                )
            val parser = reportParser
            val handler = Ctap2GetAssertionHandler(codec, mockk(), mockk(), parser, bus, useCase, lock)
            every { responses.errorResponse(any(), 0x2F.toByte()) } returns listOf(byteArrayOf(0x2F))
            every { responses.keepAliveResponse(any(), any()) } returns listOf(byteArrayOf(0x3B))
            if (useRealWrapper) {
                every { hostDevice.address } returns "00:11:22:33:44:55"
                every { hostDevice.bondState } returns BluetoothDevice.BOND_BONDED
                val hid = requireNotNull(realHidDevice)
                setPrivateField(wrapper, "hidDevice", hid)
                wrapper.onIncomingReportOwnedForTest = { ownedIncomingReports += it }
                every { hid.sendReport(any(), any(), any()) } answers {
                    val payload = thirdArg<ByteArray>()
                    sent += payload.copyOf()
                    reportObserver?.invoke(payload) ?: true
                }
            } else {
                every { wrapper.connectionState } returns wrapperState
                every { wrapper.incomingReports } returns Channel(Channel.UNLIMITED)
                every { wrapper.sessionGeneration() } answers { wrapperGeneration }
                every { wrapper.sessionSnapshot() } answers { wrapperGeneration to wrapperState.value }
                coEvery { wrapper.initialize() } returns Result.success(Unit)
                coEvery { wrapper.registerApp() } returns Result.success(Unit)
                every { wrapper.sendReport(any(), any()) } answers {
                    firstArg<ByteArray>()
                        .also { payload -> sent += payload.copyOf() }
                        .let { payload -> reportObserver?.invoke(payload) ?: true }
                }
            }
            presenter = AuthenticationPromptViewModel(useCase, verification, bus, provider)
            registrationPresenter = RegistrationPromptViewModel(registrationUseCase, verification, bus, provider)
            transport =
                BluetoothHidTransportImpl(
                    wrapper,
                    parser,
                    registrationHandler,
                    handler,
                    responses,
                    authenticatorInfoProvider,
                    mockk<Fido2EventBus>(relaxed = true),
                    mockk<PairedDeviceEventCoordinator>(),
                    verification,
                    provider,
                )
            transport.registerChannelForTest(CID)
        }

        private fun syntheticMakeCredentialOptions(): MakeCredentialOptions =
            MakeCredentialOptions.create(
                rp = PublicKeyCredentialRpEntity(RpId("example.com"), "Example RP", null),
                user = PublicKeyCredentialUserEntity(UserId("AQ"), "synthetic-user", "Synthetic User", null),
                challenge = ByteArray(32) { it.toByte() },
                pubKeyCredParams = PublicKeyCredentialParameters.createEs256(),
                authenticatorSelection =
                    AuthenticatorSelectionCriteria.create(
                        userVerification = UserVerificationRequirement.REQUIRED,
                    ),
                selectedAlgId = -7,
            )

        private fun setPrivateField(
            instance: Any,
            name: String,
            value: Any?,
        ) {
            instance.javaClass
                .getDeclaredField(name)
                .apply { isAccessible = true }
                .set(instance, value)
        }
    }

    internal class TrackingMasterSeedProvider(
        private val stall: Boolean,
    ) : MasterSeedProvider {
        var activeOwners = 0
            private set
        var bootstrapDelayMillis = BOOTSTRAP_MILLIS
        var afterOperation: suspend () -> Unit = {}

        override suspend fun <T> withSessionOwner(
            initialize: Boolean,
            ownerKind: MasterSeedOwnerKind,
            operation: suspend (generation: Long) -> T,
        ): T {
            activeOwners++
            return try {
                if (initialize) {
                    if (stall) awaitCancellation()
                    delay(bootstrapDelayMillis)
                }
                val result = operation(1L)
                afterOperation()
                result
            } finally {
                activeOwners--
            }
        }

        override fun cancelSessionOwners(ownerKind: MasterSeedOwnerKind) = Unit

        override fun sessionGeneration(): Long = 1L

        override fun isSessionGenerationCurrent(generation: Long): Boolean = generation == 1L

        override fun clearSession() = Unit

        override suspend fun getMasterSeed(): ByteArray? = null

        override suspend fun getDeviceKeyPair(): HdkKeyPair? = null

        override suspend fun getMnemonic(): CharArray? = null

        override suspend fun importMnemonic(mnemonic: CharArray): ImportMnemonicResult = ImportMnemonicResult.Created
    }
}

internal class BluetoothHidTransportLifecycleTest : BluetoothHidTransportLifecycleTestSupport() {
    /** @see NFR-SEC-041 */
    @ParameterizedTest
    @CsvSource("Idle,false", "Advertising,false", "Connected,true")
    @Req("FR-HID-010", "FR-VAL-020")
    fun `constructor reconciles sender for the initial wrapper snapshot`(
        initialState: String,
        senderExpected: Boolean,
    ) = runTest {
        val state =
            when (initialState) {
                "Idle" -> HidConnectionState.Idle
                "Advertising" -> HidConnectionState.Advertising
                "Connected" -> HidConnectionState.Connected(mockk(relaxed = true))
                else -> error("Unknown test state")
            }
        val fixture =
            BluetoothHidTransportLifecycleTestSupport.Fixture(
                phase = "candidates",
                productionProvider = false,
                useRealWrapper = false,
                initialWrapperState = state,
            )
        val reportSent = CountDownLatch(1)
        fixture.reportObserver = {
            reportSent.countDown()
            true
        }
        try {
            val packet = ByteArray(HID_PACKET_SIZE) { 0x31 }
            fixture.transport.enqueueSendPacketForTest(packet)
            assertTrue(packet.all { it == 0.toByte() }, "The sender consumes caller packets immediately")
            assertEquals(
                senderExpected,
                reportSent.await(
                    if (senderExpected) 2 else 100,
                    if (senderExpected) TimeUnit.SECONDS else TimeUnit.MILLISECONDS,
                ),
                "Initial $initialState snapshot determines whether a sender queue is active",
            )
        } finally {
            fixture.transport.stopSendQueueForTest()
        }
    }

    /**
     * @see FR-HID-010
     * @see FR-VAL-020
     */
    @Test
    @Req("FR-HID-010", "FR-VAL-020")
    fun `unallocated CIDs and stale session results are rejected before wallet ownership`() =
        runTest {
            withFixture("candidates") { fixture ->
                val invalidCidPayload = byteArrayOf(0x02, 0x00)
                fixture.transport.dispatchMessage(
                    CtapHidMessage(byteArrayOf(9, 9, 9, 9), CTAPHID_CBOR, invalidCidPayload),
                )
                runCurrent()
                assertTrue(invalidCidPayload.all { it == 0.toByte() })
                assertEquals(0, fixture.activeOwnerCount())

                val stalePayload = byteArrayOf(0x02, 0x00)
                fixture.transport.dispatchMessage(
                    CtapHidMessage(CID, CTAPHID_CBOR, stalePayload),
                    sessionGeneration = fixture.transportSessionGeneration() - 1,
                )
                runCurrent()
                assertTrue(stalePayload.all { it == 0.toByte() })
                assertEquals(0, fixture.activeOwnerCount())
            }
        }

    /**
     * @see FR-HID-010
     * @see FR-VAL-020
     */
    @Test
    @Req("FR-HID-010", "FR-VAL-020")
    fun `CID allocation has a session limit and a finite collision retry count`() =
        runTest {
            withFixture("candidates") { fixture ->
                var candidate = 1
                fixture.transport.nextCidBytes = { bytes ->
                    bytes.fill(0)
                    bytes[3] = candidate++.toByte()
                }
                repeat(BluetoothHidTransportImpl.MAX_SESSION_CIDS - 1) {
                    assertTrue(fixture.transport.allocateCidForTest() != null)
                }
                assertNull(fixture.transport.allocateCidForTest(), "A session cannot exceed its CID budget")

                var attempts = 0
                fixture.transport.nextCidBytes = { bytes ->
                    attempts++
                    byteArrayOf(1, 2, 3, 4).copyInto(bytes)
                }
                assertNull(fixture.transport.allocateCidForTest(), "A live CID is never silently reused")
                assertEquals(BluetoothHidTransportImpl.MAX_CID_ALLOCATION_ATTEMPTS, attempts)
            }
        }

    @ParameterizedTest
    @ValueSource(strings = ["success", "send-failure", "send-exception", "cancel"])
    @Req("FR-HID-010", "FR-VAL-020")
    fun `response packet remains readable through handoff and is erased afterward`(outcome: String) =
        runTest {
            val senderScheduler = TestCoroutineScheduler()
            withFixture("candidates", sendQueueDispatcher = StandardTestDispatcher(senderScheduler)) { fixture ->
                senderScheduler.runCurrent()
                val requestPayload = byteArrayOf(0x7F)
                val retainedPacket = ByteArray(64) { 0x5A }
                val expectedBytes = retainedPacket.copyOf()
                var reportReference: ByteArray? = null
                fixture.reportObserver = { payload ->
                    reportReference = payload
                    assertTrue(payload.contentEquals(expectedBytes), "The report consumer must see intact bytes")
                    if (outcome == "send-exception") error("synthetic HID driver failure")
                    outcome != "send-failure"
                }
                every { fixture.responses.errorResponse(any(), any()) } returns listOf(retainedPacket)

                fixture.transport.dispatchMessage(CtapHidMessage(CID, CTAPHID_CBOR, requestPayload))
                runCurrent()
                assertTrue(retainedPacket.contentEquals(expectedBytes), "Queued packet remains owned until consumed")

                if (outcome == "cancel") {
                    fixture.transport.dispatchMessage(CtapHidMessage(CID, CTAPHID_CANCEL, byteArrayOf()))
                    runCurrent()
                    assertTrue(
                        retainedPacket.all { it == 0.toByte() },
                        "Cancellation releases the response owner while the queued copy stays owned by the sender",
                    )
                }

                senderScheduler.runCurrent()
                runCurrent()

                assertFalse(reportReference === retainedPacket, "The sender receives an isolated queue copy")
                assertTrue(retainedPacket.all { it == 0.toByte() }, "Transport must erase the packet after handoff")
                assertTrue(requestPayload.all { it == 0.toByte() }, "Terminal CTAP request owner erases its input")
                if (outcome == "cancel") {
                    assertTrue(reportReference == null, "Cancelled response is discarded before driver handoff")
                    assertTrue(fixture.sent.isEmpty())
                } else {
                    assertEquals(1, fixture.sent.size)
                    assertEquals(expectedBytes.toList(), fixture.sent.single().toList())
                }
            }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @ParameterizedTest
    @ValueSource(strings = ["timeout", "cancel", "transfer-failure", "success"])
    @Req("FR-HID-010", "FR-VAL-020")
    fun `response created before wallet owner transfer is erased or handed off by terminal outcome`(outcome: String) =
        runTest {
            withFixture("candidates", sendQueueDispatcher = StandardTestDispatcher(testScheduler)) { fixture ->
                val provider = fixture.provider as TrackingMasterSeedProvider
                provider.bootstrapDelayMillis = 0L
                val originalPacket = ByteArray(HID_PACKET_SIZE) { 0x51 }
                val expectedOriginal = originalPacket.copyOf()
                val timeoutPacket = ByteArray(HID_PACKET_SIZE) { 0x72 }
                val expectedTimeout = timeoutPacket.copyOf()
                val handoff = mutableListOf<ByteArray>()
                val transferEntered = CompletableDeferred<Unit>()
                coEvery { fixture.registrationHandler.handle(any()) } returns listOf(originalPacket)
                every { fixture.responses.errorResponse(any(), 0x2F.toByte()) } returns listOf(timeoutPacket)
                fixture.reportObserver = { packet ->
                    handoff += packet.copyOf()
                    true
                }
                fixture.transport.ceremonyTimeoutMillis = 25L
                provider.afterOperation = {
                    transferEntered.complete(Unit)
                    when (outcome) {
                        "timeout" -> delay(100L)
                        "cancel" -> kotlinx.coroutines.awaitCancellation()
                        "transfer-failure" -> error("synthetic owner-result transfer failure")
                        else -> Unit
                    }
                }

                val requestPayload = byteArrayOf(0x01)
                fixture.transport.dispatchMessage(CtapHidMessage(CID, CTAPHID_CBOR, requestPayload))
                runCurrent()
                transferEntered.await()

                if (outcome == "cancel") {
                    fixture.transport.dispatchMessage(CtapHidMessage(CID, CTAPHID_CANCEL, byteArrayOf()))
                }
                if (outcome == "timeout") advanceTimeBy(25L)
                runCurrent()
                if (outcome == "success") runCurrent()

                assertTrue(originalPacket.all { it == 0.toByte() }, "The original response owner is always released")
                when (outcome) {
                    "timeout" -> {
                        assertEquals(1, handoff.size)
                        assertEquals(expectedTimeout.toList(), handoff.single().toList())
                        assertTrue(timeoutPacket.all { it == 0.toByte() })
                    }
                    "success" -> {
                        assertEquals(1, handoff.size)
                        assertEquals(expectedOriginal.toList(), handoff.single().toList())
                    }
                    else -> assertTrue(handoff.isEmpty(), "Cancelled/failed result is never handed off")
                }
                handoff.forEach { it.fill(0) }
            }
        }

    /**
     * @see FR-HID-010
     * @see FR-VAL-020
     */
    @Test
    @Req("FR-HID-010", "FR-VAL-020")
    fun `prior sender cleanup cannot consume a successor generation response`() =
        runTest {
            val senderScheduler = TestCoroutineScheduler()
            withFixture("candidates", sendQueueDispatcher = StandardTestDispatcher(senderScheduler)) { fixture ->
                senderScheduler.runCurrent()
                val predecessor = ByteArray(HID_PACKET_SIZE) { 0x31 }
                fixture.transport.enqueueSendPacketForTest(predecessor)
                senderScheduler.runCurrent()
                assertEquals(1, fixture.sent.size)

                fixture.wrapperGeneration += 1L
                fixture.transport.restartSendQueueForTest()
                val successor = ByteArray(HID_PACKET_SIZE) { 0x62 }
                val expectedSuccessor = successor.copyOf()
                fixture.transport.enqueueSendPacketForTest(successor)

                // Resume the cancelled sender's finally block and the replacement sender
                // together. The old sender must be confined to its own queue.
                senderScheduler.runCurrent()
                runCurrent()

                assertEquals(2, fixture.sent.size)
                assertEquals(expectedSuccessor.toList(), fixture.sent.last().toList())
                assertTrue(predecessor.all { it == 0.toByte() })
                assertTrue(successor.all { it == 0.toByte() })
            }
        }

    /**
     * @see Fido2Transport.connect
     * @see FR-HID-010
     * @see FR-VAL-020
     */
    @Test
    @Req("FR-HID-010", "FR-VAL-020")
    fun `real wrapper connection callback rebases sender and fences reconnect traffic`() =
        runTest {
            val senderScheduler = TestCoroutineScheduler()
            withFixture(
                phase = "candidates",
                sendQueueDispatcher = StandardTestDispatcher(senderScheduler),
                useRealWrapper = true,
            ) { fixture ->
                var cidSequence = 0x20
                fixture.transport.nextCidBytes = { bytes ->
                    bytes.fill(0)
                    bytes[3] = cidSequence++.toByte()
                }
                fixture.connectHost()
                val firstGeneration = fixture.transportSessionGeneration()
                assertTrue(firstGeneration > 0L)

                fixture.sendHostReport(initRequestReport(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)))
                runCurrent()
                senderScheduler.runCurrent()
                assertEquals(1, fixture.sent.size, "The first connected host receives CTAPHID_INIT")
                val firstCid = allocatedCidFromInitResponse(fixture.sent.single())

                val abandonedPredecessorPacket = ByteArray(HID_PACKET_SIZE) { 0x2A }
                val expectedAbandonedPacket = abandonedPredecessorPacket.copyOf()
                fixture.transport.enqueueSendPacketForTest(abandonedPredecessorPacket)
                assertTrue(abandonedPredecessorPacket.all { it == 0.toByte() })

                var completedMessagePayload: ByteArray? = null
                fixture.transport.onCompletedMessageForTest = { message ->
                    completedMessagePayload = message.payload
                    fixture.disconnectHost()
                    fixture.connectHost()
                    fixture.transport.onCompletedMessageForTest = null
                }
                val oldHostReport = requestReport(firstCid, CTAPHID_PING, byteArrayOf(0x5A))
                val expectedOldHostReport = oldHostReport.copyOf()
                fixture.sendHostReport(oldHostReport)
                runCurrent()
                val secondGeneration = fixture.transportSessionGeneration()
                assertTrue(secondGeneration > firstGeneration)
                assertTrue(requireNotNull(completedMessagePayload).all { it == 0.toByte() })
                assertEquals(
                    expectedOldHostReport.toList(),
                    oldHostReport.toList(),
                    "Platform callback input is borrowed",
                )
                assertTrue(fixture.ownedIncomingReports.last().all { it == 0.toByte() })
                senderScheduler.runCurrent()
                runCurrent()

                val staleCborPayload = byteArrayOf(0x01)
                val rejectedOldCidPayload = arrayOfNulls<ByteArray>(1)
                fixture.transport.onCompletedMessageForTest = { message -> rejectedOldCidPayload[0] = message.payload }
                fixture.sendHostReport(cborRequestReport(firstCid, staleCborPayload))
                runCurrent()
                assertTrue(staleCborPayload.any { it != 0.toByte() }, "The report builder retains its borrowed input")
                assertTrue(
                    requireNotNull(rejectedOldCidPayload[0]).all { it == 0.toByte() },
                    "Old-CID payload is disposed",
                )
                coVerify(exactly = 0) { fixture.registrationHandler.handle(any()) }
                fixture.transport.onCompletedMessageForTest = null

                fixture.sendHostReport(initRequestReport(byteArrayOf(8, 7, 6, 5, 4, 3, 2, 1)))
                runCurrent()
                senderScheduler.runCurrent()
                assertEquals(2, fixture.sent.size, "The reconnect receives its own CTAPHID_INIT")
                val secondCid = allocatedCidFromInitResponse(fixture.sent.last())
                assertFalse(firstCid.contentEquals(secondCid), "A new host generation receives a new channel")

                val activeRequestPayload = byteArrayOf(0x01)
                val admittedRequestPayload = arrayOfNulls<ByteArray>(1)
                fixture.transport.onCborRequestPayloadForTest = { admittedRequestPayload[0] = it }
                coEvery { fixture.registrationHandler.handle(any()) } coAnswers {
                    kotlinx.coroutines.awaitCancellation()
                }
                fixture.sendHostReport(cborRequestReport(secondCid, activeRequestPayload))
                runCurrent()
                val ownedCborPayload = requireNotNull(admittedRequestPayload[0])
                assertTrue(ownedCborPayload.any { it != 0.toByte() })

                fixture.wrapper.reportError("synthetic terminal host error")
                runCurrent()
                assertTrue(ownedCborPayload.all { it == 0.toByte() }, "Terminal Error cancels only ended-session work")
                fixture.startAdvertising()
                fixture.connectHost()
                val thirdGeneration = fixture.transportSessionGeneration()
                assertTrue(thirdGeneration > secondGeneration)
                fixture.transport.onCborRequestPayloadForTest = null

                fixture.sendHostReport(initRequestReport(byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7)))
                runCurrent()
                senderScheduler.advanceUntilIdle()
                runCurrent()
                val thirdCid = allocatedCidFromInitResponse(fixture.sent.last())
                assertTrue(
                    fixture.transport.isChannelRegisteredForTest(thirdCid, thirdGeneration),
                    "INIT registers its assigned CID in the current session",
                )

                val responsePackets =
                    List(3) { index -> ByteArray(HID_PACKET_SIZE) { (0x60 + index).toByte() } }
                val expectedResponsePackets = responsePackets.map { it.copyOf() }
                coEvery { fixture.registrationHandler.handle(any()) } returns responsePackets
                val currentRequestOwner = arrayOfNulls<ByteArray>(1)
                fixture.transport.onCborRequestPayloadForTest = { currentRequestOwner[0] = it }
                fixture.sendHostReport(cborRequestReport(thirdCid, byteArrayOf(0x01)))

                repeat(8) {
                    runCurrent()
                    senderScheduler.advanceUntilIdle()
                    runCurrent()
                    if (fixture.sent.size >= 5) return@repeat
                }
                advanceTimeBy(BOOTSTRAP_MILLIS + 1)
                repeat(8) {
                    runCurrent()
                    senderScheduler.advanceUntilIdle()
                    runCurrent()
                    if (fixture.sent.size >= 6) return@repeat
                }

                assertTrue(
                    currentRequestOwner[0] != null,
                    "The current generation's CTAP request reaches its request owner",
                )
                coVerify(exactly = 1) { fixture.registrationHandler.handle(any()) }
                assertTrue(fixture.sent.size >= 6, "Reconnect sends all reports of its multi-report response")
                assertEquals(
                    expectedResponsePackets.map { it.toList() },
                    fixture.sent.takeLast(3).map { it.toList() },
                )
                assertFalse(
                    fixture.sent.any { it.contentEquals(expectedAbandonedPacket) },
                    "A packet queued by the predecessor never reaches the successor host",
                )
                assertTrue(responsePackets.all { packet -> packet.all { it == 0.toByte() } })
                fixture.ownedIncomingReports.forEach { report -> assertTrue(report.all { it == 0.toByte() }) }
                expectedResponsePackets.forEach { it.fill(0) }
            }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
}

internal class BluetoothHidTransportRequestLifecycleTest : BluetoothHidTransportLifecycleTestSupport() {
    /**
     * @see FR-HID-010
     * @see FR-VAL-020
     */
    @Test
    @Req("FR-HID-010", "FR-VAL-020")
    fun `active request retains its bytes while admitted and erases busy and cancelled requests`() =
        runTest {
            withFixture("candidates") { fixture ->
                val admittedPayload = byteArrayOf(0x02, 0x00)
                fixture.transport.dispatchMessage(CtapHidMessage(CID, CTAPHID_CBOR, admittedPayload))
                runCurrent()
                advanceTimeBy(BOOTSTRAP_MILLIS)
                runCurrent()
                assertTrue(fixture.lookupStarted)
                assertTrue(admittedPayload.any { it != 0.toByte() }, "Active handler still borrows the request")

                val rejectedPayload = byteArrayOf(0x02, 0x01)
                fixture.transport.dispatchMessage(CtapHidMessage(CID, CTAPHID_CBOR, rejectedPayload))
                assertTrue(rejectedPayload.all { it == 0.toByte() }, "Channel-busy request is erased on rejection")

                fixture.transport.dispatchMessage(CtapHidMessage(CID, CTAPHID_CANCEL, byteArrayOf()))
                runCurrent()
                assertTrue(admittedPayload.all { it == 0.toByte() }, "Cancelled handler input is erased after it stops")

                val successorPayload = byteArrayOf(0x02, 0x00)
                fixture.transport.dispatchMessage(CtapHidMessage(CID, CTAPHID_CBOR, successorPayload))
                runCurrent()
                advanceTimeBy(BOOTSTRAP_MILLIS)
                runCurrent()
                assertTrue(
                    successorPayload.any { it != 0.toByte() },
                    "A successor request receives its own live buffer",
                )
                fixture.transport.dispatchMessage(CtapHidMessage(CID, CTAPHID_CANCEL, byteArrayOf()))
                runCurrent()
                assertTrue(successorPayload.all { it == 0.toByte() })
            }
        }

    /**
     * @see Fido2Transport.disconnect
     * @see FR-HID-010
     * @see FR-VAL-020
     */
    @Test
    @Req("FR-HID-010", "FR-VAL-020")
    fun `disconnect erases incomplete parser state and a successor starts with fresh storage`() =
        runTest {
            withFixture("candidates") { fixture ->
                var retainedStaging: ByteArray? = null
                fixture.reportParser.onReassemblyBufferCreatedForTest = { retainedStaging = it }
                val init =
                    ByteArray(HID_PACKET_SIZE).apply {
                        CID.copyInto(this)
                        this[INIT_CMD_OFFSET] = (CTAPHID_CBOR or 0x80).toByte()
                        this[INIT_BCNTH_OFFSET] = 0
                        this[INIT_BCNTL_OFFSET] = 60
                        fill(0x5A, INIT_DATA_OFFSET, HID_PACKET_SIZE)
                    }
                fixture.reportParser.processReport(init)
                val previousOwner = requireNotNull(retainedStaging)

                assertTrue(fixture.transport.disconnect().isSuccess)

                assertTrue(previousOwner.all { it == 0.toByte() }, "Disconnect must erase incomplete reassembly")
                val successor = fixture.reportParser.processReport(init)
                assertTrue(successor.isSuccess)
                assertTrue(requireNotNull(retainedStaging).any { it != 0.toByte() })
                fixture.reportParser.reset()
            }
        }

    /**
     * @see FR-HID-010
     * @see FR-VAL-020
     */
    @Test
    @Req("FR-HID-010", "FR-VAL-020")
    fun `handler failure still erases its admitted request payload`() =
        runTest {
            withFixture("candidates") { fixture ->
                every { fixture.authenticatorInfoProvider.getAuthenticatorInfo() } throws
                    IllegalStateException("synthetic handler failure")
                val payload = byteArrayOf(0x04)

                fixture.transport.dispatchMessage(CtapHidMessage(CID, CTAPHID_CBOR, payload))
                runCurrent()

                assertTrue(payload.all { it == 0.toByte() })
            }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @ParameterizedTest
    @CsvSource(
        "bootstrap,timeout,false",
        "bootstrap,timeout,true",
        "bootstrap,cancel,false",
        "bootstrap,cancel,true",
        "bootstrap,disconnect,false",
        "bootstrap,disconnect,true",
        "candidates,timeout,false",
        "candidates,timeout,true",
        "candidates,cancel,false",
        "candidates,cancel,true",
        "candidates,disconnect,false",
        "candidates,disconnect,true",
        "availability,timeout,false",
        "availability,timeout,true",
        "availability,cancel,false",
        "availability,cancel,true",
        "availability,disconnect,false",
        "availability,disconnect,true",
        "consent,timeout,false",
        "consent,timeout,true",
        "consent,cancel,false",
        "consent,cancel,true",
        "consent,disconnect,false",
        "consent,disconnect,true",
        "confirmation,timeout,false",
        "confirmation,timeout,true",
        "confirmation,cancel,false",
        "confirmation,cancel,true",
        "confirmation,disconnect,false",
        "confirmation,disconnect,true",
        "verification,timeout,false",
        "verification,timeout,true",
        "verification,cancel,false",
        "verification,cancel,true",
        "verification,disconnect,false",
        "verification,disconnect,true",
        "processing,timeout,false",
        "processing,timeout,true",
        "processing,cancel,false",
        "processing,cancel,true",
        "processing,disconnect,false",
        "processing,disconnect,true",
    )
    fun `termination cancels each actual assertion stage and stops keepalive`(
        phase: String,
        ending: String,
        productionProvider: Boolean,
    ) = runTest {
        withFixture(phase, productionProvider) { fixture ->
            val effects = mutableListOf<AuthenticationEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                fixture.presenter.effects.collect { effects += it }
            }
            val events = mutableListOf<Fido2UiEvent.AuthenticationRequested>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                fixture.bus.events.collect { if (it is Fido2UiEvent.AuthenticationRequested) events += it }
            }
            prepareAssertionStage(fixture, phase)
            assertTrue(fixture.activeOwnerCount() > 0)
            val cachedRoot = fixture.production?.field("cachedPrk") as? ByteArray
            val cachedParent = fixture.production?.field("cachedParent") as? HdkKeyPair
            if (productionProvider && phase != "bootstrap") {
                assertTrue(requireNotNull(cachedRoot).any { it != 0.toByte() })
                assertTrue(requireNotNull(cachedParent).privateKey.any { it != 0.toByte() })
            }
            assertTrue(fixture.sent.any { it.contentEquals(byteArrayOf(0x3B)) })
            when (ending) {
                "timeout" -> {
                    advanceTimeBy(DEADLINE_MILLIS - testScheduler.currentTime - 1)
                    runCurrent()
                    assertTrue(fixture.activeOwnerCount() > 0)
                    verify(exactly = 0) { fixture.responses.errorResponse(any(), 0x2F.toByte()) }
                    advanceTimeBy(1)
                }
                "cancel" -> fixture.transport.dispatchMessage(CtapHidMessage(CID, CTAPHID_CANCEL, byteArrayOf()))
                else -> assertTrue(fixture.transport.disconnect().isSuccess)
            }
            runCurrent()
            advanceTimeBy(SETTLE_MILLIS)
            runCurrent()
            assertEquals(0, fixture.activeOwnerCount())
            fixture.production?.let { provider ->
                assertEquals(null, provider.field("cachedPrk"))
                assertEquals(null, provider.field("cachedParent"))
                assertEquals(null, fixture.lookupTokens.field("indexKey"))
                assertEquals(null, fixture.metadata.field("encryptionKey"))
                cachedRoot?.let { assertTrue(it.all { byte -> byte == 0.toByte() }) }
                cachedParent?.let { assertTrue(it.privateKey.all { byte -> byte == 0.toByte() }) }
                assertTrue(fixture.derivedSeeds.all { seed -> seed.all { it == 0.toByte() } })
            }
            assertAssertionTerminated(fixture, phase, events, effects)
            verify(exactly = if (ending == "timeout") 1 else 0) {
                fixture.responses.errorResponse(any(), 0x2F.toByte())
            }
            if (ending == "timeout") assertEquals(1, fixture.sent.count { it.contentEquals(byteArrayOf(0x2F)) })
            assertKeepaliveStopped(fixture)
            fixture.presenter.handleIntent(AuthenticationIntent.UserVerificationSuccess(1L))
            fixture.presenter.handleIntent(AuthenticationIntent.UserVerificationFailed(1L, "obsolete result"))
            runCurrent()
            coVerify(exactly = if (phase == "processing") 1 else 0) { fixture.useCase(any(), any()) }
            coVerify(exactly = 0) { fixture.useCase.invokeHeadless(any(), any()) }
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @ParameterizedTest
    @CsvSource(
        "verification,timeout",
        "verification,cancel",
        "verification,disconnect",
        "processing,timeout",
        "processing,cancel",
        "processing,disconnect",
    )
    fun `registration termination cancels prompts and work under the production wallet owner`(
        phase: String,
        ending: String,
    ) = runTest {
        withFixture(phase, productionProvider = true) { fixture ->
            val effects = mutableListOf<RegistrationEffect>()
            val events = mutableListOf<Fido2UiEvent.RegistrationRequested>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                fixture.registrationPresenter.effects.collect { effects += it }
            }
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                fixture.bus.events.collect { if (it is Fido2UiEvent.RegistrationRequested) events += it }
            }
            fixture.transport.dispatchMessage(CtapHidMessage(CID, CTAPHID_CBOR, byteArrayOf(1, 0)))
            runCurrent()
            advanceTimeBy(BOOTSTRAP_MILLIS)
            runCurrent()
            assertTrue(fixture.registrationPresenter.state.value is RegistrationState.AwaitingUserConsent)
            fixture.registrationPresenter.handleIntent(RegistrationIntent.ConfirmRegistration)
            runCurrent()
            assertEquals(RegistrationState.AwaitingUserVerification, fixture.registrationPresenter.state.value)
            if (phase == "processing") {
                fixture.registrationPresenter.handleIntent(RegistrationIntent.UserVerificationSuccess(1L))
                runCurrent()
                assertTrue(fixture.processingStarted)
            }
            val provider = requireNotNull(fixture.production)
            val cachedRoot = requireNotNull(provider.field("cachedPrk") as? ByteArray)
            val cachedParent = requireNotNull(provider.field("cachedParent") as? HdkKeyPair)
            assertTrue(cachedRoot.any { it != 0.toByte() })
            assertTrue(cachedParent.privateKey.any { it != 0.toByte() })
            assertTrue(fixture.sent.any { it.contentEquals(byteArrayOf(0x3B)) })
            when (ending) {
                "timeout" -> {
                    advanceTimeBy(DEADLINE_MILLIS - testScheduler.currentTime - 1)
                    runCurrent()
                    assertTrue(events.single().deferred.isActive)
                    advanceTimeBy(1)
                }
                "cancel" -> fixture.transport.dispatchMessage(CtapHidMessage(CID, CTAPHID_CANCEL, byteArrayOf()))
                else -> assertTrue(fixture.transport.disconnect().isSuccess)
            }
            runCurrent()
            advanceTimeBy(SETTLE_MILLIS)
            runCurrent()
            assertEquals(RegistrationState.Cancelled, fixture.registrationPresenter.state.value)
            assertTrue(events.single().deferred.isCancelled)
            assertTrue(events.single().userInteractionCompleted.isCompleted)
            assertEquals(null, fixture.bus.currentRegistrationRequest)
            assertFalse(fixture.lock.isLocked)
            assertEquals(0, fixture.activeOwnerCount())
            assertEquals(null, provider.field("cachedPrk"))
            assertEquals(null, provider.field("cachedParent"))
            assertEquals(null, fixture.lookupTokens.field("indexKey"))
            assertEquals(null, fixture.metadata.field("encryptionKey"))
            assertTrue(cachedRoot.all { it == 0.toByte() })
            assertTrue(cachedParent.privateKey.all { it == 0.toByte() })
            if (phase == "verification") {
                assertEquals(1L, effects.filterIsInstance<RegistrationEffect.CancelSystemPrompt>().single().promptId)
            } else {
                assertTrue(fixture.processingCancelled)
            }
            verify(exactly = if (ending == "timeout") 1 else 0) {
                fixture.responses.errorResponse(any(), 0x2F.toByte())
            }
            if (ending == "timeout") assertEquals(1, fixture.sent.count { it.contentEquals(byteArrayOf(0x2F)) })
            assertKeepaliveStopped(fixture)
            fixture.registrationPresenter.handleIntent(RegistrationIntent.UserVerificationSuccess(1L))
            fixture.registrationPresenter.handleIntent(RegistrationIntent.UserVerificationFailed(1L, "obsolete result"))
            runCurrent()
            coVerify(exactly = if (phase == "processing") 1 else 0) { fixture.registrationUseCase(any(), any()) }
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @ParameterizedTest
    @ValueSource(strings = ["cbor", "apdu"])
    @Req("FR-HID-010", "FR-VAL-020")
    fun `an expired queued request cannot acquire a wallet owner`(route: String) =
        runTest {
            withFixture("candidates") { fixture ->
                var clock = 0L
                var routedPayload: ByteArray? = null
                fixture.transport.monotonicNanos = { clock }
                fixture.transport.onCborRequestPayloadForTest = { routedPayload = it }
                val requestPayload =
                    if (route == "cbor") {
                        request().payload
                    } else {
                        byteArrayOf(0, 0x10, 0, 0, 2, 2, 0)
                    }
                val request =
                    if (route == "cbor") {
                        CtapHidMessage(CID, CTAPHID_CBOR, requestPayload)
                    } else {
                        CtapHidMessage(CID, CTAPHID_MSG, requestPayload)
                    }
                fixture.transport.dispatchMessage(request)
                if (route == "cbor") {
                    assertTrue(
                        requestPayload.any { it != 0.toByte() },
                        "Admitted handler retains its input while pending",
                    )
                } else {
                    assertTrue(requestPayload.all { it == 0.toByte() }, "APDU input is released after route extraction")
                    assertEquals(listOf(2.toByte(), 0.toByte()), routedPayload?.toList())
                    assertTrue(requireNotNull(routedPayload).any { it != 0.toByte() })
                }
                clock = DEADLINE_MILLIS * NANOS_PER_MILLISECOND
                advanceTimeBy(DEADLINE_MILLIS + 1)
                runCurrent()
                assertTrue(requestPayload.all { it == 0.toByte() }, "Terminal request owner erases its input")
                assertTrue(requireNotNull(routedPayload).all { it == 0.toByte() }, "Routed CBOR owner is erased too")
                assertEquals(0, fixture.activeOwnerCount())
                coVerify(exactly = 0) { fixture.useCase.findCandidateSummaries(any()) }
                verify(exactly = 1) { fixture.responses.errorResponse(any(), 0x2F.toByte()) }
            }
        }
}

private val CID = byteArrayOf(1, 2, 3, 4)
private const val BOOTSTRAP_MILLIS = 200L
private const val DEADLINE_MILLIS = 1_000L
private const val SETTLE_MILLIS = 500L
private const val NANOS_PER_MILLISECOND = 1_000_000L

private fun Any.field(name: String): Any? = javaClass.getDeclaredField(name).apply { isAccessible = true }.get(this)

private fun request() = CtapHidMessage(CID, CTAPHID_CBOR, byteArrayOf(2, 0))
