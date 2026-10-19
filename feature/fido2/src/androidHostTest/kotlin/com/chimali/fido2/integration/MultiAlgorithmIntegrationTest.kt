package com.chimali.fido2.integration

import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.common.result.getOrThrow
import com.chimali.core.common.result.isSuccess
import com.chimali.core.domain.model.CredentialKeyDescriptor
import com.chimali.core.domain.model.CredentialSummary
import com.chimali.core.domain.model.RelyingParty
import com.chimali.core.domain.model.UserConsentRecord
import com.chimali.core.domain.valueobject.CredentialId
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
import com.chimali.fido2.domain.model.GetAssertionOptions
import com.chimali.fido2.domain.model.MakeCredentialOptions
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.model.PublicKeyCredentialDescriptor
import com.chimali.fido2.domain.model.PublicKeyCredentialParameters
import com.chimali.fido2.domain.model.PublicKeyCredentialRpEntity
import com.chimali.fido2.domain.model.PublicKeyCredentialUserEntity
import com.chimali.fido2.domain.model.SensitiveCredentialText
import com.chimali.fido2.domain.model.UserVerificationRequirement
import com.chimali.fido2.domain.repository.CredentialRepository
import com.chimali.fido2.domain.repository.CredentialStatistics
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
import java.security.Signature
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MultiAlgorithmIntegrationTest {
    private lateinit var cryptoService: Fido2CryptoService
    private lateinit var repository: MultiAlgInMemoryCredentialRepository
    private lateinit var registerUseCase: RegisterCredentialUseCase
    private lateinit var assertionUseCase: GetAssertionUseCase
    private lateinit var userVerificationService: UserVerificationService
    private val postQuantumCrypto = PostQuantumCrypto()

    companion object {
        private const val SEED_SIZE_32 = 32
        private const val MAX_PIN_LENGTH = 8
        private const val MIN_PIN_LENGTH = 4
        private const val MAX_CREDENTIALS = 1000
        private const val MIN_ML_DSA_PUB_KEY_SIZE_1900 = 1900
        private const val MIN_ML_DSA_SIG_SIZE_3000 = 3000
        private const val DUMMY_BYTE_CD = 0xCD.toByte()
    }

    private val realSeed = ByteArray(SEED_SIZE_32) { it.toByte() }
    private val realDeviceKeyPair: HdkKeyPair by lazy {
        P256ParentKeyDerivation.deriveFromPrk(realSeed).let { result ->
            HdkKeyPair(result.privateScalar.copyOf(), result.publicKey.copyOf()).also { result.erasePrivateScalar() }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @BeforeTest
    fun setUp() {
        Security.addProvider(
            org.bouncycastle.jce.provider
                .BouncyCastleProvider(),
        )
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>(), any()) } returns 0
        every { android.util.Log.i(any(), any()) } returns 0

        val masterSeedProvider: MasterSeedProvider =
            mockk {
                every { sessionGeneration() } returns 1L
                every { isSessionGenerationCurrent(any()) } returns true
                coEvery { getMasterSeed() } answers { realSeed.copyOf() }
                coEvery { getDeviceKeyPair() } answers {
                    HdkKeyPair(realDeviceKeyPair.privateKey.copyOf(), realDeviceKeyPair.publicKey.copyOf())
                }
            }

        cryptoService =
            Fido2CryptoService(
                hdkManager = HdkP256,
                masterSeedProvider = masterSeedProvider,
                postQuantumCrypto = postQuantumCrypto,
                defaultDispatcher = UnconfinedTestDispatcher(),
            )

        repository = MultiAlgInMemoryCredentialRepository()

        userVerificationService =
            mockk {
                coEvery { getUserVerificationAvailability() } returns
                    UserVerificationAvailability(
                        isBiometricAvailable = true,
                        isPinAvailable = true,
                        isDeviceLockAvailable = false,
                        supportedBiometricTypes = listOf(BiometricType.FINGERPRINT),
                        maxPinLength = MAX_PIN_LENGTH,
                        minPinLength = MIN_PIN_LENGTH,
                        biometricStrength = BiometricStrength.STRONG,
                    )
                coEvery {
                    isUserVerificationRequiredForText(any(), any(), any())
                } returns ServiceVerificationRequirement.PREFERRED
                coEvery { recordUserConsent(any()) } returns Outcome.Success(Unit)
            }

        val selectCredentialUseCase = SelectCredentialUseCase()

        val settingsRepository: Fido2SettingsRepository =
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
                settingsRepository = settingsRepository,
                clientDataHashService = clientDataHashService,
            )
        assertionUseCase =
            GetAssertionUseCase(
                credentialRepository = repository,
                userVerificationService = userVerificationService,
                selectCredentialUseCase = selectCredentialUseCase,
                cryptoService = cryptoService,
                clientDataHashService = clientDataHashService,
            )
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(android.util.Log::class)
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `registration and authentication work for ECDSA`() =
        runTest {
            val options = buildMakeCredentialOptions("user-ecdsa", "example.com", CredentialKeyDescriptor.COSE_ES256)
            val result = registerUseCase(options)
            assertTrue(result.isSuccess)

            val credential = result.getOrThrow().credential
            assertEquals(CredentialKeyDescriptor.COSE_ES256, credential.coseAlgorithm)
            assertEquals(CredentialKeyDescriptor.P256_PROFILE, credential.derivationProfile)

            val authOptions = buildGetAssertionOptions("https://example.com", credential.id)
            val authResult = assertionUseCase(authOptions)
            assertTrue(authResult.isSuccess)
            assertCeremonySignaturesVerify(result.getOrThrow(), authResult.getOrThrow(), authOptions)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `registration and authentication work for Ed25519`() =
        runTest {
            val options = buildMakeCredentialOptions("user-ed25519", "ed25519.com", CredentialKeyDescriptor.COSE_EDDSA)
            val result = registerUseCase(options)
            assertTrue(result.isSuccess)

            val credential = result.getOrThrow().credential
            assertEquals(CredentialKeyDescriptor.COSE_EDDSA, credential.coseAlgorithm)
            assertEquals(CredentialKeyDescriptor.ED25519_PROFILE, credential.derivationProfile)

            val authOptions = buildGetAssertionOptions("https://ed25519.com", credential.id)
            val authResult = assertionUseCase(authOptions)
            assertTrue(authResult.isSuccess)
            assertCeremonySignaturesVerify(result.getOrThrow(), authResult.getOrThrow(), authOptions)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `registration and authentication work for ML-DSA-65`() =
        runTest {
            val options = buildMakeCredentialOptions("user-mldsa", "mldsa.com", CredentialKeyDescriptor.COSE_ML_DSA_65)
            val result = registerUseCase(options)
            assertTrue(result.isSuccess)

            val credential = result.getOrThrow().credential
            assertEquals(CredentialKeyDescriptor.COSE_ML_DSA_65, credential.coseAlgorithm)
            assertEquals(CredentialKeyDescriptor.MLDSA65_PROFILE, credential.derivationProfile)

            // ML-DSA-65 public key should be quite large (>1900 bytes)
            assertTrue(credential.publicKey.encoded.size > MIN_ML_DSA_PUB_KEY_SIZE_1900)

            val authOptions = buildGetAssertionOptions("https://mldsa.com", credential.id)
            val authResult = assertionUseCase(authOptions)
            assertTrue(authResult.isSuccess)

            // ML-DSA signature should be very large too (>3000 bytes)
            val assertionObject = authResult.getOrThrow()
            assertTrue(assertionObject.signature.size > MIN_ML_DSA_SIG_SIZE_3000)
            assertCeremonySignaturesVerify(result.getOrThrow(), assertionObject, authOptions)
        }

    private fun assertCeremonySignaturesVerify(
        registration: com.chimali.fido2.domain.model.MakeCredentialResult,
        assertion: com.chimali.fido2.domain.model.AssertionObject,
        assertionOptions: GetAssertionOptions,
    ) {
        val credential = registration.credential
        val signatureAlgorithm =
            when (credential.coseAlgorithm) {
                CredentialKeyDescriptor.COSE_ES256 -> "SHA256withECDSA"
                CredentialKeyDescriptor.COSE_EDDSA -> "Ed25519"
                CredentialKeyDescriptor.COSE_ML_DSA_65 -> "ML-DSA-65"
                else -> error("Unexpected test algorithm ${credential.coseAlgorithm}")
            }

        fun verify(
            message: ByteArray,
            signature: ByteArray,
        ) {
            val verifier = Signature.getInstance(signatureAlgorithm, BouncyCastleProvider.PROVIDER_NAME)
            verifier.initVerify(credential.publicKey)
            verifier.update(message)
            assertTrue(verifier.verify(signature), "The $signatureAlgorithm ceremony signature must verify")
        }

        val attestationStatement = registration.attestationObject.attStmt
        assertEquals(credential.coseAlgorithm, (attestationStatement.alg as Number).toInt())
        verify(
            requireNotNull(attestationStatement.authData) + registration.attestationObject.clientData.challenge,
            requireNotNull(attestationStatement.attCert),
        )
        verify(assertion.authData + assertionOptions.clientDataHash, assertion.signature)
    }

    private fun buildMakeCredentialOptions(
        userId: String,
        rpIdHost: String,
        algId: Int,
    ): MakeCredentialOptions {
        val rp = PublicKeyCredentialRpEntity.create(id = RpId("https://$rpIdHost"), name = rpIdHost)
        val user =
            PublicKeyCredentialUserEntity.create(
                id = UserId.fromByteArray(userId.encodeToByteArray()),
                name = userId,
                displayName = userId,
            )
        val params =
            when (algId) {
                CredentialKeyDescriptor.COSE_ML_DSA_65 -> PublicKeyCredentialParameters.createMlDsa65()
                CredentialKeyDescriptor.COSE_EDDSA -> PublicKeyCredentialParameters.createEdDsa()
                else -> PublicKeyCredentialParameters.createES256P256()
            }
        return MakeCredentialOptions.create(
            rp = rp,
            user = user,
            challenge = ByteArray(SEED_SIZE_32) { (it + 1).toByte() },
            pubKeyCredParams = params,
            selectedAlgId = algId,
        )
    }

    private fun buildGetAssertionOptions(
        rpId: String,
        credId: CredentialId,
    ): GetAssertionOptions {
        val allowList =
            listOf(
                PublicKeyCredentialDescriptor
                    .create(id = credId),
            )
        return GetAssertionOptions.create(
            rpId = RpId(rpId),
            clientDataHash = ByteArray(SEED_SIZE_32) { DUMMY_BYTE_CD },
            userVerification = UserVerificationRequirement.PREFERRED,
            allowCredentials = allowList,
        )
    }
}

/**
 * Thread-safe in-memory implementation of [CredentialRepository] for use in tests.
 */
private class MultiAlgInMemoryCredentialRepository : CredentialRepository {
    override suspend fun getCredentialSummariesForRpText(
        rpId: CharSequence,
    ): Outcome<List<CredentialSummary>, DomainError> =
        Outcome.Success(
            credentials.values.filter { it.rpId.contentEquals(rpId, ignoreCase = true) }.map {
                CredentialSummary.fromText(
                    it.id.encoded,
                    it.rpId,
                    it.id,
                    it.lastUsedAt,
                    it.coseAlgorithm,
                    it.derivationProfile,
                    it.storedPublicKeyEncoding,
                    it.credProtectPolicy,
                )
            },
        )

    override suspend fun validateCredentialCreationForText(
        rpId: CharSequence,
        userId: CharSequence,
    ): Outcome<Unit, DomainError> = Outcome.Success(Unit)

    override suspend fun getRelyingPartyForText(rpId: CharSequence): RelyingParty? = null

    private val credentials = ConcurrentHashMap<String, PasskeyCredential>()
    private val signCounts = ConcurrentHashMap<String, Long>()

    fun getAllSummariesForRp(rpId: RpId): List<CredentialSummary> =
        credentials.values
            .asSequence()
            .filter { it.belongsToRelyingParty(rpId) }
            .map {
                CredentialSummary(
                    id = it.id.encoded,
                    rpId = it.rpId.toRpId(),
                    credentialId = it.id,
                    lastUsedAt = it.lastUsedAt,
                    coseAlgorithm = it.coseAlgorithm,
                    derivationProfile = it.derivationProfile,
                    storedPublicKeyEncoding = it.storedPublicKeyEncoding,
                )
            }.toList()

    override suspend fun saveCredential(credential: PasskeyCredential): Outcome<Unit, DomainError> {
        credentials[credential.id.encoded] = credential.copyWithIndependentSensitiveText()
        signCounts[credential.id.encoded] = 0L
        return Outcome.Success(Unit)
    }

    override suspend fun getCredentialById(credentialId: CredentialId): PasskeyCredential? =
        credentials[credentialId.encoded]

    override fun getCredentialsByRpId(rpId: RpId): Flow<PasskeyCredential> =
        flowOf(
            *credentials.values
                .filter {
                    it.belongsToRelyingParty(rpId)
                }.toTypedArray(),
        )

    override fun getCredentialsByUserId(userId: UserId): Flow<PasskeyCredential> =
        flowOf(
            *credentials.values
                .filter {
                    it.belongsToUser(userId)
                }.toTypedArray(),
        )

    override fun getAllCredentials(): Flow<PasskeyCredential> = flowOf(*credentials.values.toTypedArray())

    override suspend fun getPagedCredentials(
        limit: Long,
        offset: Long,
    ): Outcome<List<PasskeyCredential>, DomainError> {
        val list =
            credentials.values
                .asSequence()
                .drop(offset.toInt())
                .take(limit.toInt())
                .toList()
        return Outcome.Success(list)
    }

    override suspend fun getPagedCredentialsByRpId(
        rpId: RpId,
        limit: Long,
        offset: Long,
    ): Outcome<List<PasskeyCredential>, DomainError> {
        val list =
            credentials.values
                .asSequence()
                .filter { it.belongsToRelyingParty(rpId) }
                .drop(offset.toInt())
                .take(limit.toInt())
                .toList()
        return Outcome.Success(list)
    }

    override suspend fun updateSignCount(
        credentialId: CredentialId,
        newSignCount: Long,
    ): Outcome<Unit, DomainError> {
        signCounts[credentialId.encoded] = newSignCount
        return Outcome.Success(Unit)
    }

    override suspend fun updateLastUsedAt(credentialId: CredentialId): Outcome<Unit, DomainError> =
        Outcome.Success(
            Unit,
        )

    override suspend fun deleteCredential(credentialId: CredentialId): Outcome<Unit, DomainError> =
        Outcome.Success(
            Unit,
        )

    override suspend fun credentialExists(
        rpId: RpId,
        userId: UserId,
    ): Boolean = false

    override fun getExpiredCredentials(maxAgeDays: Long): Flow<PasskeyCredential> = emptyFlow()

    override suspend fun getCredentialCountByRpId(rpId: RpId): Int = 0

    override fun getRecentlyUnusedCredentials(days: Long): Flow<PasskeyCredential> = emptyFlow()

    override fun searchCredentials(query: SensitiveCredentialText): Flow<PasskeyCredential> = emptyFlow()

    override suspend fun validateCredentialCreation(
        rpId: RpId,
        userId: UserId,
    ): Outcome<Unit, DomainError> = Outcome.Success(Unit)

    override fun getCredentialsRequiringUserVerification(): Flow<PasskeyCredential> = emptyFlow()

    override suspend fun saveRelyingParty(rp: RelyingParty): Outcome<Unit, DomainError> = Outcome.Success(Unit)

    override suspend fun updateRelyingParty(
        rpId: RpId,
        update: (RelyingParty) -> RelyingParty,
    ): Outcome<Unit, DomainError> = Outcome.Success(Unit)

    override suspend fun getRelyingParty(rpId: RpId): RelyingParty? = null

    override suspend fun saveUserConsent(consent: UserConsentRecord): Outcome<Unit, DomainError> = Outcome.Success(Unit)

    override fun getRecentUserConsent(
        rpId: RpId?,
        limit: Int,
    ): Flow<UserConsentRecord> = emptyFlow()

    override suspend fun isUserConsentRequired(
        rpId: RpId,
        operationType: String,
    ): Boolean = false

    override suspend fun getCredentialStatistics(): CredentialStatistics =
        CredentialStatistics(
            0,
            0,
            0,
            0,
            0.0,
        )

    override suspend fun getCredentialsForRp(rpId: RpId): Outcome<List<PasskeyCredential>, DomainError> =
        Outcome.Success(
            emptyList(),
        )

    override suspend fun getCredentialSummariesForRp(rpId: RpId): Outcome<List<CredentialSummary>, DomainError> =
        Outcome.Success(
            getAllSummariesForRp(rpId),
        )

    override suspend fun getSignCount(credentialId: CredentialId): Outcome<Long, DomainError> =
        Outcome.Success(
            signCounts[credentialId.encoded] ?: 0L,
        )

    override suspend fun getCredentialsByIds(
        credentialIds: Set<CredentialId>,
        rpId: RpId?,
    ): Outcome<List<PasskeyCredential>, DomainError> =
        Outcome.Success(
            emptyList(),
        )

    override suspend fun cleanupExpiredCredentials(maxAgeDays: Long): Outcome<Int, DomainError> = Outcome.Success(0)

    override suspend fun deleteAllCredentials(rpId: RpId?): Outcome<Unit, DomainError> = Outcome.Success(Unit)

    override suspend fun resetAuthenticator(): Outcome<Unit, DomainError> = Outcome.Success(Unit)

    override suspend fun updateLabel(
        credentialId: CredentialId,
        label: String?,
    ): Outcome<Unit, DomainError> {
        credentials[credentialId.encoded]?.let {
            credentials[credentialId.encoded] = it.copy(label = label)
        }
        return Outcome.Success(Unit)
    }
}
