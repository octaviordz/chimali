package com.chimali.fido2.util.logging

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
            "privateKey",
            "private_key",
            "seed",
            "mnemonic",
            "secret",
            "secretKey",
        )

    fun scrub(message: String): String {
        var scrubbed = message

        // 1. Scrub Mnemonics
        scrubbed = MNEMONIC_PATTERN.replace(scrubbed, REDACTED)

        // 2. Scrub Key Material (Hex / Base64)
        scrubbed = KEY_MATERIAL_PATTERN.replace(scrubbed, REDACTED)

        // 3. Simple Keyword Based Value Redaction
        // Replaces patterns like "seed=abandon..." or "password: blah"
        SENSITIVE_KEYWORDS.forEach { keyword ->
            val regex = Regex("""(?i)($keyword)\s*[:=]\s*([^\s,;&]+)""")
            scrubbed = scrubbed.replace(regex, "$1=$REDACTED")
        }

        return scrubbed
    }
}
