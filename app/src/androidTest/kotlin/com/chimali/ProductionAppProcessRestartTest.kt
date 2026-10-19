package com.chimali

import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import com.chimali.core.common.result.getOrThrow
import com.chimali.core.domain.model.CredentialKeyDescriptor
import com.chimali.core.domain.valueobject.RpId
import com.chimali.core.security.api.MasterSeedOwnerKind
import com.chimali.core.security.api.MasterSeedProvider
import com.chimali.fido2.domain.model.AuthenticatorSelectionCriteria
import com.chimali.fido2.domain.model.GetAssertionOptions
import com.chimali.fido2.domain.model.MakeCredentialOptions
import com.chimali.fido2.domain.model.PublicKeyCredentialDescriptor
import com.chimali.fido2.domain.model.PublicKeyCredentialParameters
import com.chimali.fido2.domain.model.PublicKeyCredentialRpEntity
import com.chimali.fido2.domain.model.PublicKeyCredentialUserEntity
import com.chimali.fido2.domain.model.UserVerificationRequirement
import com.chimali.fido2.domain.repository.CredentialRepository
import com.chimali.fido2.domain.service.Fido2Service
import com.chimali.fido2.domain.usecase.GetAssertionUseCase
import java.io.File
import java.security.Security
import java.security.Signature
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.core.context.GlobalContext

/** Exercises the production application package across a real force-stop and process recreation. */
class ProductionAppProcessRestartTest {
    private companion object {
        const val ARGUMENT_TEST_ID = "appProcessRestartTestId"
        const val EXPECTED_TARGET_PACKAGE = "com.chimali.shrunk"
        const val FIXTURE_DIRECTORY = "hdk-production-app-process-restart"
        const val RP_SUFFIX = "app-restart.example"
        const val CHALLENGE_SIZE = 32
        const val REGISTRATION_CHALLENGE_START = 0x21
        const val ASSERTION_CHALLENGE_START = 0x61
    }

    private fun testId(): String? =
        InstrumentationRegistry
            .getArguments()
            .getString(ARGUMENT_TEST_ID)
            ?.takeIf { it.matches(Regex("[0-9a-fA-F-]{36}")) }

    private fun fixtureFile(
        filesDirectory: File,
        testId: String,
    ): File = File(filesDirectory, "$FIXTURE_DIRECTORY/$testId/credential.txt")

    @Test
    fun registerSyntheticCredentialInProductionAppProcess() =
        runBlocking {
            // Ordinary connected runs do not have the two-phase runner's cross-invocation ID.
            val id = testId() ?: return@runBlocking
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            assertEquals(
                "The test must target the isolated shrunkDebug app package",
                EXPECTED_TARGET_PACKAGE,
                context.packageName,
            )

            val fixture = fixtureFile(context.filesDir, id)
            fixture.parentFile?.mkdirs()
            val challenge = ByteArray(CHALLENGE_SIZE) { (it + REGISTRATION_CHALLENGE_START).toByte() }
            var attestationMessage: ByteArray? = null
            try {
                val masterSeedProvider = GlobalContext.get().get<MasterSeedProvider>()
                val fido2Service = GlobalContext.get().get<Fido2Service>()
                val rpId = RpId("$id.$RP_SUFFIX")
                val registration =
                    masterSeedProvider.withSessionOwner(ownerKind = MasterSeedOwnerKind.AUTHENTICATOR) {
                        fido2Service
                            .makeCredential(
                                MakeCredentialOptions.create(
                                    rp = PublicKeyCredentialRpEntity.create(rpId, "Synthetic App Restart", icon = null),
                                    user =
                                        PublicKeyCredentialUserEntity.create(
                                            com.chimali.core.domain.valueobject
                                                .UserId("app-restart-$id"),
                                            "synthetic-app-restart",
                                            "Synthetic App Restart User",
                                            icon = null,
                                        ),
                                    challenge = challenge,
                                    pubKeyCredParams = PublicKeyCredentialParameters.createES256P256(),
                                    authenticatorSelection =
                                        AuthenticatorSelectionCriteria.create(
                                            authenticatorAttachment = null,
                                            requireResidentKey = null,
                                            userVerification = UserVerificationRequirement.DISCOURAGED,
                                            timeoutSeconds = null,
                                            allowCredentials = null,
                                        ),
                                    timeout = null,
                                    allowCredentials = null,
                                    excludeCredentials = null,
                                    attestation =
                                        com.chimali.fido2.domain.model.AttestationConveyancePreference.NONE,
                                    extensions = null,
                                    selectedAlgId = CredentialKeyDescriptor.COSE_ES256,
                                ),
                            ).getOrThrow()
                    }
                val statement = registration.attestationObject.attStmt
                attestationMessage = requireNotNull(statement.authData) + challenge
                verifyP256Signature(
                    registration.credential.publicKey,
                    requireNotNull(attestationMessage),
                    requireNotNull(statement.attCert),
                )
                assertEquals(CredentialKeyDescriptor.P256_PROFILE, registration.credential.derivationProfile)
                fixture.writeText("${Process.myPid()}\n${registration.credential.id.encoded}\n")
            } finally {
                challenge.fill(0)
                attestationMessage?.fill(0)
                GlobalContext.get().get<MasterSeedProvider>().clearSession()
            }
        }

    @Test
    fun recoverAndSignInFreshProductionAppProcess() =
        runBlocking {
            // The dedicated runner supplies this only after a fresh target-app process starts.
            val id = testId() ?: return@runBlocking
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            assertEquals(
                "The test must target the isolated shrunkDebug app package",
                EXPECTED_TARGET_PACKAGE,
                context.packageName,
            )

            val fixture = fixtureFile(context.filesDir, id)
            val recorded = fixture.readLines()
            val previousPid = recorded.first().toInt()
            val expectedCredentialId = recorded[1]
            assertNotEquals(
                "The runner must force-stop and relaunch the production app package",
                previousPid,
                Process.myPid(),
            )

            val masterSeedProvider = GlobalContext.get().get<MasterSeedProvider>()
            val fido2Service = GlobalContext.get().get<Fido2Service>()
            val credentialRepository = GlobalContext.get().get<CredentialRepository>()
            val assertionUseCase = GlobalContext.get().get<GetAssertionUseCase>()
            val rpId = RpId("$id.$RP_SUFFIX")
            val credential =
                fido2Service
                    .getAllCredentials()
                    .first { candidate ->
                        candidate.id.encoded == expectedCredentialId && candidate.rpId.contentEquals(rpId.value)
                    }
            assertEquals(CredentialKeyDescriptor.COSE_ES256, credential.coseAlgorithm)
            assertEquals(CredentialKeyDescriptor.P256_PROFILE, credential.derivationProfile)

            val challenge = ByteArray(CHALLENGE_SIZE) { (it + ASSERTION_CHALLENGE_START).toByte() }
            var assertionMessage: ByteArray? = null
            var signature: ByteArray? = null
            try {
                val assertion =
                    masterSeedProvider.withSessionOwner(ownerKind = MasterSeedOwnerKind.AUTHENTICATOR) {
                        assertionUseCase(
                            GetAssertionOptions.create(
                                rpId = rpId,
                                clientDataHash = challenge,
                                allowCredentials =
                                    listOf(
                                        PublicKeyCredentialDescriptor.create(
                                            type = com.chimali.fido2.domain.model.PublicKeyCredentialType.PUBLIC_KEY,
                                            id = credential.id,
                                            transports = null,
                                        ),
                                    ),
                                userVerification = UserVerificationRequirement.DISCOURAGED,
                                extensions = null,
                                timeout = null,
                            ),
                        ).getOrThrow()
                    }
                assertionMessage = assertion.authData + challenge
                signature = assertion.signature
                verifyP256Signature(credential.publicKey, requireNotNull(assertionMessage), requireNotNull(signature))
                assertEquals(1L, credentialRepository.getSignCount(credential.id).getOrThrow())
            } finally {
                challenge.fill(0)
                assertionMessage?.fill(0)
                signature?.fill(0)
                masterSeedProvider.clearSession()
                fixture.parentFile?.deleteRecursively()
            }
        }

    private fun verifyP256Signature(
        publicKey: java.security.PublicKey,
        message: ByteArray,
        signature: ByteArray,
    ) {
        val provider =
            Security
                .getProviders()
                .firstOrNull { candidate ->
                    candidate.name != "BC" && candidate.getService("Signature", "SHA256withECDSA") != null
                } ?: error("No non-BC platform verifier is available for P-256")
        assertNotEquals("P-256 ceremony verification must use the platform provider", "BC", provider.name)
        val verifier = Signature.getInstance("SHA256withECDSA", provider)
        verifier.initVerify(publicKey)
        verifier.update(message)
        assertTrue("The recovered registered P-256 key must verify the signature", verifier.verify(signature))
    }
}
