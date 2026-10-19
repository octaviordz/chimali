package com.chimali.fido2.util.logging

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import com.chimali.core.common.annotation.Req

/** Redacts messages and strips throwable messages/causes before forwarding them to a log writer. */
class PrivacyScrubbingLogWriter(
    private val delegate: LogWriter,
) : LogWriter() {
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun log(
        severity: Severity,
        message: String,
        tag: String,
        throwable: Throwable?,
    ) {
        val safeThrowable =
            throwable?.let { original ->
                Throwable(original::class.qualifiedName ?: "Throwable").apply {
                    stackTrace = original.stackTrace
                }
            }
        delegate.log(severity, PrivacyLogScrubber.scrub(message), tag, safeThrowable)
    }
}
