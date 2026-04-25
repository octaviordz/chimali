package com.chimali.fido2.data.crypto

import co.touchlab.kermit.Logger
import java.security.MessageDigest
import java.util.Base64
import org.koin.core.annotation.Single

/**
 * T059 — Client data hash generation per WebAuthn / CTAP2 spec.
 *
 * ### What is the client data hash?
 * During a FIDO2 ceremony the *client* (browser / app) constructs a JSON object called
 * `clientDataJSON` containing:
 * - `type`:        `"webauthn.create"` or `"webauthn.get"`
 * - `challenge`:   Base64URL-encoded challenge from the relying party
 * - `origin`:      The origin URL of the requesting party
 * - `crossOrigin`: Whether cross-origin RP-ID is in use (default `false`)
 *
 * The authenticator receives the **SHA-256 hash** of that JSON string as `clientDataHash`.
 * This binding prevents relay attacks.
 *
 * In our BLE/HID transport the host sends clientDataHash directly in the CTAPHID_CBOR
 * request — however this service is used when the Android side is the "client" (e.g.
 * during integration testing or when the authenticator drives the full ceremony).
 */
@Single
class ClientDataHashService {
    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Computes SHA-256(`clientDataJSON`) where `clientDataJSON` is a minimal
     * {type, challenge, origin, crossOrigin} JSON object.
     *
     * @param type        `"webauthn.create"` for registration, `"webauthn.get"` for assertion.
     * @param challenge   Raw bytes of the challenge received from the relying party.
     * @param origin      Origin of the relying party (e.g. `"https://example.com"`).
     * @param crossOrigin `true` if using cross-origin iframes (default `false`).
     * @return 32-byte SHA-256 digest.
     */
    fun computeHash(
        type: String,
        challenge: ByteArray,
        origin: String,
        crossOrigin: Boolean = false,
    ): ByteArray {
        val clientDataJson = buildClientDataJson(type, challenge, origin, crossOrigin)
        Logger.d { "clientDataJSON: $clientDataJson" }
        return sha256(clientDataJson.toByteArray(Charsets.UTF_8))
    }

    /**
     * Computes SHA-256 of a pre-built clientDataJSON string.
     * Use this when you already have the JSON from the host.
     */
    fun computeHashFromJson(clientDataJson: String): ByteArray = sha256(clientDataJson.toByteArray(Charsets.UTF_8))

    /**
     * Computes SHA-256 directly from raw bytes.
     * Use when `clientDataHash` is already received from the host (CTAP2 parameter 0x01).
     */
    fun computeHashFromBytes(data: ByteArray): ByteArray = sha256(data)

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Builds the canonical clientDataJSON string per WebAuthn §5.8.1.
     * Keys must appear in exactly this order: type, challenge, origin, crossOrigin.
     */
    internal fun buildClientDataJson(
        type: String,
        challenge: ByteArray,
        origin: String,
        crossOrigin: Boolean,
    ): String {
        // Base64URL-encode the challenge (no padding per spec)
        val challengeB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(challenge)
        // Minimal compact JSON — rely on Kotlin string interpolation to avoid library dependency.
        // Order is significant per some WebAuthn implementations.
        return """{"type":"$type","challenge":"$challengeB64","origin":"$origin","crossOrigin":$crossOrigin}"""
    }

    companion object {
        private val SHA_256 = MessageDigest.getInstance("SHA-256")

        fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

        /** Convenience: SHA-256 of a relying party ID string (used for rpIdHash). */
        fun rpIdHash(rpId: String): ByteArray = sha256(rpId.toByteArray(Charsets.UTF_8))
    }
}
