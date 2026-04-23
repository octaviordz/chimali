package com.chimali.core.security.api

/**
 * Interface for generating and managing the Master Seed based on BIP39.
 */
interface MasterSeedGenerator {
    /**
     * Generates a new 12 or 24 word mnemonic.
     * @param wordCount Must be 12 or 24.
     */
    fun generateMnemonic(wordCount: Int = 12): List<String>

    /**
     * Derives a seed from a mnemonic and an optional passphrase.
     */
    fun deriveSeed(mnemonic: List<String>, passphrase: String = ""): ByteArray
}
