/**
 * Utility functions for safely wrapping operations in [DataResult] without using
 * generic `catch (e: Exception)` blocks (which violate the Detekt `TooGenericExceptionCaught` rule).
 *
 * ## Coroutine Safety
 *
 * All variants in this file explicitly rethrow [CancellationException] so that Kotlin's
 * structured concurrency is not broken. Catching [CancellationException] causes coroutines
 * to hang and leak memory (FR-005 compliance).
 *
 * ## Usage
 *
 * These utilities are for scenarios where a third-party API throws undocumented runtime
 * exceptions and catching a specific set is infeasible. For all other cases, prefer
 * explicit `try-catch` with specific exception types at the repository boundary.
 *
 * ```kotlin
 * // For truly unknown APIs (use sparingly)
 * val result: DataResult<String, DomainError> = runCatchingResult(
 *     onError = { e -> DomainError.UnknownError("Unexpected failure", e) }
 * ) {
 *     undocumentedApi.doSomething()
 * }
 * ```
 */
package com.chimali.core.common.result

import kotlinx.coroutines.CancellationException

/**
 * Executes [block] and wraps the result in a [DataResult].
 *
 * - On success: returns [DataResult.Success] with the block's return value.
 * - On [CancellationException]: **always rethrows** to preserve structured concurrency.
 * - On any other [Throwable]: maps via [onError] and returns [DataResult.Error].
 *
 * **Important**: [CancellationException] is NOT caught or wrapped.
 *
 * @param onError Maps the caught [Throwable] to a specific [DomainError] subtype.
 *                The throwable passed to [onError] is never a [CancellationException].
 */
@Suppress("TooGenericExceptionCaught")
inline fun <D, E : DomainError> runCatchingResult(
    onError: (Throwable) -> E,
    block: () -> D,
): DataResult<D, E> =
    try {
        DataResult.Success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        DataResult.Error(onError(throwable))
    }

/**
 * Executes [block] and wraps the result in a [DataResult.Success], or returns
 * [DataResult.Error] with a [DomainError.UnknownError] if any non-cancellation
 * [Throwable] is caught.
 *
 * Use this as a **last resort** for truly opaque APIs. For all other cases, prefer
 * explicit `try-catch` with specific exception types and a meaningful [DomainError].
 *
 * **Important**: [CancellationException] is NOT caught or wrapped.
 */
inline fun <D> runCatchingResultOrUnknown(
    errorMessage: String = "An unexpected error occurred",
    block: () -> D,
): DataResult<D, DomainError.UnknownError> =
    runCatchingResult(
        onError = { e -> DomainError.UnknownError(errorMessage, e) },
        block = block,
    )
