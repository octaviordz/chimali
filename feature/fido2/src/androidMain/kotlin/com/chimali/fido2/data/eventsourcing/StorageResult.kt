package com.chimali.fido2.data.eventsourcing

import com.chimali.core.common.annotation.Req
import kotlinx.coroutines.CancellationException

/** Converts expected persistence, serialization, and encryption failures to the repository Result. */
@Req("NFR-SEC-041", "TR-AUTH-031")
@Suppress("TooGenericExceptionCaught", "SwallowedException") // Storage/provider details must not cross this boundary.
internal suspend fun <T> captureStorageResult(operation: suspend () -> T): Result<T> =
    try {
        Result.success(operation())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        Result.failure(IllegalStateException("Passkey storage operation failed"))
    }
