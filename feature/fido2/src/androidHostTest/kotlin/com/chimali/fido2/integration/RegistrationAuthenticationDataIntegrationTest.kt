package com.chimali.fido2.integration

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.chimali.core.common.result.Outcome
import com.chimali.core.common.result.getOrThrow
import com.chimali.core.common.result.isSuccess
import com.chimali.core.domain.time.TimeProvider
import com.chimali.core.domain.valueobject.RpId
import com.chimali.core.domain.valueobject.UserId
import com.chimali.core.security.api.HdkKeyPair
import com.chimali.core.security.api.HdkManager
import com.chimali.core.security.api.HdkResult
import com.chimali.core.security.api.MasterSeedProvider
import com.chimali.core.security.hdkeys.P256Group
import com.chimali.fido2.data.crypto.CborCodec
import com.chimali.fido2.data.crypto.ClientDataHashService
import com.chimali.fido2.data.crypto.Fido2CryptoService
import com.chimali.fido2.data.crypto.PostQuantumCrypto
import com.chimali.fido2.data.dao.PasskeyCredentialDao
import com.chimali.fido2.data.dao.RelyingPartyDao
import com.chimali.fido2.data.dao.UserConsentRecordDao
import com.chimali.fido2.data.database.Fido2Database
import com.chimali.fido2.data.repository.CredentialRepositoryImpl
import com.chimali.fido2.data.worker.CorruptedKeyRepairWorker
import com.chimali.fido2.domain.model.GetAssertionOptions
import com.chimali.fido2.domain.model.MakeCredentialOptions
import com.chimali.fido2.domain.model.PublicKeyCredentialDescriptor
import com.chimali.fido2.domain.model.PublicKeyCredentialParameters
import com.chimali.fido2.domain.model.PublicKeyCredentialRpEntity
import com.chimali.fido2.domain.model.PublicKeyCredentialUserEntity
import com.chimali.fido2.domain.model.UserVerificationRequirement
import com.chimali.fido2.domain.repository.Fido2SettingsRepository
import com.chimali.fido2.domain.service.BiometricStrength
import com.chimali.fido2.domain.service.BiometricType
import com.chimali.fido2.domain.service.UserVerificationAvailability
import com.chimali.fido2.domain.service.UserVerificationRequirement as ServiceVerificationRequirement
import com.chimali.fido2.domain.service.UserVerificationService
import com.chimali.fido2.domain.usecase.GetAssertionUseCase
import com.chimali.fido2.domain.usecase.RegisterCredentialUseCase
import com.chimali.fido2.domain.usecase.SelectCredentialUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.math.BigInteger
import java.security.Security
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.bouncycastle.jce.provider.BouncyCastleProvider

/**
 * T159b — End-to-end Data Integration Test for Registration and Authentication.
 *
 * Verifies the full flow from UseCase through Repository and DAOs into a real
 * SQLDelight in-memory database, specifically focusing on CredentialId
 * serialization integrity.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RegistrationAuthenticationDataIntegrationTest {
    private lateinit var database: Fido2Database
    private lateinit var repository: CredentialRepositoryImpl
    private lateinit var registerUseCase: RegisterCredentialUseCase
    private lateinit var assertionUseCase: GetAssertionUseCase
    private lateinit var cryptoService: Fido2CryptoService
    private lateinit var userVerificationService: UserVerificationService

    private val testDispatcher = UnconfinedTestDispatcher()
    private val timeProvider = TimeProvider()

    private companion object {
        private const val SEED_SIZE_32 = 32
        private const val CHALLENGE_SIZE_32 = 32
        private const val MAX_CREDENTIALS = 100
        private const val DUMMY_BYTE_CD = 0xCD.toByte()
        private const val PATH_ARG_INDEX_2 = 2
    }

    private val realSeed = ByteArray(SEED_SIZE_32) { it.toByte() }
    private val realDeviceKeyPair: HdkKeyPair by lazy {
        P256Group.generateKeyPair().let {
            HdkKeyPair(P256Group.serializeScalar(it.first), P256Group.serializeElement(it.second))
        }
    }

    @BeforeTest
    fun setUp() {
        Security.addProvider(BouncyCastleProvider())
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>(), any()) } returns 0
        every { android.util.Log.i(any(), any()) } returns 0

        // 1. Database Setup
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Fido2Database.Schema.create(driver)
        database = Fido2Database(driver)

        // 2. DAO Setup
        val passkeyDao = PasskeyCredentialDao(database, timeProvider)
        val rpDao = RelyingPartyDao(database, timeProvider)
        val consentDao = UserConsentRecordDao(database)

        // 3. Crypto Setup
        val masterSeedProvider: MasterSeedProvider =
            mockk {
                coEvery { getMasterSeed() } returns realSeed
                coEvery { getDeviceKeyPair() } returns realDeviceKeyPair
                coEvery { getPqChildSeed() } returns ByteArray(64)
            }

        val hdkManager: HdkManager =
            mockk {
                every { deriveHdk(any(), any(), any()) } answers {
                    val seed = arg<ByteArray>(1)
                    val path = arg<List<UInt>>(PATH_ARG_INDEX_2)
                    val digest = java.security.MessageDigest.getInstance("SHA-256")
                    path.forEach { idx -> digest.update((idx and 0xFFu).toByte()) }
                    val childScalar =
                        BigInteger(1, digest.digest(seed)).mod(P256Group.ORDER).let {
                            if (it == BigInteger.ZERO) BigInteger.ONE else it
                        }
                    val childPubKey = P256Group.G.multiply(childScalar).normalize()
                    HdkResult(
                        publicKey = P256Group.serializeElement(childPubKey),
                        salt = ByteArray(SEED_SIZE_32),
                        blindingFactor = P256Group.serializeScalar(childScalar),
                    )
                }
                every { blindPrivateKey(any(), any()) } answers {
                    val devicePriv = BigInteger(1, arg<ByteArray>(0))
                    val blindFactor = BigInteger(1, arg<ByteArray>(1))
                    val blindedScalar = devicePriv.multiply(blindFactor).mod(P256Group.ORDER)
                    P256Group.serializeScalar(blindedScalar)
                }
            }

        cryptoService =
            Fido2CryptoService(
                hdkManager = hdkManager,
                masterSeedProvider = masterSeedProvider,
                postQuantumCrypto = PostQuantumCrypto(),
                timeProvider = timeProvider,
                defaultDispatcher = testDispatcher,
            )

        // 4. Repository Setup
        val repairWorker: CorruptedKeyRepairWorker = mockk(relaxed = true)
        val publicKeyDecoder: com.chimali.fido2.data.crypto.PublicKeyDecoder =
            mockk {
                val dummyKey =
                    java.security.KeyPairGenerator
                        .getInstance(
                            "EC",
                        ).apply { initialize(256) }
                        .generateKeyPair()
                        .public
                every { decodePublicKey(any(), any()) } returns Outcome.Success(dummyKey)
            }

        val aggregateService: com.chimali.core.domain.eventsourcing.AggregateService<
            com.chimali.core.domain.eventsourcing.passkey.PasskeyCommand,
            com.chimali.core.domain.eventsourcing.passkey.PasskeyState,
        > =
            mockk {
                coEvery { execute(any(), any()) } returns Result.success(mockk(relaxed = true))
            }

        repository =
            CredentialRepositoryImpl(
                passkeyCredentialDao = passkeyDao,
                relyingPartyDao = rpDao,
                userConsentRecordDao = consentDao,
                cryptoService = cryptoService,
                publicKeyDecoder = publicKeyDecoder,
                corruptedKeyRepairWorker = repairWorker,
                timeProvider = timeProvider,
                aggregateService = aggregateService,
                ioDispatcher = testDispatcher,
            )

        // 5. UseCase Setup
        userVerificationService =
            mockk {
                coEvery { getUserVerificationAvailability() } returns
                    UserVerificationAvailability(
                        isBiometricAvailable = true,
                        isPinAvailable = true,
                        isDeviceLockAvailable = false,
                        supportedBiometricTypes = listOf(BiometricType.FINGERPRINT),
                        maxPinLength = 8,
                        minPinLength = 4,
                        biometricStrength = BiometricStrength.STRONG,
                    )
                coEvery {
                    isUserVerificationRequired(any(), any(), any())
                } returns ServiceVerificationRequirement.PREFERRED
                coEvery { recordUserConsent(any()) } returns Outcome.Success(Unit)
            }

        val settingsRepo: Fido2SettingsRepository =
            mockk {
                coEvery { getMaxCredentialCount() } returns MAX_CREDENTIALS
            }

        val clientDataHashService = ClientDataHashService()

        registerUseCase =
            RegisterCredentialUseCase(
                passkeyCredentialRepository = repository,
                userVerificationService = userVerificationService,
                cborCodec = CborCodec(),
                cryptoService = cryptoService,
                settingsRepository = settingsRepo,
                clientDataHashService = clientDataHashService,
            )

        assertionUseCase =
            GetAssertionUseCase(
                credentialRepository = repository,
                userVerificationService = userVerificationService,
                selectCredentialUseCase = SelectCredentialUseCase(),
                cryptoService = cryptoService,
                clientDataHashService = clientDataHashService,
            )
    }

    @AfterTest
    fun tearDown() {
        unmockkStatic(android.util.Log::class)
    }

    @Test
    fun `full registration and authentication flow with real data persistence`() =
        runTest {
            val rpIdHost = "data-integration.example.com"
            val rpId = RpId("https://$rpIdHost")
            val userId = UserId("user-123")

            // 1. Registration
            val makeOptions =
                MakeCredentialOptions.create(
                    rp = PublicKeyCredentialRpEntity.create(id = rpId, name = "Example"),
                    user = PublicKeyCredentialUserEntity.create(id = userId, name = "user", displayName = "User"),
                    challenge = ByteArray(CHALLENGE_SIZE_32) { it.toByte() },
                    pubKeyCredParams = PublicKeyCredentialParameters.createES256P256(),
                    selectedAlgId = Fido2CryptoService.COSE_ES256,
                )

            val regResult = registerUseCase(makeOptions)
            if (regResult is Outcome.Error) {
                println("Registration failed: ${regResult.error.message}")
                regResult.error.cause?.printStackTrace()
            }
            assertTrue(regResult.isSuccess, "Registration should succeed")

            val credential = regResult.getOrThrow().credential
            val credId = credential.id

            val savedCred = repository.getCredentialById(credId)
            assertTrue(savedCred != null, "Credential should be found in database")
            assertEquals(credId, savedCred.id)
            assertEquals(rpId, savedCred.rpId)

            // 2. Authentication
            val authOptions =
                GetAssertionOptions.create(
                    rpId = rpId,
                    clientDataHash = ByteArray(CHALLENGE_SIZE_32) { DUMMY_BYTE_CD },
                    userVerification = UserVerificationRequirement.PREFERRED,
                    allowCredentials = listOf(PublicKeyCredentialDescriptor.create(id = credId)),
                )

            val authResult = assertionUseCase(authOptions)
            assertTrue(authResult.isSuccess, "Authentication should succeed using persisted credential")

            val assertion = authResult.getOrThrow()
            assertEquals(credId.encoded, assertion.credentialId)
        }
}
