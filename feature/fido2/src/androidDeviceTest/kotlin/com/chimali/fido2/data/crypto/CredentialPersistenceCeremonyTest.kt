package com.chimali.fido2.data.crypto

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chimali.core.common.datastore.EncryptionWrapper
import com.chimali.core.common.datastore.createUserPreferencesDataStore
import com.chimali.core.common.result.Outcome
import com.chimali.core.common.result.getOrThrow
import com.chimali.core.domain.model.CredentialKeyDescriptor
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.RpId
import com.chimali.core.domain.valueobject.UserId
import com.chimali.core.security.api.MasterSeedOwnerKind
import com.chimali.core.security.hdkeys.HdkP256
import com.chimali.core.security.impl.AesGcmEncryptedMetadataService
import com.chimali.core.security.impl.Bip39MasterSeedGenerator
import com.chimali.core.security.impl.HmacMetadataLookupTokenService
import com.chimali.fido2.domain.model.GetAssertionOptions
import com.chimali.fido2.domain.model.MakeCredentialOptions
import com.chimali.fido2.domain.model.PublicKeyCredentialDescriptor
import com.chimali.fido2.domain.model.PublicKeyCredentialParameters
import com.chimali.fido2.domain.model.PublicKeyCredentialRpEntity
import com.chimali.fido2.domain.model.PublicKeyCredentialUserEntity
import com.chimali.fido2.domain.model.UserVerificationRequirement
import com.chimali.fido2.domain.usecase.GetAssertionUseCase
import com.chimali.fido2.domain.usecase.RegisterCredentialUseCase
import com.chimali.fido2.domain.usecase.SelectCredentialUseCase
import java.io.File
import java.security.Security
import java.security.Signature
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CredentialPersistenceCeremonyTest {
    private companion object {
        const val RECOVERY_STORE_RELATIVE_PATH = "hdk-device-tests/%s/user-preferences.pb"
        const val REGISTRATION_CHALLENGE_SIZE = 32
        const val REGISTRATION_CHALLENGE_START = 1
        const val ASSERTION_CHALLENGE_SIZE = 32
        const val ASSERTION_CHALLENGE_START = 0x40
        const val MISMATCH_CREDENTIAL_ID_START = 0x11
        const val UPDATE_PUBLIC_KEY_PARAMETER_COUNT = 2
    }

    private fun recoveryFile(
        filesDirectory: File,
        testId: String,
    ): File = File(filesDirectory, RECOVERY_STORE_RELATIVE_PATH.format(testId))

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun persistentCeremoniesRecoverForEverySupportedSignatureAlgorithm() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val testId = UUID.randomUUID().toString()
            val recoveryFile = recoveryFile(context.filesDir, testId)
            recoveryFile.parentFile?.mkdirs()
            val databaseName = "hdk-algorithm-ceremony-$testId.db"
            val lookupTokens = HmacMetadataLookupTokenService()
            val encryptedMetadata = AesGcmEncryptedMetadataService()
            val provider =
                WalletMasterSeedProvider(
                    masterSeedGenerator = Bip39MasterSeedGenerator(),
                    dataStore = createUserPreferencesDataStore { recoveryFile.absolutePath },
                    encryptionWrapper = EncryptionWrapper(),
                    lookupTokenService = lookupTokens,
                    encryptedMetadataService = encryptedMetadata,
                )
            val crypto = Fido2CryptoService(HdkP256, provider, PostQuantumCrypto(), Dispatchers.Default)
            val rpId = RpId("algorithm-recovery.example")
            val algorithmIds =
                listOf(
                    CredentialKeyDescriptor.COSE_ES256,
                    CredentialKeyDescriptor.COSE_EDDSA,
                    CredentialKeyDescriptor.COSE_ML_DSA_65,
                )
            val credentialIds = mutableListOf<CredentialId>()
            val publicKeys = mutableListOf<ByteArray>()
            var driver: app.cash.sqldelight.db.SqlDriver? = null
            try {
                var store =
                    createCredentialRepository(context, provider, databaseName, lookupTokens, encryptedMetadata)
                driver = store.first
                val registration =
                    RegisterCredentialUseCase(
                        passkeyCredentialRepository = store.second,
                        userVerificationService = NoUserVerification,
                        cborCodec = CborCodec(),
                        cryptoService = crypto,
                        settingsRepository = FixedCredentialLimit,
                        clientDataHashService = ClientDataHashService(),
                    )
                algorithmIds.forEachIndexed { position, algorithmId ->
                    val challenge = ByteArray(REGISTRATION_CHALLENGE_SIZE) { (position + it).toByte() }
                    var attestationMessage: ByteArray? = null
                    try {
                        val parameters =
                            when (algorithmId) {
                                CredentialKeyDescriptor.COSE_ES256 ->
                                    PublicKeyCredentialParameters
                                        .createES256P256()
                                CredentialKeyDescriptor.COSE_EDDSA -> PublicKeyCredentialParameters.createEdDsa()
                                CredentialKeyDescriptor.COSE_ML_DSA_65 ->
                                    PublicKeyCredentialParameters
                                        .createMlDsa65()
                                else -> error("Unsupported test algorithm: $algorithmId")
                            }
                        val result =
                            provider.withSessionOwner(ownerKind = MasterSeedOwnerKind.AUTHENTICATOR) {
                                registration(
                                    MakeCredentialOptions.create(
                                        rp = PublicKeyCredentialRpEntity.create(rpId, "Synthetic Algorithms"),
                                        user =
                                            PublicKeyCredentialUserEntity.create(
                                                UserId.fromByteArray("synthetic-user-$algorithmId".encodeToByteArray()),
                                                "synthetic-$algorithmId",
                                                "Synthetic Algorithm User",
                                            ),
                                        challenge = challenge,
                                        pubKeyCredParams = parameters,
                                        selectedAlgId = algorithmId,
                                    ),
                                ).getOrThrow()
                            }
                        val credential = result.credential
                        credentialIds += credential.id
                        publicKeys += credential.publicKey.encoded.copyOf()
                        val attestationStatement = result.attestationObject.attStmt
                        val signatureAlgorithm = signatureAlgorithm(algorithmId)
                        val attestationSignature = requireNotNull(attestationStatement.attCert)
                        attestationMessage = requireNotNull(attestationStatement.authData) + challenge
                        verifySignature(
                            signatureAlgorithm,
                            credential.publicKey,
                            requireNotNull(attestationMessage),
                            attestationSignature,
                        )
                        assertEquals(algorithmId, credential.coseAlgorithm)
                        assertEquals(profileForAlgorithm(algorithmId), credential.derivationProfile)
                    } finally {
                        challenge.fill(0)
                        attestationMessage?.fill(0)
                    }
                }

                driver.close()
                driver = null
                provider.clearSession()
                store = createCredentialRepository(context, provider, databaseName, lookupTokens, encryptedMetadata)
                driver = store.first
                val assertion =
                    GetAssertionUseCase(
                        credentialRepository = store.second,
                        userVerificationService = NoUserVerification,
                        selectCredentialUseCase = SelectCredentialUseCase(),
                        cryptoService = crypto,
                        clientDataHashService = ClientDataHashService(),
                    )

                algorithmIds.forEachIndexed { index, algorithmId ->
                    val credentialId = credentialIds[index]
                    val recovered = requireNotNull(store.second.getCredentialById(credentialId))
                    assertEquals(algorithmId, recovered.coseAlgorithm)
                    assertEquals(profileForAlgorithm(algorithmId), recovered.derivationProfile)
                    assertArrayEquals(publicKeys[index], recovered.publicKey.encoded)
                    val assertionChallenge =
                        ByteArray(ASSERTION_CHALLENGE_SIZE) { (index + ASSERTION_CHALLENGE_START + it).toByte() }
                    var assertionMessage: ByteArray? = null
                    var assertionSignature: ByteArray? = null
                    try {
                        val result =
                            provider.withSessionOwner(ownerKind = MasterSeedOwnerKind.AUTHENTICATOR) {
                                assertion(
                                    GetAssertionOptions.create(
                                        rpId = rpId,
                                        clientDataHash = assertionChallenge,
                                        allowCredentials =
                                            listOf(PublicKeyCredentialDescriptor.create(id = credentialId)),
                                        userVerification = UserVerificationRequirement.DISCOURAGED,
                                    ),
                                ).getOrThrow()
                            }
                        assertionMessage = result.authData + assertionChallenge
                        assertionSignature = result.signature
                        verifySignature(
                            signatureAlgorithm(algorithmId),
                            recovered.publicKey,
                            requireNotNull(assertionMessage),
                            requireNotNull(assertionSignature),
                        )
                        assertEquals(1L, store.second.getSignCount(credentialId).getOrThrow())
                    } finally {
                        assertionChallenge.fill(0)
                        assertionMessage?.fill(0)
                        assertionSignature?.fill(0)
                    }
                }
            } finally {
                provider.clearSession()
                driver?.close()
                context.deleteDatabase(databaseName)
                recoveryFile.parentFile?.deleteRecursively()
                publicKeys.forEach { it.fill(0) }
            }
        }

    private fun signatureAlgorithm(coseAlgorithm: Int): String =
        when (coseAlgorithm) {
            CredentialKeyDescriptor.COSE_ES256 -> "SHA256withECDSA"
            CredentialKeyDescriptor.COSE_EDDSA -> "Ed25519"
            CredentialKeyDescriptor.COSE_ML_DSA_65 -> "ML-DSA-65"
            else -> error("Unsupported test algorithm: $coseAlgorithm")
        }

    private fun profileForAlgorithm(coseAlgorithm: Int): String =
        when (coseAlgorithm) {
            CredentialKeyDescriptor.COSE_ES256 -> CredentialKeyDescriptor.P256_PROFILE
            CredentialKeyDescriptor.COSE_EDDSA -> CredentialKeyDescriptor.ED25519_PROFILE
            CredentialKeyDescriptor.COSE_ML_DSA_65 -> CredentialKeyDescriptor.MLDSA65_PROFILE
            else -> error("Unsupported test algorithm: $coseAlgorithm")
        }

    private fun verifySignature(
        algorithm: String,
        publicKey: java.security.PublicKey,
        message: ByteArray,
        signature: ByteArray,
    ) {
        val verifier =
            if (algorithm == "ML-DSA-65") {
                Signature.getInstance(algorithm, "BC")
            } else {
                val platformProvider =
                    Security
                        .getProviders()
                        .firstOrNull { provider ->
                            provider.name != "BC" && provider.getService("Signature", algorithm) != null
                        } ?: error("No non-BC platform verifier is available for $algorithm")
                Signature.getInstance(algorithm, platformProvider)
            }
        if (algorithm != "ML-DSA-65") {
            assertNotEquals("P-256 and Ed25519 use the platform verification provider", "BC", verifier.provider.name)
        }
        verifier.initVerify(publicKey)
        verifier.update(message)
        assertTrue("Recovered $algorithm public key must verify its signature", verifier.verify(signature))
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun p256RegistrationAndAssertionRecoverFromEncryptedEventAndProjectionStores() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val testId = UUID.randomUUID().toString()
            val recoveryFile = recoveryFile(context.filesDir, testId)
            recoveryFile.parentFile?.mkdirs()
            val databaseName = "hdk-ceremony-$testId.db"
            val dataStore = createUserPreferencesDataStore { recoveryFile.absolutePath }
            val lookupTokens = HmacMetadataLookupTokenService()
            val encryptedMetadata = AesGcmEncryptedMetadataService()
            val provider =
                WalletMasterSeedProvider(
                    masterSeedGenerator = Bip39MasterSeedGenerator(),
                    dataStore = dataStore,
                    encryptionWrapper = EncryptionWrapper(),
                    lookupTokenService = lookupTokens,
                    encryptedMetadataService = encryptedMetadata,
                )
            val crypto = Fido2CryptoService(HdkP256, provider, PostQuantumCrypto(), Dispatchers.Default)
            val clientDataHashService = ClientDataHashService()
            val registrationChallenge =
                ByteArray(REGISTRATION_CHALLENGE_SIZE) { (it + REGISTRATION_CHALLENGE_START).toByte() }
            val assertionChallenge = ByteArray(ASSERTION_CHALLENGE_SIZE) { (ASSERTION_CHALLENGE_START + it).toByte() }
            var driver: app.cash.sqldelight.db.SqlDriver? = null
            var registeredPublicKey: ByteArray? = null
            var attestationMessage: ByteArray? = null
            var assertionMessage: ByteArray? = null
            try {
                val firstStore =
                    createCredentialRepository(context, provider, databaseName, lookupTokens, encryptedMetadata)
                driver = firstStore.first
                val registration =
                    RegisterCredentialUseCase(
                        passkeyCredentialRepository = firstStore.second,
                        userVerificationService = NoUserVerification,
                        cborCodec = CborCodec(),
                        cryptoService = crypto,
                        settingsRepository = FixedCredentialLimit,
                        clientDataHashService = clientDataHashService,
                    )
                val rpId = RpId("device-test.example")
                val registrationResult =
                    provider.withSessionOwner(ownerKind = MasterSeedOwnerKind.AUTHENTICATOR) {
                        registration(
                            MakeCredentialOptions.create(
                                rp = PublicKeyCredentialRpEntity.create(rpId, "Synthetic Device Test"),
                                user =
                                    PublicKeyCredentialUserEntity.create(
                                        UserId.fromByteArray("synthetic-user".encodeToByteArray()),
                                        "user",
                                        "Synthetic User",
                                    ),
                                challenge = registrationChallenge,
                                pubKeyCredParams = PublicKeyCredentialParameters.createES256P256(),
                                selectedAlgId = CredentialKeyDescriptor.COSE_ES256,
                            ),
                        ).getOrThrow()
                    }
                val credentialId = registrationResult.credential.id
                registeredPublicKey =
                    registrationResult.credential.publicKey.encoded
                        .copyOf()
                val attestation = registrationResult.attestationObject
                val attestationSignature = requireNotNull(attestation.attStmt.attCert)
                attestationMessage = requireNotNull(attestation.attStmt.authData) + registrationChallenge
                val attestationVerifier = Signature.getInstance("SHA256withECDSA")
                attestationVerifier.initVerify(registrationResult.credential.publicKey)
                attestationVerifier.update(requireNotNull(attestationMessage))
                assertTrue(
                    "The registered key must verify its self-attestation",
                    attestationVerifier.verify(attestationSignature),
                )

                driver.close()
                driver = null
                provider.clearSession()

                // Rebuild the encrypted SQLite driver, database, DAOs and aggregate repositories
                // over the same disposable files after erasing the in-memory provider session.
                val recoveredStore =
                    createCredentialRepository(context, provider, databaseName, lookupTokens, encryptedMetadata)
                driver = recoveredStore.first
                val recoveredCredential = requireNotNull(recoveredStore.second.getCredentialById(credentialId))
                assertEquals(CredentialKeyDescriptor.COSE_ES256, recoveredCredential.coseAlgorithm)
                assertEquals(CredentialKeyDescriptor.P256_PROFILE, recoveredCredential.derivationProfile)
                assertArrayEquals(registeredPublicKey, recoveredCredential.publicKey.encoded)

                val assertion =
                    GetAssertionUseCase(
                        credentialRepository = recoveredStore.second,
                        userVerificationService = NoUserVerification,
                        selectCredentialUseCase = SelectCredentialUseCase(),
                        cryptoService = crypto,
                        clientDataHashService = clientDataHashService,
                    )
                val assertionResult =
                    provider.withSessionOwner(ownerKind = MasterSeedOwnerKind.AUTHENTICATOR) {
                        assertion(
                            GetAssertionOptions.create(
                                rpId = rpId,
                                clientDataHash = assertionChallenge,
                                allowCredentials = listOf(PublicKeyCredentialDescriptor.create(id = credentialId)),
                                userVerification = UserVerificationRequirement.DISCOURAGED,
                            ),
                        ).getOrThrow()
                    }
                assertionMessage = assertionResult.authData + assertionChallenge
                val assertionVerifier = Signature.getInstance("SHA256withECDSA")
                assertionVerifier.initVerify(recoveredCredential.publicKey)
                assertionVerifier.update(requireNotNull(assertionMessage))
                assertTrue(
                    "The recovered registered key must verify its assertion",
                    assertionVerifier.verify(assertionResult.signature),
                )
                assertEquals(1L, recoveredStore.second.getSignCount(credentialId).getOrThrow())

                val otherCredentialId =
                    CredentialId.fromByteArray(
                        ByteArray(
                            CredentialId.CREDENTIAL_ID_SIZE_BYTES,
                        ) { (it + MISMATCH_CREDENTIAL_ID_START).toByte() },
                    )
                val otherDescriptor =
                    CredentialKeyDescriptor(
                        otherCredentialId,
                        CredentialKeyDescriptor.COSE_ES256,
                        CredentialKeyDescriptor.P256_PROFILE,
                    )
                val mismatchedPublicKey =
                    provider.withSessionOwner(ownerKind = MasterSeedOwnerKind.AUTHENTICATOR) {
                        val generated = crypto.generateCredentialPublicKey(otherDescriptor).getOrThrow()
                        crypto.decodePublicKey(otherDescriptor, generated.publicKey).encoded
                    }
                val encodedMismatch =
                    java.util.Base64
                        .getEncoder()
                        .encodeToString(mismatchedPublicKey)
                recoveredStore.first.execute(
                    identifier = null,
                    sql = "UPDATE passkey_credential SET public_key = ? WHERE id = ?",
                    parameters = UPDATE_PUBLIC_KEY_PARAMETER_COUNT,
                ) {
                    bindString(0, encodedMismatch)
                    bindString(1, credentialId.encoded)
                }

                val mismatchAssertion =
                    provider.withSessionOwner(ownerKind = MasterSeedOwnerKind.AUTHENTICATOR) {
                        assertion(
                            GetAssertionOptions.create(
                                rpId = rpId,
                                clientDataHash = assertionChallenge,
                                allowCredentials = listOf(PublicKeyCredentialDescriptor.create(id = credentialId)),
                                userVerification = UserVerificationRequirement.DISCOURAGED,
                            ),
                        )
                    }
                assertTrue(
                    "Assertion must fail when persisted key metadata disagrees with derivation",
                    mismatchAssertion is Outcome.Error,
                )
                assertEquals(1L, recoveredStore.second.getSignCount(credentialId).getOrThrow())
                val persistedPublicKeyAfterFailure =
                    provider.withSessionOwner(ownerKind = MasterSeedOwnerKind.AUTHENTICATOR) {
                        recoveredStore.second
                            .getCredentialSummariesForRp(rpId)
                            .getOrThrow()
                            .single()
                            .storedPublicKeyEncoding
                    }
                assertArrayEquals(mismatchedPublicKey, persistedPublicKeyAfterFailure)
                mismatchedPublicKey.fill(0)
                persistedPublicKeyAfterFailure.fill(0)
            } finally {
                provider.clearSession()
                driver?.close()
                context.deleteDatabase(databaseName)
                recoveryFile.parentFile?.deleteRecursively()
                registrationChallenge.fill(0)
                assertionChallenge.fill(0)
                registeredPublicKey?.fill(0)
                attestationMessage?.fill(0)
                assertionMessage?.fill(0)
            }
        }
}
