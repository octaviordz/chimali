@file:Suppress("MagicNumber")

package com.chimali.fido2.performance

import com.chimali.core.common.annotation.Req
import com.chimali.fido2.data.crypto.CborCodec
import com.chimali.fido2.domain.model.AssertionObject
import com.chimali.fido2.domain.model.AttestationObject
import com.chimali.fido2.domain.model.AuthenticatorData
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.util.Base64

/** In-process synthetic RP that validates registration data and verifies assertion signatures independently. */
@Req("TR-PERF-010")
class SimulatedRelyingParty(
    private val cborCodec: CborCodec,
) {
    data class Request(
        val rpId: String,
        val origin: String,
        val challenge: ByteArray,
        val clientDataHash: ByteArray,
    ) {
        init {
            require(rpId.isNotBlank())
            require(origin.isNotBlank())
            require(challenge.isNotEmpty())
            require(clientDataHash.size == SHA256_BYTES)
        }
    }

    data class VerifiedCredential(
        val credentialId: ByteArray,
        val publicKey: ECPublicKey,
    ) {
        init {
            require(credentialId.isNotEmpty())
        }
    }

    data class VerificationResult<T>(
        val value: T?,
        val failure: String? = null,
    ) {
        val verified: Boolean get() = value != null
    }

    fun verifyRegistration(
        request: Request,
        response: AttestationObject,
    ): VerificationResult<VerifiedCredential> {
        val data = response.authData
        val validClientData =
            response.clientData.let {
                it.type == "webauthn.create" &&
                    it.challenge.contentEquals(request.challenge) &&
                    it.origin == request.origin &&
                    !it.isCrossOrigin
            }
        if (!validClientData ||
            response.fmt != "packed" ||
            response.attStmt.fmt != "packed" ||
            (response.attStmt.alg as? Number)?.toInt() != COSE_ALG_ES256
        ) {
            return VerificationResult(null, "registration response binding is invalid")
        }
        if (!data.rpIdHash.contentEquals(sha256(request.rpId.toByteArray(Charsets.UTF_8))) ||
            !hasFlag(data.flags, FLAG_UP) ||
            !hasFlag(data.flags, FLAG_UV) ||
            !hasFlag(data.flags, FLAG_AT)
        ) {
            return VerificationResult(null, "registration authenticator data is invalid")
        }
        if (data.counter > MAX_COUNTER) return VerificationResult(null, "registration counter is invalid")
        val publicKey =
            decodeP256CoseKey(data.publicKey)
                ?: return VerificationResult(null, "registration public key is unsupported")
        val signedAuthData =
            response.attStmt.authData
                ?: return VerificationResult(null, "registration signed data is missing")
        val signatureBytes =
            response.attStmt.attCert
                ?: return VerificationResult(null, "registration signature is missing")
        if (!signedAuthData.contentEquals(serializeAuthenticatorData(data))) {
            return VerificationResult(null, "registration authenticator data is inconsistent")
        }
        val signatureValid =
            try {
                Signature.getInstance("SHA256withECDSA").run {
                    initVerify(publicKey)
                    update(signedAuthData)
                    update(request.clientDataHash)
                    verify(signatureBytes)
                }
            } catch (_: Exception) {
                false
            }
        if (!signatureValid) return VerificationResult(null, "registration signature is invalid")
        return VerificationResult(VerifiedCredential(data.credentialId.copyOf(), publicKey))
    }

    fun verifyAssertion(
        request: Request,
        credential: VerifiedCredential,
        response: AssertionObject,
    ): VerificationResult<Boolean> {
        val authData = response.authData
        if (authData.size < AUTH_DATA_MIN_BYTES ||
            !authData.copyOfRange(0, SHA256_BYTES).contentEquals(sha256(request.rpId.toByteArray(Charsets.UTF_8))) ||
            !hasFlag(authData, FLAG_UP_OFFSET, FLAG_UP) ||
            !hasFlag(authData, FLAG_UP_OFFSET, FLAG_UV)
        ) {
            return VerificationResult(null, "assertion authenticator data is invalid")
        }
        val expectedId = Base64.getUrlEncoder().withoutPadding().encodeToString(credential.credentialId)
        if (response.credentialId != expectedId) return VerificationResult(null, "assertion credential is unexpected")
        return try {
            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(credential.publicKey)
            verifier.update(authData)
            verifier.update(request.clientDataHash)
            if (verifier.verify(response.signature)) {
                VerificationResult(true)
            } else {
                VerificationResult(null, "assertion signature is invalid")
            }
        } catch (_: Exception) {
            VerificationResult(null, "assertion signature is invalid")
        }
    }

    private fun decodeP256CoseKey(encoded: ByteArray): ECPublicKey? {
        val cose = cborCodec.decodeFromFido2Format(encoded)
        return try {
            val kty = (cose["1"] as? Number)?.toInt()
            val algorithm = (cose["3"] as? Number)?.toInt()
            val curve = (cose["-1"] as? Number)?.toInt()
            val x = cose["-2"] as? ByteArray
            val y = cose["-3"] as? ByteArray
            if (!isSupportedP256Key(kty, algorithm, curve, x, y)) {
                return null
            }
            val parameters =
                AlgorithmParameters
                    .getInstance("EC")
                    .apply {
                        init(ECGenParameterSpec("secp256r1"))
                    }.getParameterSpec(ECParameterSpec::class.java)
            val keySpec = ECPublicKeySpec(ECPoint(BigInteger(1, x), BigInteger(1, y)), parameters)
            KeyFactory.getInstance("EC").generatePublic(keySpec) as ECPublicKey
        } catch (_: Exception) {
            null
        } finally {
            cborCodec.eraseDecodedRequestValues(cose)
        }
    }

    private fun isSupportedP256Key(
        keyType: Int?,
        algorithm: Int?,
        curve: Int?,
        x: ByteArray?,
        y: ByteArray?,
    ): Boolean =
        keyType == COSE_KTY_EC2 &&
            algorithm == COSE_ALG_ES256 &&
            curve == COSE_CURVE_P256 &&
            x != null &&
            y != null

    private fun serializeAuthenticatorData(data: AuthenticatorData): ByteArray {
        require(data.counter <= MAX_COUNTER)
        val length = data.credentialId.size
        return data.rpIdHash + data.flags +
            byteArrayOf(
                (data.counter shr 24).toByte(),
                (data.counter shr 16).toByte(),
                (data.counter shr 8).toByte(),
                data.counter.toByte(),
            ) + data.aaguid + byteArrayOf((length shr 8).toByte(), length.toByte()) + data.credentialId + data.publicKey
    }

    private fun hasFlag(
        flags: ByteArray,
        mask: Int,
    ): Boolean = hasFlag(flags, 0, mask)

    private fun hasFlag(
        data: ByteArray,
        offset: Int,
        mask: Int,
    ): Boolean = data.size > offset && (data[offset].toInt() and mask) != 0

    private fun sha256(value: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(value)

    private companion object {
        const val SHA256_BYTES = 32
        const val AUTH_DATA_MIN_BYTES = 37
        const val FLAG_UP_OFFSET = 32
        const val FLAG_UP = 0x01
        const val FLAG_UV = 0x04
        const val FLAG_AT = 0x40
        const val COSE_KTY_EC2 = 2
        const val COSE_ALG_ES256 = -7
        const val COSE_CURVE_P256 = 1
        const val MAX_COUNTER = 0xFFFF_FFFFL
    }
}
