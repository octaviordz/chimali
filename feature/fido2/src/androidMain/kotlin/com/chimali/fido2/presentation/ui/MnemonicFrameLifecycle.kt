package com.chimali.fido2.presentation.ui

import java.util.concurrent.atomic.AtomicBoolean

/** Releases a camera frame on synchronous setup failure or the asynchronous scan terminal path. */
internal fun withMnemonicFrameRelease(
    releaseFrame: () -> Unit,
    startScan: (onComplete: () -> Unit) -> Unit,
) {
    val released = AtomicBoolean(false)
    val complete = {
        if (released.compareAndSet(false, true)) releaseFrame()
    }
    try {
        startScan(complete)
    } catch (_: Exception) {
        complete()
    }
}
