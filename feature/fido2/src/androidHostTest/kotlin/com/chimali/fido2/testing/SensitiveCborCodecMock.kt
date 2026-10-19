package com.chimali.fido2.testing

import com.chimali.fido2.data.crypto.CborCodec
import io.mockk.Runs
import io.mockk.every
import io.mockk.just

/** Adapts legacy map fixtures to the current CTAP decode and cleanup calls in handler unit tests. */
internal fun CborCodec.stubSensitiveRequestCompatibility() {
    every { decodeSensitiveRequest(any()) } answers { decodeFromFido2Format(firstArg()) }
    every { eraseDecodedRequestValues(any()) } just Runs
}
