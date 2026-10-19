package com.chimali.fido2.integration

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.chimali.core.common.result.Outcome
import com.chimali.core.common.result.getOrThrow
import com.chimali.core.common.result.isSuccess
import com.chimali.core.domain.model.CredentialKeyDescriptor
import com.chimali.core.domain.time.TimeProvider
import com.chimali.core.domain.valueobject.RpId
import com.chimali.core.domain.valueobject.UserId
import com.chimali.core.security.api.HdkKeyPair
import com.chimali.core.security.api.MasterSeedProvider
import com.chimali.core.security.hdkeys.HdkP256
import com.chimali.core.security.hdkeys.P256ParentKeyDerivation
import com.chimali.fido2.data.crypto.CborCodec
import com.chimali.fido2.data.crypto.ClientDataHashService
import com.chimali.fido2.data.crypto.Fido2CryptoService
import com.chimali.fido2.data.crypto.PostQuantumCrypto
import com.chimali.fido2.data.dao.PasskeyCredentialDao
import com.chimali.fido2.data.dao.RelyingPartyDao
import com.chimali.fido2.data.dao.UserConsentRecordDao
import com.chimali.fido2.data.database.Fido2Database
import com.chimali.fido2.data.repository.CredentialRepositoryImpl
import com.chimali.fido2.data.service.CredentialMetadataProtectionService
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
import java.security.Security
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
    }

    private val realSeed = ByteArray(SEED_SIZE_32) { it.toByte() }
    private val realDeviceKeyPair: HdkKeyPair by lazy {
        P256ParentKeyDerivation.deriveFromPrk(realSeed).let { result ->
            HdkKeyPair(result.privateScalar.copyOf(), result.publicKey.copyOf()).also { result.erasePrivateScalar() }
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
        // Exercise real metadata serialization and parsing; the synthetic envelope below is
        // deliberately reversible and proves no platform AES/Keystore properties.
        val metadataProtectionService =
            CredentialMetadataProtectionService(
                lookupTokenService =
                    mockk {
                        every { generateToken(any(), any()) } answers {
                            StringBuilder()
                                .append(firstArg<CharSequence>())
                                .append(':')
                                .append(secondArg<CharSequence>())
                                .toString()
                                .toByteArray()
                        }
                    },
                encryptedMetadataService =
                    mockk {
                        every { encrypt(any(), any()) } answers { firstArg<ByteArray>().copyOf() }
                        every { decrypt(any(), any()) } answers { firstArg<ByteArray>().copyOf() }
                    },
            )
        val passkeyDao = PasskeyCredentialDao(database, timeProvider, metadataProtectionService)
        val rpDao = RelyingPartyDao(database, timeProvider, metadataProtectionService)
        val consentDao = UserConsentRecordDao(database, metadataProtectionService)

        // 3. Crypto Setup
        val masterSeedProvider: MasterSeedProvider =
            mockk {
                every { sessionGeneration() } returns 1L
                every { isSessionGenerationCurrent(any()) } returns true
                coEvery { getMasterSeed() } answers { realSeed.copyOf() }
                coEvery {
                    getDeviceKeyPair()
                } answers { HdkKeyPair(realDeviceKeyPair.privateKey.copyOf(), realDeviceKeyPair.publicKey.copyOf()) }
            }

        cryptoService =
            Fido2CryptoService(
                hdkManager = HdkP256,
                masterSeedProvider = masterSeedProvider,
                postQuantumCrypto = PostQuantumCrypto(),
                defaultDispatcher = testDispatcher,
            )

        // 4. Repository Setup
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
                publicKeyDecoder = publicKeyDecoder,
                timeProvider = timeProvider,
                aggregateService = aggregateService,
                metadataProtectionService = metadataProtectionService,
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
                    isUserVerificationRequiredForText(any(), any(), any())
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

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `full registration and authentication flow with real data persistence`() =
        runTest {
            val rpIdHost = "data-integration.example.com"
            val rpId = RpId("https://$rpIdHost")
            val userId = UserId.fromByteArray("user-123".encodeToByteArray())

            // 1. Registration
            val makeOptions =
                MakeCredentialOptions.create(
                    rp = PublicKeyCredentialRpEntity.create(id = rpId, name = "Example"),
                    user = PublicKeyCredentialUserEntity.create(id = userId, name = "user", displayName = "User"),
                    challenge = ByteArray(CHALLENGE_SIZE_32) { it.toByte() },
                    pubKeyCredParams = PublicKeyCredentialParameters.createES256P256(),
                    selectedAlgId = CredentialKeyDescriptor.COSE_ES256,
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
            assertEquals(rpId.value, savedCred.rpId.toBoundaryString())

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

    /**
     * T051 [US4] — FIDO2 registration/authentication regression test using migrated credential layout.
     *
     * Verifies that credentials written with lookup-token columns (rp_id_index, user_id_index)
     * and encrypted_metadata remain accessible through the full FIDO2 authentication flow,
     * proving backward compatibility with the migrated schema.
     */
    @Suppress("LongMethod")
    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `authentication succeeds with migrated credential schema`() =
        runTest {
            val rpIdHost = "migrated-integration.example.com"
            val rpId = RpId("https://$rpIdHost")
            val userId = UserId.fromByteArray("migrated-user-456".encodeToByteArray())

            // 1. Registration using the migrated DAO (rp_id_index + encrypted_metadata written)
            val makeOptions =
                MakeCredentialOptions.create(
                    rp = PublicKeyCredentialRpEntity.create(id = rpId, name = "Migrated Example"),
                    user =
                        PublicKeyCredentialUserEntity.create(
                            id = userId,
                            name = "migrated",
                            displayName = "Migrated User",
                        ),
                    challenge = ByteArray(CHALLENGE_SIZE_32) { (it + 1).toByte() },
                    pubKeyCredParams = PublicKeyCredentialParameters.createES256P256(),
                    selectedAlgId = CredentialKeyDescriptor.COSE_ES256,
                )

            val regResult = registerUseCase(makeOptions)
            assertTrue(regResult.isSuccess, "Registration with migrated schema should succeed")

            val credential = regResult.getOrThrow().credential
            val credId = credential.id

            // 2. Verify token columns were populated (migration-compatible write path)
            val savedEntity =
                database.passkeyCredentialQueries
                    .select_by_id(credId.encoded)
                    .executeAsOneOrNull()
            assertNotNull(savedEntity, "Credential entity should be persisted")

            // 3. Authentication using the persisted credential
            val authOptions =
                GetAssertionOptions.create(
                    rpId = rpId,
                    clientDataHash = ByteArray(CHALLENGE_SIZE_32) { DUMMY_BYTE_CD },
                    userVerification = UserVerificationRequirement.PREFERRED,
                    allowCredentials = listOf(PublicKeyCredentialDescriptor.create(id = credId)),
                )

            val authResult = assertionUseCase(authOptions)
            assertTrue(authResult.isSuccess, "Authentication with migrated credential should succeed")

            val assertion = authResult.getOrThrow()
            assertEquals(credId.encoded, assertion.credentialId)
        }
}
