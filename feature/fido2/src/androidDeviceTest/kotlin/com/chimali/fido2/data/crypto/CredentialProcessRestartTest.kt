package com.chimali.fido2.data.crypto

import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chimali.core.common.datastore.EncryptionWrapper
import com.chimali.core.common.datastore.createUserPreferencesDataStore
import com.chimali.core.common.result.getOrThrow
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
import java.security.Signature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Run the prepare and recover methods in separate `am instrument` invocations. The runner script
 * force-stops this package between them, which verifies recovery after a real OS process death.
 * Without its explicit fixture ID, the connected-suite discovery call returns without modifying
 * device data; the separate runner is required for meaningful recovery evidence.
 */
@RunWith(AndroidJUnit4::class)
class CredentialProcessRestartTest {
    private companion object {
        const val ARGUMENT_TEST_ID = "restartTestId"
        const val STORE_PATH = "hdk-process-restart/%s/user-preferences.pb"
        const val DATABASE_NAME = "hdk-process-restart-%s.db"
        const val PROCESS_ID_FILENAME = "process-id.txt"
        const val RP_ID = "restart-test.example"
        const val REGISTRATION_CHALLENGE_SIZE = 32
        const val ASSERTION_CHALLENGE_SIZE = 32
        const val ASSERTION_CHALLENGE_START = 0x50
    }

    private fun testId(): String? =
        InstrumentationRegistry
            .getArguments()
            .getString(ARGUMENT_TEST_ID)
            ?.takeIf { it.matches(Regex("[0-9a-fA-F-]{36}")) }

    private fun storeFile(testId: String): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return File(context.filesDir, STORE_PATH.format(testId))
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun prepareSyntheticCredentialForRestartRecovery() {
        runBlocking {
            val id = testId()
            if (id == null) return@runBlocking
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val recoveryFile = storeFile(id)
            recoveryFile.parentFile?.mkdirs()
            File(recoveryFile.parentFile, PROCESS_ID_FILENAME).writeText(Process.myPid().toString())
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
            val challenge = ByteArray(REGISTRATION_CHALLENGE_SIZE) { it.toByte() }
            var driver: app.cash.sqldelight.db.SqlDriver? = null
            try {
                val store =
                    createCredentialRepository(
                        context,
                        provider,
                        DATABASE_NAME.format(id),
                        lookupTokens,
                        encryptedMetadata,
                    )
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
                provider.withSessionOwner(ownerKind = MasterSeedOwnerKind.AUTHENTICATOR) {
                    registration(
                        MakeCredentialOptions.create(
                            rp = PublicKeyCredentialRpEntity.create(RpId(RP_ID), "Synthetic Restart Test"),
                            user =
                                PublicKeyCredentialUserEntity.create(
                                    UserId.fromByteArray("synthetic-restart-user".encodeToByteArray()),
                                    "user",
                                    "Synthetic User",
                                ),
                            challenge = challenge,
                            pubKeyCredParams = PublicKeyCredentialParameters.createES256P256(),
                            selectedAlgId = com.chimali.core.domain.model.CredentialKeyDescriptor.COSE_ES256,
                        ),
                    ).getOrThrow()
                }
            } finally {
                provider.clearSession()
                driver?.close()
                challenge.fill(0)
            }
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun recoverAndSignAfterOperatingSystemProcessRestart() {
        runBlocking {
            val id = testId()
            if (id == null) return@runBlocking
            val testId = id
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val recoveryFile = storeFile(testId)
            val processIdFile = File(recoveryFile.parentFile, PROCESS_ID_FILENAME)
            val previousProcessId = processIdFile.readText().toInt()
            assertNotEquals("The runner must start a new app process", previousProcessId, Process.myPid())

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
            val challenge = ByteArray(ASSERTION_CHALLENGE_SIZE) { (ASSERTION_CHALLENGE_START + it).toByte() }
            var driver: app.cash.sqldelight.db.SqlDriver? = null
            var assertionMessage: ByteArray? = null
            try {
                val store =
                    createCredentialRepository(
                        context,
                        provider,
                        DATABASE_NAME.format(testId),
                        lookupTokens,
                        encryptedMetadata,
                    )
                driver = store.first
                val assertion =
                    GetAssertionUseCase(
                        credentialRepository = store.second,
                        userVerificationService = NoUserVerification,
                        selectCredentialUseCase = SelectCredentialUseCase(),
                        cryptoService = crypto,
                        clientDataHashService = ClientDataHashService(),
                    )
                provider.withSessionOwner(ownerKind = MasterSeedOwnerKind.AUTHENTICATOR) {
                    val credential =
                        requireNotNull(
                            store.second
                                .getCredentialSummariesForRp(RpId(RP_ID))
                                .getOrThrow()
                                .singleOrNull(),
                        ) {
                            "The credential projection must survive process restart"
                        }
                    val recoveredCredential = requireNotNull(store.second.getCredentialById(credential.credentialId))
                    val publicKey = recoveredCredential.publicKey
                    val assertionResult =
                        assertion(
                            GetAssertionOptions.create(
                                rpId = RpId(RP_ID),
                                clientDataHash = challenge,
                                allowCredentials =
                                    listOf(PublicKeyCredentialDescriptor.create(id = credential.credentialId)),
                                userVerification = UserVerificationRequirement.DISCOURAGED,
                            ),
                        ).getOrThrow()
                    assertionMessage = assertionResult.authData + challenge
                    val verifier = Signature.getInstance("SHA256withECDSA")
                    verifier.initVerify(publicKey)
                    verifier.update(requireNotNull(assertionMessage))
                    assertTrue(
                        "Recovered key must sign after the OS process restart",
                        verifier.verify(assertionResult.signature),
                    )
                    assertArrayEquals(publicKey.encoded, recoveredCredential.publicKey.encoded)
                }
            } finally {
                provider.clearSession()
                driver?.close()
                context.deleteDatabase(DATABASE_NAME.format(testId))
                recoveryFile.parentFile?.deleteRecursively()
                challenge.fill(0)
                assertionMessage?.fill(0)
            }
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun cleanSyntheticRestartFixture() {
        val id = testId()
        if (id == null) return
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(DATABASE_NAME.format(id))
        storeFile(id).parentFile?.deleteRecursively()
    }
}
