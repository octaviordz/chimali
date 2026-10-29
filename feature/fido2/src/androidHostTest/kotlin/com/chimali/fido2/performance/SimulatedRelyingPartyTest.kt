package com.chimali.fido2.performance

import com.chimali.core.common.annotation.Req
import com.chimali.fido2.data.crypto.CborCodec
import com.chimali.fido2.domain.model.AssertionObject
import com.chimali.fido2.domain.model.AttestationObject
import com.chimali.fido2.domain.model.AttestationStatement
import com.chimali.fido2.domain.model.AuthenticatorData
import com.chimali.fido2.domain.model.ClientData
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Req("TR-PERF-010")
class SimulatedRelyingPartyTest {
    private val codec = CborCodec()
    private val relyingParty = SimulatedRelyingParty(codec)

    @Test
    fun `independently verifies synthetic registration and assertion signature`() {
        val keyPair = p256KeyPair()
        val credentialId = ByteArray(32) { it.toByte() }
        val request = request()
        val cosePublicKey = codec.encodeCosePublicKeyFromJavaKey(keyPair.public)
        val authData =
            AuthenticatorData.create(
                rpIdHash = sha256(request.rpId.toByteArray()),
                flags = byteArrayOf(0x45),
                counter = 0,
                aaguid = ByteArray(16),
                credentialId = credentialId,
                publicKey = cosePublicKey,
            )
        val signedAuthData =
            authData.rpIdHash + authData.flags + ByteArray(4) + authData.aaguid +
                byteArrayOf(0, credentialId.size.toByte()) + credentialId + cosePublicKey
        val attestationSigner = Signature.getInstance("SHA256withECDSA")
        attestationSigner.initSign(keyPair.private)
        attestationSigner.update(signedAuthData)
        attestationSigner.update(request.clientDataHash)
        val attestation =
            AttestationObject.create(
                fmt = "packed",
                authData = authData,
                attStmt =
                    AttestationStatement.create(
                        alg = -7,
                        fmt = "packed",
                        attCert = attestationSigner.sign(),
                        authData = signedAuthData,
                    ),
                clientData = ClientData.create(challenge = request.challenge, origin = request.origin),
            )

        val registration = relyingParty.verifyRegistration(request, attestation)
        assertTrue(registration.verified)
        val verifiedCredential = assertNotNull(registration.value)

        val assertionAuthData = sha256(request.rpId.toByteArray()) + byteArrayOf(0x05) + byteArrayOf(0, 0, 0, 1)
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(keyPair.private)
        signer.update(assertionAuthData)
        signer.update(request.clientDataHash)
        val response =
            AssertionObject(
                credential = null,
                authData = assertionAuthData,
                signature = signer.sign(),
                user = null,
                credentialId = Base64.getUrlEncoder().withoutPadding().encodeToString(credentialId),
            )

        assertTrue(relyingParty.verifyAssertion(request, verifiedCredential, response).verified)
        assertFalse(
            relyingParty
                .verifyAssertion(
                    request,
                    verifiedCredential,
                    response.copy(signature = byteArrayOf(1)),
                ).verified,
        )
    }

    @Test
    fun `rejects a registration challenge mismatch`() {
        val keyPair = p256KeyPair()
        val request = request()
        val authData =
            AuthenticatorData.create(
                rpIdHash = sha256(request.rpId.toByteArray()),
                flags = byteArrayOf(0x45),
                counter = 0,
                aaguid = ByteArray(16),
                credentialId = ByteArray(32) { it.toByte() },
                publicKey = codec.encodeCosePublicKeyFromJavaKey(keyPair.public),
            )
        val signedAuthData =
            authData.rpIdHash + authData.flags + ByteArray(4) + authData.aaguid +
                byteArrayOf(0, authData.credentialId.size.toByte()) + authData.credentialId + authData.publicKey
        val attestation =
            AttestationObject.create(
                fmt = "packed",
                authData = authData,
                attStmt =
                    AttestationStatement.create(
                        alg = -7,
                        fmt = "packed",
                        attCert = byteArrayOf(1),
                        authData = signedAuthData,
                    ),
                clientData = ClientData.create(challenge = request.challenge + 1, origin = request.origin),
            )

        assertFalse(relyingParty.verifyRegistration(request, attestation).verified)
    }

    private fun request(): SimulatedRelyingParty.Request {
        val challenge = sha256("synthetic-client-data".toByteArray())
        return SimulatedRelyingParty.Request(
            rpId = "example.com",
            origin = "https://example.com",
            challenge = challenge,
            clientDataHash = challenge.copyOf(),
        )
    }

    private fun p256KeyPair(): KeyPair =
        KeyPairGenerator
            .getInstance("EC")
            .apply {
                initialize(256)
            }.generateKeyPair()

    private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
}
