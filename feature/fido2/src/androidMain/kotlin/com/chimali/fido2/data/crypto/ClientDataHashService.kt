package com.chimali.fido2.data.crypto

import java.security.MessageDigest
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
    fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    /** Convenience: SHA-256 of a relying party ID string (used for rpIdHash). */
    fun rpIdHash(rpId: String): ByteArray = sha256(rpId.toByteArray(Charsets.UTF_8))
}
