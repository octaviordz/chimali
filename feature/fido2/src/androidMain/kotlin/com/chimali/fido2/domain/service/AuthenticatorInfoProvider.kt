package com.chimali.fido2.domain.service

import com.chimali.fido2.domain.model.AuthenticatorTransport
import org.koin.core.annotation.Single

/**
 * FR-006 — Authoritative capabilities returned by the CTAP authenticatorGetInfo path.
 *
 * Ceremony behavior remains owned by the dedicated CTAP handlers and use cases. This provider
 * intentionally owns only static, publicly advertised capability information.
 */
@Single
class AuthenticatorInfoProvider {
    fun getAuthenticatorInfo(): AuthenticatorInfo =
        AuthenticatorInfo(
            aaguid = CHIMALI_AAGUID.copyOf(),
            version = "U2F_V2",
            supportedAlgorithms = SUPPORTED_ALGORITHMS,
            supportedTransports = SUPPORTED_TRANSPORTS,
            isResidentKeySupported = true,
            isUserVerificationSupported = true,
            maxCredentialCount = MAX_CREDENTIAL_COUNT,
            maxCredentialIdLength = MAX_CREDENTIAL_ID_LENGTH,
            firmwareVersion = "1.0",
            serialNumber = "00000000",
            isInitialized = true,
            isLocked = false,
        )

    private companion object {
        const val MAX_CREDENTIAL_COUNT = 50
        const val MAX_CREDENTIAL_ID_LENGTH = 255
        val SUPPORTED_ALGORITHMS = listOf("ES256", "EdDSA", "RS256", "ML-DSA")
        val SUPPORTED_TRANSPORTS = listOf(AuthenticatorTransport.USB, AuthenticatorTransport.BLE)
        val CHIMALI_AAGUID =
            byteArrayOf(
                0x43,
                0x48,
                0x49,
                0x4D,
                0x41,
                0x4C,
                0x49,
                0x00,
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
}

/** Public capability data used to encode CTAP authenticatorGetInfo responses. */
data class AuthenticatorInfo(
    val aaguid: ByteArray,
    val version: String,
    val supportedAlgorithms: List<String>,
    val supportedTransports: List<AuthenticatorTransport>,
    val isResidentKeySupported: Boolean,
    val isUserVerificationSupported: Boolean,
    val maxCredentialCount: Int,
    val maxCredentialIdLength: Int,
    val firmwareVersion: String,
    val serialNumber: String,
    val isInitialized: Boolean,
    val isLocked: Boolean,
) {
    fun isReady(): Boolean = isInitialized && !isLocked

    fun getDescription(): String = "Authenticator v$version (AAGUID: ${aaguid.joinToString("") { "%02x".format(it) }})"

    fun supportsAlgorithm(algorithm: String): Boolean = supportedAlgorithms.contains(algorithm)

    fun supportsTransport(transport: AuthenticatorTransport): Boolean = supportedTransports.contains(transport)
}
