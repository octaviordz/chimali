package com.chimali.fido2.testing

import com.chimali.fido2.data.crypto.CborCodec
import com.chimali.fido2.domain.model.SensitiveCredentialText
import io.mockk.every

/** Adapts legacy map fixtures to the current CTAP decode and cleanup calls in handler unit tests. */
internal fun CborCodec.stubSensitiveRequestCompatibility() {
    every { decodeSensitiveRequest(any()) } answers { decodeFromFido2Format(firstArg()) }
    every { eraseDecodedRequestValues(any()) } answers { eraseSyntheticDecodedValue(firstArg()) }
}

private fun eraseSyntheticDecodedValue(value: Any?) {
    when (value) {
        is ByteArray -> value.fill(0)
        is SensitiveCredentialText -> value.erase()
        is Map<*, *> -> value.values.forEach(::eraseSyntheticDecodedValue)
        is Iterable<*> -> value.forEach(::eraseSyntheticDecodedValue)
    }
}
