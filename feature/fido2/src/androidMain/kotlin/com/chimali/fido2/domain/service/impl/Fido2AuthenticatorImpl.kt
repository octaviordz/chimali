package com.chimali.fido2.domain.service.impl

import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.domain.time.TimeProvider
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.RpId
import com.chimali.fido2.domain.model.AssertionObject
import com.chimali.fido2.domain.model.AttestationObject
import com.chimali.fido2.domain.model.AuthenticatorTransport
import com.chimali.fido2.domain.model.GetAssertionOptions
import com.chimali.fido2.domain.model.MakeCredentialOptions
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.service.AuthenticatorConfiguration
import com.chimali.fido2.domain.service.AuthenticatorInfo
import com.chimali.fido2.domain.service.AuthenticatorResetType
import com.chimali.fido2.domain.service.AuthenticatorState
import com.chimali.fido2.domain.service.Fido2Authenticator
import com.chimali.fido2.domain.service.Fido2Request
import com.chimali.fido2.domain.service.HealthCheckResult
import com.chimali.fido2.domain.service.PairingRequest
import com.chimali.fido2.domain.service.PairingResult
import com.chimali.fido2.domain.service.RequestValidationResult
import com.chimali.fido2.domain.service.SecurityLevel
import com.chimali.fido2.domain.service.VerificationPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.koin.core.annotation.Single

@Single
class Fido2AuthenticatorImpl(
    private val timeProvider: TimeProvider,
) : Fido2Authenticator {
    companion object {
        private const val MAX_CREDENTIAL_COUNT = 50
        private const val MAX_CREDENTIAL_ID_LENGTH = 255

        private val CHIMALI_AAGUID =
            byteArrayOf(
                // "CHIMALI\0"
                0x43,
                0x48,
                0x49,
                0x4D,
                0x41,
                0x4C,
                0x49,
                0x00,
                // ...version 1
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x01,
            )
    }

    override suspend fun makeCredential(options: MakeCredentialOptions): Outcome<AttestationObject, DomainError> =
        Outcome.Error(DomainError.UnknownError("Not implemented"))

    override suspend fun getAssertion(assertionOptions: GetAssertionOptions): Outcome<AssertionObject, DomainError> =
        Outcome.Error(DomainError.UnknownError("Not implemented"))

    override suspend fun getCredentials(): Flow<PasskeyCredential> = emptyFlow()

    override suspend fun getCredentialsByRpId(rpId: RpId): Flow<PasskeyCredential> = emptyFlow()

    override suspend fun deleteCredential(
        credentialId: CredentialId,
        rpId: RpId?,
    ): Outcome<Unit, DomainError> = Outcome.Success(Unit)

    override suspend fun updateVerificationPreferences(
        preferences: VerificationPreferences,
    ): Outcome<Unit, DomainError> = Outcome.Success(Unit)

    override suspend fun getVerificationPreferences(): VerificationPreferences = VerificationPreferences.createDefault()

    override suspend fun supportsAlgorithm(algorithm: String): Boolean = true

    override suspend fun supportsResidentKeys(): Boolean = true

    override suspend fun supportsUserVerification(): Boolean = true

    override suspend fun getAuthenticatorInfo(): AuthenticatorInfo =
        AuthenticatorInfo(
            aaguid = CHIMALI_AAGUID,
            // Also supports FIDO_2_0
            version = "U2F_V2",
            // T021: supportedAlgorithms must reflect all COSE IDs accepted by algorithm negotiation
            // per WebAuthn L3 §5.4. Order mirrors Ctap2MakeCredentialHandler preference list.
            supportedAlgorithms = listOf("ES256", "EdDSA", "RS256", "ML-DSA"),
            supportedTransports = listOf(AuthenticatorTransport.USB, AuthenticatorTransport.BLE),
            supportsResidentKeys = true,
            supportsUserVerification = true,
            maxCredentialCount = MAX_CREDENTIAL_COUNT,
            maxCredentialIdLength = MAX_CREDENTIAL_ID_LENGTH,
            firmwareVersion = "1.0",
            serialNumber = "00000000",
            isInitialized = true,
            isLocked = false,
        )

    override suspend fun resetAuthenticator(resetType: AuthenticatorResetType): Outcome<Unit, DomainError> =
        Outcome.Success(Unit)

    override suspend fun getAuthenticatorState(): AuthenticatorState = AuthenticatorState.READY

    override suspend fun performHealthCheck(): Outcome<HealthCheckResult, DomainError> =
        Outcome.Success(
            HealthCheckResult(true, emptyMap(), timeProvider.now(), null),
        )

    override suspend fun configureAuthenticator(configuration: AuthenticatorConfiguration): Outcome<Unit, DomainError> =
        Outcome.Success(Unit)

    override suspend fun getAuthenticatorConfiguration(): AuthenticatorConfiguration =
        AuthenticatorConfiguration(
            requireUserVerification = true,
            allowedAlgorithms = listOf("ES256"),
            allowedTransports = listOf(AuthenticatorTransport.BLE),
            enableResidentKeys = true,
            maxCredentialCount = MAX_CREDENTIAL_COUNT,
            biometricSettings = null,
            pinSettings = null,
            securityLevel = SecurityLevel.HIGH,
        )

    override suspend fun isReady(): Boolean = true

    override suspend fun initiatePairing(pairingRequest: PairingRequest): Outcome<PairingResult, DomainError> =
        Outcome.Error(DomainError.UnknownError("Not implemented"))

    override suspend fun getSupportedTransports(): List<AuthenticatorTransport> = listOf(AuthenticatorTransport.BLE)

    override suspend fun validateRequest(request: Fido2Request): Outcome<RequestValidationResult, DomainError> =
        Outcome.Success(
            RequestValidationResult(true, emptyList(), emptyList(), emptyList(), null),
        )
}
