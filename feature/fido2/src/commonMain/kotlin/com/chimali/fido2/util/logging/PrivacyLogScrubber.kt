package com.chimali.fido2.util.logging

import com.chimali.core.common.annotation.Req

/**
 * T151: Privacy-safe debug logging.
 * Scrubs sensitive data (mnemonics, private keys, biometric states) from log messages.
 */
object PrivacyLogScrubber {
    private const val REDACTED = "[REDACTED]"

    // A heuristic to catch mnemonics: 12 or 24 words separated by spaces.
    // E.g. "abandon abandon abandon..."
    private val MNEMONIC_PATTERN = Regex("(?i)(?:[a-z]{3,8}\\s+){11,23}[a-z]{3,8}")

    // A heuristic to catch base64/hex long strings (e.g. EC private keys, large signatures)
    // Matches 64+ character hex strings or long base64
    private val KEY_MATERIAL_PATTERN = Regex("([A-Fa-f0-9]{64,})|([A-Za-z0-9+/=]{80,})")

    // Sensitive keys/terms
    private val SENSITIVE_KEYWORDS =
        listOf(
            "password",
            "pin",
            "prk",
            "privateKey",
            "private key",
            "private_key",
            "privateScalar",
            "private scalar",
            "private_scalar",
            "seed",
            "mnemonic",
            "secret",
            "secretKey",
            "rootKey",
            "root key",
            "root_key",
            "factor",
        )

    // Compile once and scan once rather than compiling and scanning for every keyword. The
    // scrubber runs on the logging path, including CTAP/HID handling, so avoid repeated work.
    private val SENSITIVE_KEYWORD_PATTERN =
        Regex(
            """(?i)(${SENSITIVE_KEYWORDS.joinToString("|") { Regex.escape(it) }})\s*[:=]\s*([^\s,;&]+)""",
        )

    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun scrub(message: String): String {
        var scrubbed = message

        // 1. Scrub Mnemonics
        scrubbed = MNEMONIC_PATTERN.replace(scrubbed, REDACTED)

        // 2. Scrub Key Material (Hex / Base64)
        scrubbed = KEY_MATERIAL_PATTERN.replace(scrubbed, REDACTED)

        // 3. Simple Keyword Based Value Redaction
        // Replaces patterns like "seed=abandon..." or "password: blah"
        scrubbed = scrubbed.replace(SENSITIVE_KEYWORD_PATTERN, "$1=$REDACTED")

        return scrubbed
    }
}
