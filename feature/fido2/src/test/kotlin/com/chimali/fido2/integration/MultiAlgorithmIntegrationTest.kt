package com.chimali.fido2.integration

import com.chimali.core.security.api.HdkKeyPair
import com.chimali.core.security.api.HdkManager
import com.chimali.core.security.api.HdkResult
import com.chimali.core.security.hdkeys.P256Group
import com.chimali.fido2.data.crypto.CborCodec
import com.chimali.fido2.data.crypto.Fido2CryptoService
import com.chimali.fido2.data.crypto.MasterSeedProvider
import com.chimali.fido2.data.crypto.PostQuantumCrypto
import com.chimali.fido2.domain.model.CredentialSummary
import com.chimali.fido2.domain.model.GetAssertionOptions
import com.chimali.fido2.domain.model.MakeCredentialOptions
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.model.PublicKeyCredentialParameters
import com.chimali.fido2.domain.model.PublicKeyCredentialRpEntity
import com.chimali.fido2.domain.model.PublicKeyCredentialUserEntity
import com.chimali.fido2.domain.model.RelyingParty
import com.chimali.fido2.domain.model.UserConsentRecord
import com.chimali.fido2.domain.model.UserVerificationRequirement
import com.chimali.fido2.domain.repository.CredentialRepository
import com.chimali.fido2.domain.repository.CredentialStatistics
import com.chimali.fido2.domain.repository.Fido2SettingsRepository
import com.chimali.fido2.domain.service.BiometricStrength
import com.chimali.fido2.domain.service.BiometricType
import com.chimali.fido2.domain.service.UserVerificationAvailability
import com.chimali.fido2.domain.service.UserVerificationService
import com.chimali.fido2.domain.usecase.GetAssertionUseCase
import com.chimali.fido2.domain.usecase.RegisterCredentialUseCase
import com.chimali.fido2.domain.usecase.SelectCredentialUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test
import java.math.BigInteger
import java.security.Security
import java.util.concurrent.ConcurrentHashMap
import com.chimali.fido2.domain.service.UserVerificationRequirement as ServiceVerificationRequirement

class MultiAlgorithmIntegrationTest {
    private lateinit var cryptoService: Fido2CryptoService
    private lateinit var repository: MultiAlgInMemoryCredentialRepository
    private lateinit var registerUseCase: RegisterCredentialUseCase
    private lateinit var assertionUseCase: GetAssertionUseCase
    private lateinit var userVerificationService: UserVerificationService
    private val postQuantumCrypto = PostQuantumCrypto()

    private val realSeed = ByteArray(32) { it.toByte() }
    private val realPqSeed = ByteArray(64) { (it + 1).toByte() }
    private val realDeviceKeyPair: HdkKeyPair by lazy {
        P256Group.generateKeyPair().let { HdkKeyPair(P256Group.serializeScalar(it.first), P256Group.serializeElement(it.second)) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @BeforeTest
    fun setUp() {
        Security.addProvider(org.bouncycastle.jce.provider.BouncyCastleProvider())
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>(), any()) } returns 0
        every { android.util.Log.i(any(), any()) } returns 0

        val masterSeedProvider: MasterSeedProvider =
            mockk {
                coEvery { getMasterSeed() } returns realSeed
                coEvery { getDeviceKeyPair() } returns realDeviceKeyPair
                coEvery { getPqChildSeed() } returns realPqSeed
            }

        val hdkManager: HdkManager =
            mockk {
                every { deriveHdk(any(), any(), any()) } answers {
                    val seed = arg<ByteArray>(1)
                    val path = arg<List<UInt>>(2)
                    val digest = java.security.MessageDigest.getInstance("SHA-256")
                    path.forEach { idx -> digest.update((idx and 0xFFu).toByte()) }
                    val childScalar =
                        BigInteger(1, digest.digest(seed)).mod(P256Group.ORDER).let {
                            if (it == BigInteger.ZERO) BigInteger.ONE else it
                        }
                    val childPubKey = P256Group.G.multiply(childScalar).normalize()
                    HdkResult(
                        publicKey = P256Group.serializeElement(childPubKey),
                        salt = ByteArray(32),
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
        cryptoService = Fido2CryptoService(hdkManager, masterSeedProvider, postQuantumCrypto, UnconfinedTestDispatcher())

        repository = MultiAlgInMemoryCredentialRepository()

        userVerificationService =
            mockk {
                coEvery { getUserVerificationAvailability() } returns
                    UserVerificationAvailability(
                        biometricAvailable = true,
                        pinAvailable = true,
                        deviceLockAvailable = false,
                        supportedBiometricTypes = listOf(BiometricType.FINGERPRINT),
                        maxPinLength = 8,
                        minPinLength = 4,
                        biometricStrength = BiometricStrength.STRONG,
                    )
                coEvery { isUserVerificationRequired(any(), any(), any()) } returns ServiceVerificationRequirement.PREFERRED
                coEvery { recordUserConsent(any()) } returns Result.success(Unit)
            }

        val selectCredentialUseCase = SelectCredentialUseCase()

        val settingsRepository: Fido2SettingsRepository =
            mockk {
                coEvery { getMaxCredentialCount() } returns 1000
            }

        registerUseCase =
            RegisterCredentialUseCase(
                passkeyCredentialRepository = repository,
                userVerificationService = userVerificationService,
                cborCodec = CborCodec(),
                cryptoService = cryptoService,
                settingsRepository = settingsRepository,
            )
        assertionUseCase =
            GetAssertionUseCase(
                credentialRepository = repository,
                userVerificationService = userVerificationService,
                selectCredentialUseCase = selectCredentialUseCase,
                cryptoService = cryptoService,
            )
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(android.util.Log::class)
    }

    @Test
    fun `registration and authentication work for ECDSA`() =
        runTest {
            val options = buildMakeCredentialOptions("user-ecdsa", "example.com", Fido2CryptoService.COSE_ES256)
            val result = registerUseCase(options)
            assertTrue(result.isSuccess)

            val credential = result.getOrThrow().credential
            assertEquals(Fido2CryptoService.COSE_ES256, credential.coseAlgorithm)

            val authOptions = buildGetAssertionOptions("https://example.com", credential.credentialId)
            val authResult = assertionUseCase(authOptions)
            assertTrue(authResult.isSuccess)
        }

    @Test
    fun `registration and authentication work for ML-DSA-65`() =
        runTest {
            val options = buildMakeCredentialOptions("user-mldsa", "mldsa.com", Fido2CryptoService.COSE_ML_DSA_65)
            val result = registerUseCase(options)
            assertTrue(result.isSuccess)

            val credential = result.getOrThrow().credential
            assertEquals(Fido2CryptoService.COSE_ML_DSA_65, credential.coseAlgorithm)

            // ML-DSA-65 public key should be quite large (>1900 bytes)
            assertTrue(credential.publicKey.encoded.size > 1900)

            val authOptions = buildGetAssertionOptions("https://mldsa.com", credential.credentialId)
            val authResult = assertionUseCase(authOptions)
            assertTrue(authResult.isSuccess)

            // ML-DSA signature should be very large too (>3000 bytes)
            val assertionObject = authResult.getOrThrow()
            assertTrue(assertionObject.signature.size > 3000)
        }

    private fun buildMakeCredentialOptions(
        userId: String,
        rpIdHost: String,
        algId: Int,
    ): MakeCredentialOptions {
        val rp = PublicKeyCredentialRpEntity.create(id = "https://$rpIdHost", name = rpIdHost)
        val user =
            PublicKeyCredentialUserEntity.create(
                id = userId.toByteArray(),
                name = userId,
                displayName = userId,
            )
        val params = if (algId == Fido2CryptoService.COSE_ML_DSA_65) PublicKeyCredentialParameters.createMlDsa65() else PublicKeyCredentialParameters.createES256P256()
        return MakeCredentialOptions.create(
            rp = rp,
            user = user,
            challenge = ByteArray(32) { (it + 1).toByte() },
            pubKeyCredParams = params,
            selectedAlgId = algId,
        )
    }

    private fun buildGetAssertionOptions(
        rpId: String,
        credId: ByteArray,
    ): GetAssertionOptions {
        val allowList = listOf(com.chimali.fido2.domain.model.PublicKeyCredentialDescriptor.create(id = credId))
        return GetAssertionOptions.create(
            rpId = rpId,
            clientDataHash = ByteArray(32) { 0xCD.toByte() },
            userVerification = UserVerificationRequirement.PREFERRED,
            allowCredentials = allowList,
        )
    }
}

/**
 * Thread-safe in-memory implementation of [CredentialRepository] for use in tests.
 */
private class MultiAlgInMemoryCredentialRepository : CredentialRepository {
    private val credentials = ConcurrentHashMap<String, PasskeyCredential>()
    private val signCounts = ConcurrentHashMap<String, Long>()

    fun getAllSummariesForRp(rpId: String): List<CredentialSummary> =
        credentials.values
            .filter { it.rpId == rpId }
            .map {
                CredentialSummary(
                    id = it.id,
                    rpId = it.rpId,
                    credentialId = it.credentialId,
                    lastUsedAt = it.lastUsedAt,
                    coseAlgorithm = it.coseAlgorithm,
                )
            }

    override suspend fun saveCredential(credential: PasskeyCredential): Result<Unit> {
        credentials[credential.id] = credential
        signCounts[credential.id] = 0L
        return Result.success(Unit)
    }

    override suspend fun getCredentialById(credentialId: String): PasskeyCredential? = credentials[credentialId]

    override suspend fun getCredentialsByRpId(rpId: String): Flow<PasskeyCredential> =
        flowOf(
            *credentials.values.filter {
                it.rpId == rpId
            }.toTypedArray(),
        )

    override suspend fun getCredentialsByUserId(userId: String): Flow<PasskeyCredential> =
        flowOf(
            *credentials.values.filter {
                it.userId == userId
            }.toTypedArray(),
        )

    override suspend fun getAllCredentials(): Flow<PasskeyCredential> = flowOf(*credentials.values.toTypedArray())

    override suspend fun updateSignCount(
        credentialId: String,
        newSignCount: Long,
    ): Result<Unit> {
        signCounts[credentialId] = newSignCount
        return Result.success(Unit)
    }

    override suspend fun updateLastUsedAt(credentialId: String): Result<Unit> = Result.success(Unit)

    override suspend fun deleteCredential(credentialId: String): Result<Unit> = Result.success(Unit)

    override suspend fun credentialExists(
        rpId: String,
        userId: String,
    ): Boolean = false

    override suspend fun getExpiredCredentials(maxAgeDays: Long): Flow<PasskeyCredential> = emptyFlow()

    override suspend fun getCredentialCountByRpId(rpId: String): Int = 0

    override suspend fun getRecentlyUnusedCredentials(days: Long): Flow<PasskeyCredential> = emptyFlow()

    override suspend fun searchCredentials(query: String): Flow<PasskeyCredential> = emptyFlow()

    override suspend fun validateCredentialCreation(
        rpId: String,
        userId: String,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun getCredentialsRequiringUserVerification(): Flow<PasskeyCredential> = emptyFlow()

    override suspend fun saveRelyingParty(rp: RelyingParty): Result<Unit> = Result.success(Unit)

    override suspend fun updateRelyingParty(
        rpId: String,
        update: (RelyingParty) -> RelyingParty,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun getRelyingParty(rpId: String): RelyingParty? = null

    override suspend fun saveUserConsent(consent: UserConsentRecord): Result<Unit> = Result.success(Unit)

    override suspend fun getRecentUserConsent(
        rpId: String?,
        limit: Int,
    ): Flow<UserConsentRecord> = emptyFlow()

    override suspend fun isUserConsentRequired(
        rpId: String,
        operationType: String,
    ): Boolean = false

    override suspend fun getCredentialStatistics(): CredentialStatistics = CredentialStatistics(0, emptyMap(), 0, 0, 0, 0.0)

    override suspend fun getCredentialsForRp(rpId: String): Result<List<PasskeyCredential>> = Result.success(emptyList())

    override suspend fun getCredentialSummariesForRp(rpId: String): Result<List<CredentialSummary>> =
        Result.success(
            getAllSummariesForRp(rpId),
        )

    override suspend fun getSignCount(credentialId: String): Result<Long> = Result.success(signCounts[credentialId] ?: 0L)

    override suspend fun getCredentialsByIds(
        credentialIds: Set<String>,
        rpId: String?,
    ): Result<List<PasskeyCredential>> =
        Result.success(
            emptyList(),
        )

    override suspend fun cleanupExpiredCredentials(maxAgeDays: Long): Result<Int> = Result.success(0)

    override suspend fun deleteAllCredentials(rpId: String?): Result<Unit> = Result.success(Unit)

    override suspend fun resetAuthenticator(): Result<Unit> = Result.success(Unit)

    override suspend fun updateLabel(
        credentialId: String,
        label: String?,
    ): Result<Unit> {
        credentials[credentialId]?.let {
            credentials[credentialId] = it.copy(label = label)
        }
        return Result.success(Unit)
    }
}
