/**
 * Utility functions for safely wrapping operations in [Outcome] without using
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
 * val result: Outcome<String, DomainError> = runCatchingOutcome(
 *     onError = { e -> DomainError.UnknownError("Unexpected failure", e) }
 * ) {
 *     undocumentedApi.doSomething()
 * }
 * ```
 */
package com.chimali.core.common.result

import kotlinx.coroutines.CancellationException

/**
 * Executes [block] and wraps the result in an [Outcome].
 *
 * - On success: returns [Outcome.Success] with the block's return value.
 * - On [CancellationException]: **always rethrows** to preserve structured concurrency.
 * - On any other [Throwable]: maps via [onError] and returns [Outcome.Error].
 *
 * ### Architectural Decision: @Suppress("TooGenericExceptionCaught")
 * We catch [Throwable] here to provide a safe boundary for third-party or platform APIs
 * that might throw undocumented runtime exceptions. This is the **only** layer where
 * generic catching is permitted, ensuring that the rest of the business logic remains
 * crash-safe and adheres to strict static analysis.
 *
 * **Important**: [CancellationException] is NOT caught or wrapped.
 *
 * @param onError Maps the caught [Throwable] to a specific [DomainError] subtype.
 *                The throwable passed to [onError] is never a [CancellationException].
 */
@Suppress("TooGenericExceptionCaught")
inline fun <D, E : DomainError> runCatchingOutcome(
    onError: (Throwable) -> E,
    block: () -> D,
): Outcome<D, E> =
    try {
        Outcome.Success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        Outcome.Error(onError(throwable))
    }

/**
 * Executes [block] and wraps the result in an [Outcome.Success], or returns
 * [Outcome.Error] with a [DomainError.UnknownError] if any non-cancellation
 * [Throwable] is caught.
 *
 * This version is **suspend-aware** and safe for use in coroutines.
 *
 * ### Architectural Decision: @Suppress("TooGenericExceptionCaught")
 * Generic [Throwable] is caught and wrapped into a [DomainError.UnknownError] to prevent
 * unhandled crashes at the boundary of external or legacy code. By using this helper,
 * we avoid polluting the rest of the codebase with `@Suppress` annotations.
 *
 * **Important**: [CancellationException] is NOT caught or wrapped.
 */
@Suppress("TooGenericExceptionCaught")
suspend inline fun <D> functionalCatching(
    errorMessage: String = "An unexpected error occurred",
    crossinline block: suspend () -> D,
): Outcome<D, DomainError.UnknownError> =
    try {
        Outcome.Success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        Outcome.Error(DomainError.UnknownError(errorMessage, throwable))
    }
