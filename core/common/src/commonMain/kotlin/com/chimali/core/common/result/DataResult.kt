package com.chimali.core.common.result

/**
 * Functional wrapper for operations that can succeed with a value [D] or fail with a [DomainError] [E].
 *
 * ## Why not `kotlin.Result<T>`?
 *
 * Kotlin's built-in `Result<T>` forces the error type to be `Throwable`, which breaks
 * domain isolation. `DataResult<D, E>` ensures that domain and presentation layers only
 * ever deal with our categorized [DomainError] sealed hierarchy, which forces exhaustive
 * `when` statements and prevents leaking raw exceptions into the UI.
 *
 * ## Architecture Contract
 *
 * - **Repository / Service boundary**: Catch specific exceptions, log via Kermit,
 *   wrap in [Error] with the appropriate [DomainError] subtype.
 * - **ViewModel / Presenter**: Consume [DataResult] via exhaustive `when` — never
 *   catch exceptions directly, never log from the presentation layer.
 *
 * ## Usage
 *
 * ```kotlin
 * // Repository
 * suspend fun fetchData(): DataResult<MyData, DomainError> =
 *     try {
 *         DataResult.Success(api.getData())
 *     } catch (e: IOException) {
 *         Logger.e(e) { "Network failure" }
 *         DataResult.Error(DomainError.NetworkError("Network failure", e))
 *     }
 *
 * // ViewModel
 * when (val result = repository.fetchData()) {
 *     is DataResult.Success -> _uiState.value = UiState.Content(result.data)
 *     is DataResult.Error   -> _uiState.value = UiState.Error(mapError(result.error))
 * }
 * ```
 */
sealed interface DataResult<out D, out E : DomainError> {
    /**
     * The operation completed successfully and [data] holds the result.
     *
     * Note: `DataResult<D, Nothing>` — no error type can be instantiated,
     * so callers only need to handle [Success] on this branch.
     */
    data class Success<out D>(val data: D) : DataResult<D, Nothing>

    /**
     * The operation failed and [error] contains the categorized domain error.
     *
     * Note: `DataResult<Nothing, E>` — no data type can be instantiated,
     * so callers only need to handle [Error] on this branch.
     */
    data class Error<out E : DomainError>(val error: E) : DataResult<Nothing, E>
}

// ── Extension functions ────────────────────────────────────────────────────────

/**
 * Returns the [DataResult.Success.data] value, or `null` if this is [DataResult.Error].
 */
fun <D, E : DomainError> DataResult<D, E>.getOrNull(): D? = (this as? DataResult.Success)?.data

/**
 * Returns the [DataResult.Success.data] value, or [default] if this is [DataResult.Error].
 */
fun <D, E : DomainError> DataResult<D, E>.getOrDefault(default: D): D = (this as? DataResult.Success)?.data ?: default

/**
 * Returns `true` if this is [DataResult.Success].
 */
fun <D, E : DomainError> DataResult<D, E>.isSuccess(): Boolean = this is DataResult.Success

/**
 * Returns `true` if this is [DataResult.Error].
 */
fun <D, E : DomainError> DataResult<D, E>.isError(): Boolean = this is DataResult.Error

/**
 * Transforms a [DataResult.Success] value using [transform].
 * A [DataResult.Error] is passed through unchanged.
 */
inline fun <D, R, E : DomainError> DataResult<D, E>.map(transform: (D) -> R): DataResult<R, E> =
    when (this) {
        is DataResult.Success -> DataResult.Success(transform(data))
        is DataResult.Error -> this
    }

/**
 * Executes [action] if this is [DataResult.Error], then returns `this` unchanged.
 *
 * Useful for logging at the boundary without disturbing the return type chain.
 */
inline fun <D, E : DomainError> DataResult<D, E>.onFailure(action: (E) -> Unit): DataResult<D, E> {
    if (this is DataResult.Error) action(error)
    return this
}

/**
 * Executes [action] if this is [DataResult.Success], then returns `this` unchanged.
 */
inline fun <D, E : DomainError> DataResult<D, E>.onSuccess(action: (D) -> Unit): DataResult<D, E> {
    if (this is DataResult.Success) action(data)
    return this
}

/**
 * Flat-maps a [DataResult.Success] to another [DataResult].
 * A [DataResult.Error] is passed through unchanged.
 */
inline fun <D, R, E : DomainError> DataResult<D, E>.flatMap(transform: (D) -> DataResult<R, E>): DataResult<R, E> =
    when (this) {
        is DataResult.Success -> transform(data)
        is DataResult.Error -> this
    }
