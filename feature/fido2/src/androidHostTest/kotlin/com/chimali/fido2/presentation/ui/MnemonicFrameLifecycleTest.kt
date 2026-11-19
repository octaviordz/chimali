package com.chimali.fido2.presentation.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class MnemonicFrameLifecycleTest {
    @Test
    fun synchronousImageOrScannerFailureReleasesFrame() {
        var releaseCount = 0

        withMnemonicFrameRelease(releaseFrame = { releaseCount++ }) {
            throw IllegalStateException("synthetic scan setup failure")
        }

        assertEquals(1, releaseCount)
    }

    @Test
    fun asynchronousCompletionReleasesFrameExactlyOnce() {
        var releaseCount = 0
        var complete: (() -> Unit)? = null

        withMnemonicFrameRelease(releaseFrame = { releaseCount++ }) { onComplete ->
            complete = onComplete
        }

        assertEquals(0, releaseCount)
        requireNotNull(complete).invoke()
        requireNotNull(complete).invoke()
        assertEquals(1, releaseCount)
    }

    @Test
    fun synchronousFailureAfterCompletionRegistrationDoesNotDoubleRelease() {
        var releaseCount = 0

        withMnemonicFrameRelease(releaseFrame = { releaseCount++ }) { onComplete ->
            onComplete()
            throw IllegalStateException("synthetic listener registration failure")
        }

        assertEquals(1, releaseCount)
    }
}
