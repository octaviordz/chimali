package com.chimali.core.security.api

/**
 * Defines the security policy for key wrapping.
 *
 * Replaces the previous AES-SIV requirement with platform-backed AES-GCM/AEAD
 * which mandates unique nonces and authenticated encryption with associated data.
 */
object KeyWrappingPolicy {
    /**
     * Requirements for key wrapping implementations:
     * - MUST use AES-GCM or equivalent AEAD cipher.
     * - MUST use unique nonces for every encryption operation.
     * - MUST authenticate the ciphertext using associated data (e.g., key ID or purpose).
     * - MUST be backed by platform keystore where possible.
     */
    const val POLICY_VERSION = 1
}
