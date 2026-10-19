package com.chimali.fido2.data.transport

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chimali.core.common.datastore.EncryptionWrapper
import com.chimali.core.common.datastore.createUserPreferencesDataStore
import com.chimali.core.common.result.Outcome
import com.chimali.core.domain.model.CredentialKeyDescriptor
import com.chimali.core.domain.model.CredentialSummary
import com.chimali.core.domain.time.TimeProvider
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.RpId
import com.chimali.core.events.Fido2EventBus
import com.chimali.core.security.hdkeys.HdkP256
import com.chimali.core.security.impl.AesGcmEncryptedMetadataService
import com.chimali.core.security.impl.Bip39MasterSeedGenerator
import com.chimali.core.security.impl.HmacMetadataLookupTokenService
import com.chimali.fido2.bluetooth.BluetoothHidDeviceWrapper
import com.chimali.fido2.bluetooth.CTAPHID_CBOR
import com.chimali.fido2.bluetooth.CtapHidMessage
import com.chimali.fido2.bluetooth.HidReportParser
import com.chimali.fido2.ctap2.Ctap2GetAssertionHandler
import com.chimali.fido2.ctap2.Ctap2MakeCredentialHandler
import com.chimali.fido2.ctap2.Ctap2ResponseBuilder
import com.chimali.fido2.data.crypto.CborCodec
import com.chimali.fido2.data.crypto.ClientDataHashService
import com.chimali.fido2.data.crypto.Fido2CryptoService
import com.chimali.fido2.data.crypto.HmacSecretProcessor
import com.chimali.fido2.data.crypto.PostQuantumCrypto
import com.chimali.fido2.data.crypto.PrfKeyDerivation
import com.chimali.fido2.data.crypto.WalletMasterSeedProvider
import com.chimali.fido2.domain.coordinator.PairedDeviceEventCoordinator
import com.chimali.fido2.domain.model.UserVerificationRequirement as RequestUserVerificationRequirement
import com.chimali.fido2.domain.repository.CredentialRepository
import com.chimali.fido2.domain.repository.PairedDeviceRepository
import com.chimali.fido2.domain.service.AuthenticatorInfoProvider
import com.chimali.fido2.domain.service.BiometricEnrollmentStatus
import com.chimali.fido2.domain.service.CeremonyLock
import com.chimali.fido2.domain.service.PinConfiguration
import com.chimali.fido2.domain.service.UserVerificationAvailability
import com.chimali.fido2.domain.service.UserVerificationRequirement as ServiceUserVerificationRequirement
import com.chimali.fido2.domain.service.UserVerificationService
import com.chimali.fido2.domain.service.VerificationContext
import com.chimali.fido2.domain.usecase.GetAssertionUseCase
import com.chimali.fido2.domain.usecase.SavePairedDeviceUseCase
import com.chimali.fido2.domain.usecase.SelectCredentialUseCase
import com.chimali.fido2.presentation.navigation.Fido2UiEvent
import com.chimali.fido2.presentation.navigation.Fido2UiEventBus
import java.io.File
import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Connected-device proof that the production transport releases a real wallet owner on disconnect. */
@RunWith(AndroidJUnit4::class)
class BluetoothHidTransportDisconnectDeviceTest {
    private companion object {
        const val STORE_RELATIVE_PATH = "hdk-transport-disconnect/%s/user-preferences.pb"
        const val TEST_TIMEOUT_MILLIS = 10_000L
        const val RP_ID = "transport-disconnect.example"
        const val ASSERTION_COMMAND: Byte = 0x02
        const val USER_VERIFICATION_OPTION = "uv"
        const val CREDENTIAL_ID_FILL: Byte = 0x5A
        const val P256_PUBLIC_KEY_ENCODING_SIZE = 65
        const val CLIENT_DATA_HASH_SIZE = 32
        const val CHANNEL_ID_BYTE_0: Byte = 0x01
        const val CHANNEL_ID_BYTE_1: Byte = 0x02
        const val CHANNEL_ID_BYTE_2: Byte = 0x03
        const val CHANNEL_ID_BYTE_3: Byte = 0x04
        const val UNEXPECTED_VERIFICATION_CALL = "Unexpected verification call"
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun hidDisconnectCancelsPendingAuthenticationAndErasesTheProductionWalletOwner() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val fixtureId =
                java.util.UUID
                    .randomUUID()
                    .toString()
            val recoveryFile = File(context.filesDir, STORE_RELATIVE_PATH.format(fixtureId))
            recoveryFile.parentFile?.mkdirs()
            val provider =
                WalletMasterSeedProvider(
                    masterSeedGenerator = Bip39MasterSeedGenerator(),
                    dataStore = createUserPreferencesDataStore { recoveryFile.absolutePath },
                    encryptionWrapper = EncryptionWrapper(),
                    lookupTokenService = HmacMetadataLookupTokenService(),
                    encryptedMetadataService = AesGcmEncryptedMetadataService(),
                )
            val credentialId =
                CredentialId.fromByteArray(
                    ByteArray(CredentialId.MIN_CREDENTIAL_ID_BYTES) { CREDENTIAL_ID_FILL },
                )
            val summary =
                CredentialSummary(
                    id = credentialId.encoded,
                    rpId = RpId(RP_ID),
                    credentialId = credentialId,
                    lastUsedAt = Clock.System.now(),
                    coseAlgorithm = CredentialKeyDescriptor.COSE_ES256,
                    derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
                    storedPublicKeyEncoding = ByteArray(P256_PUBLIC_KEY_ENCODING_SIZE),
                )
            val credentialRepository = candidateRepository(listOf(summary))
            val uiEventBus = Fido2UiEventBus()
            val parser = HidReportParser()
            val cborCodec = CborCodec()
            val hmacSecretProcessor = HmacSecretProcessor(provider)
            val prfKeyDerivation = PrfKeyDerivation(hmacSecretProcessor)
            val ceremonyLock = CeremonyLock()
            val crypto = Fido2CryptoService(HdkP256, provider, PostQuantumCrypto(), Dispatchers.Default)
            val assertionUseCase =
                GetAssertionUseCase(
                    credentialRepository = credentialRepository,
                    userVerificationService = UnusedUserVerificationService,
                    selectCredentialUseCase = SelectCredentialUseCase(),
                    cryptoService = crypto,
                    clientDataHashService = ClientDataHashService(),
                )
            val assertionHandler =
                Ctap2GetAssertionHandler(
                    cborCodec = cborCodec,
                    hmacSecretProcessor = hmacSecretProcessor,
                    prfKeyDerivation = prfKeyDerivation,
                    hidReportParser = parser,
                    uiEventBus = uiEventBus,
                    getAssertionUseCase = assertionUseCase,
                    ceremonyLock = ceremonyLock,
                )
            val eventBus = Fido2EventBus()
            val pairedDeviceRepository =
                object : PairedDeviceRepository {
                    override fun getAllPairedDevices(): Flow<List<com.chimali.fido2.domain.model.PairedDevice>> =
                        emptyFlow()

                    override suspend fun saveDevice(device: com.chimali.fido2.domain.model.PairedDevice) =
                        Outcome.Success(Unit)

                    override suspend fun deleteDevice(macAddress: String) = Outcome.Success(Unit)

                    override suspend fun updateAlias(
                        macAddress: String,
                        alias: String?,
                    ) = Outcome.Success(Unit)
                }
            val pairedDeviceCoordinator =
                PairedDeviceEventCoordinator(
                    fido2EventBus = eventBus,
                    savePairedDeviceUseCase = SavePairedDeviceUseCase(pairedDeviceRepository, TimeProvider()),
                )
            val transport =
                BluetoothHidTransportImpl(
                    hidWrapper = BluetoothHidDeviceWrapper(context),
                    hidReportParser = parser,
                    makeCredentialHandler =
                        Ctap2MakeCredentialHandler(
                            cborCodec = cborCodec,
                            hidReportParser = parser,
                            uiEventBus = uiEventBus,
                            ceremonyLock = CeremonyLock(),
                        ),
                    getAssertionHandler = assertionHandler,
                    responseBuilder = Ctap2ResponseBuilder(cborCodec, parser),
                    authenticatorInfoProvider = AuthenticatorInfoProvider(),
                    fido2EventBus = eventBus,
                    pairedDeviceEventCoordinator = pairedDeviceCoordinator,
                    userVerificationService = UnusedUserVerificationService,
                    masterSeedProvider = provider,
                )
            val eventReceived =
                async {
                    uiEventBus.events
                        .filterIsInstance<Fido2UiEvent.AuthenticationRequested>()
                        .first()
                }
            var pendingGeneration: Long? = null

            try {
                // This lifecycle test dispatches a parsed request directly instead of running
                // CTAPHID_INIT, so register its synthetic CID in the current transport session.
                transport.registerChannelForTest(
                    byteArrayOf(
                        CHANNEL_ID_BYTE_0,
                        CHANNEL_ID_BYTE_1,
                        CHANNEL_ID_BYTE_2,
                        CHANNEL_ID_BYTE_3,
                    ),
                )
                transport.dispatchMessage(
                    CtapHidMessage(
                        channelId =
                            byteArrayOf(
                                CHANNEL_ID_BYTE_0,
                                CHANNEL_ID_BYTE_1,
                                CHANNEL_ID_BYTE_2,
                                CHANNEL_ID_BYTE_3,
                            ),
                        command = CTAPHID_CBOR,
                        payload =
                            byteArrayOf(ASSERTION_COMMAND) +
                                cborCodec.encodeToFido2Format(
                                    mapOf(
                                        "1" to RP_ID,
                                        "2" to ByteArray(CLIENT_DATA_HASH_SIZE) { it.toByte() },
                                        "5" to mapOf(USER_VERIFICATION_OPTION to true),
                                    ),
                                ),
                    ),
                )
                val request = withTimeout(TEST_TIMEOUT_MILLIS) { eventReceived.await() }
                assertNotNull("The real GetAssertion handler must publish its pending UI request", request)
                assertTrue(request.options.userVerification == RequestUserVerificationRequirement.REQUIRED)
                assertFalse("The user decision must remain pending until disconnect", request.deferred.isCompleted)
                pendingGeneration = provider.sessionGeneration()
                assertTrue(provider.isSessionGenerationCurrent(requireNotNull(pendingGeneration)))
                assertTrue("The ceremony lock must remain held while verification is pending", ceremonyLock.isLocked)

                assertTrue(transport.disconnect().isSuccess)

                assertTrue("HID disconnect must cancel the pending UI decision", request.deferred.isCancelled)
                assertFalse(
                    "Disconnect must release and erase the real production wallet owner",
                    provider.isSessionGenerationCurrent(requireNotNull(pendingGeneration)),
                )
                assertFalse("The assertion handler must release its ceremony lock", ceremonyLock.isLocked)
                assertTrue(
                    "Disconnect releases the handler-owned candidate RP",
                    summary.rpIdText.all { it == '\u0000' },
                )
            } finally {
                eventReceived.cancelAndJoin()
                provider.clearSession()
                recoveryFile.parentFile?.deleteRecursively()
            }
        }

    private fun candidateRepository(summaries: List<CredentialSummary>): CredentialRepository =
        Proxy.newProxyInstance(
            CredentialRepository::class.java.classLoader,
            arrayOf(CredentialRepository::class.java),
        ) { proxy, method, arguments ->
            when {
                method.name.startsWith("getCredentialSummariesForRp") -> {
                    val requestedRpId = arguments?.firstOrNull()
                    require(requestedRpId is CharSequence)
                    require(summaries.all { it.rpIdText.contentEquals(requestedRpId) })
                    Outcome.Success(summaries)
                }
                method.name == "toString" -> "SyntheticCredentialRepository"
                method.name == "hashCode" -> System.identityHashCode(proxy)
                method.name == "equals" -> proxy === arguments?.firstOrNull()
                else -> error("Unexpected credential repository call: ${method.name}")
            }
        } as CredentialRepository

    private object UnusedUserVerificationService : UserVerificationService {
        override suspend fun getUserVerificationAvailability(): UserVerificationAvailability =
            error("A UI decision is intentionally pending in this transport test")

        override suspend fun isBiometricAvailable(): Boolean = error(UNEXPECTED_VERIFICATION_CALL)

        override suspend fun isPinAvailable(): Boolean = error(UNEXPECTED_VERIFICATION_CALL)

        override suspend fun getBiometricEnrollmentStatus(): BiometricEnrollmentStatus =
            error(UNEXPECTED_VERIFICATION_CALL)

        override suspend fun getPinConfiguration(): PinConfiguration = error(UNEXPECTED_VERIFICATION_CALL)

        override suspend fun recordUserConsent(consent: com.chimali.core.domain.model.UserConsentRecord) =
            error(UNEXPECTED_VERIFICATION_CALL)

        override fun getRecentConsentRecords(
            rpId: RpId?,
            limit: Int,
        ): Flow<com.chimali.core.domain.model.UserConsentRecord> = error(UNEXPECTED_VERIFICATION_CALL)

        override suspend fun isUserVerificationRequiredForText(
            rpId: CharSequence,
            operationType: String,
            context: VerificationContext?,
        ): ServiceUserVerificationRequirement = error(UNEXPECTED_VERIFICATION_CALL)

        override suspend fun isUserVerificationRequired(
            rpId: RpId,
            operationType: String,
            context: VerificationContext?,
        ): ServiceUserVerificationRequirement = error(UNEXPECTED_VERIFICATION_CALL)
    }
}
