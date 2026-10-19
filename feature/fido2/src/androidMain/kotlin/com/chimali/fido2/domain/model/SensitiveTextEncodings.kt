package com.chimali.fido2.domain.model

import com.chimali.core.common.annotation.Req
import com.chimali.core.domain.model.SensitiveText
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** Encodes borrowed mutable text; the returned bytes transfer to the caller for terminal erasure. */
@Req("NFR-SEC-041", "TR-AUTH-031")
internal fun CharSequence.toOwnedUtf8(): ByteArray {
    val output = ByteBuffer.allocate(Math.multiplyExact(length, MAX_UTF8_BYTES_PER_CODE_UNIT))
    return try {
        val encoder =
            StandardCharsets.UTF_8
                .newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
        val encoded = encoder.encode(CharBuffer.wrap(this), output, true)
        if (encoded.isError) encoded.throwException()
        val flushed = encoder.flush(output)
        if (flushed.isError) flushed.throwException()
        check(encoded.isUnderflow && flushed.isUnderflow)
        output.flip()
        ByteArray(output.remaining()).also(output::get)
    } finally {
        output.array().fill(0)
    }
}

/** Decodes borrowed row bytes into an independent common owner, erasing the temporary decoder owner. */
@Req("NFR-SEC-041", "TR-AUTH-031")
internal fun decodeSensitiveText(bytes: ByteArray): SensitiveText {
    val decoded = SensitiveCredentialText.fromUtf8Bytes(bytes)
    return try {
        SensitiveText.takeOwnership(decoded.copyCharacters())
    } finally {
        decoded.erase()
    }
}

private const val MAX_UTF8_BYTES_PER_CODE_UNIT = 4
