package com.chimali.core.security.api

import com.chimali.core.common.annotation.Req

/** Mutable BIP39 boundary used to create and reconstruct the wallet root input. */
interface MasterSeedGenerator {
    /**
     * Generate an owned mutable English mnemonic. The current wallet profile permits 24 words
     * only; other counts are rejected. The caller owns and erases the returned buffer.
     */
    @Req("NFR-SEC-041")
    fun generateMnemonic(wordCount: Int = 24): CharArray

    /**
     * Validate a 24-word English BIP39 mnemonic and return its full owned 64-byte PBKDF2-HMAC-SHA512
     * result. This wallet profile fixes the BIP39 passphrase to empty. [mnemonic] is borrowed and
     * must remain unchanged; the caller owns and erases the returned seed.
     */
    @Req("NFR-SEC-041")
    fun deriveSeed(mnemonic: CharArray): ByteArray
}
