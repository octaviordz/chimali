package com.chimali.core.common.result

/**
 * Functional wrapper for operations that can succeed with a value [D] or fail with a [DomainError] [E].
 *
 * ## Why not `kotlin.Result<T>`?
 *
 * Kotlin's built-in `Result<T>` forces the error type to be `Throwable`, which breaks
 * domain isolation. `Outcome<D, E>` ensures that domain and presentation layers only
 * ever deal with our categorized [DomainError] sealed hierarchy, which forces exhaustive
 * `when` statements and prevents leaking raw exceptions into the UI.
 *
 * ## Architecture Contract
 *
 * - **Repository / Service boundary**: Catch specific exceptions, log via Kermit,
 *   wrap in [Error] with the appropriate [DomainError] subtype.
 * - **ViewModel / Presenter**: Consume [Outcome] via exhaustive `when` — never
 *   catch exceptions directly, never log from the presentation layer.
 *
 * ## Usage
 *
 * ```kotlin
 * // Repository
 * suspend fun fetchData(): Outcome<MyData, DomainError> =
 *     try {
 *         Outcome.Success(api.getData())
 *     } catch (e: IOException) {
 *         Logger.e(e) { "Network failure" }
 *         Outcome.Error(DomainError.NetworkError("Network failure", e))
 *     }
 *
 * // ViewModel
 * when (val result = repository.fetchData()) {
 *     is Outcome.Success -> _uiState.value = UiState.Content(result.data)
 *     is Outcome.Error   -> _uiState.value = UiState.Error(mapError(result.error))
 * }
 * ```
 */
sealed interface Outcome<out D, out E : DomainError> {
    /**
     * The operation completed successfully and [data] holds the result.
     *
     * Note: `Outcome<D, Nothing>` — no error type can be instantiated,
     * so callers only need to handle [Success] on this branch.
     */
    data class Success<out D>(
        val data: D,
    ) : Outcome<D, Nothing>

    /**
     * The operation failed and [error] contains the categorized domain error.
     *
     * Note: `Outcome<Nothing, E>` — no data type can be instantiated,
     * so callers only need to handle [Error] on this branch.
     */
    data class Error<out E : DomainError>(
        val error: E,
    ) : Outcome<Nothing, E>
}

// ── Extension functions ────────────────────────────────────────────────────────

/**
 * Returns the [Outcome.Success.data] value, or `null` if this is [Outcome.Error].
 */
fun <D, E : DomainError> Outcome<D, E>.getOrNull(): D? = (this as? Outcome.Success)?.data

/**
 * Returns the [Outcome.Success.data] value, or [default] if this is [Outcome.Error].
 */
fun <D, E : DomainError> Outcome<D, E>.getOrDefault(default: D): D = (this as? Outcome.Success)?.data ?: default

/**
 * Returns the [Outcome.Success.data] value, or computes a default value from the error.
 */
inline fun <D, E : DomainError> Outcome<D, E>.getOrElse(onFailure: (E) -> D): D =
    when (this) {
        is Outcome.Success -> data
        is Outcome.Error -> onFailure(error)
    }

/**
 * Returns `true` if this is [Outcome.Success].
 */
val <D, E : DomainError> Outcome<D, E>.isSuccess: Boolean get() = this is Outcome.Success

/**
 * Returns `true` if this is [Outcome.Error].
 */
val <D, E : DomainError> Outcome<D, E>.isFailure: Boolean get() = this is Outcome.Error

/**
 * Transforms a [Outcome.Success] value using [transform].
 * A [Outcome.Error] is passed through unchanged.
 */
inline fun <D, R, E : DomainError> Outcome<D, E>.map(transform: (D) -> R): Outcome<R, E> =
    when (this) {
        is Outcome.Success -> Outcome.Success(transform(data))
        is Outcome.Error -> this
    }

/**
 * Executes [action] if this is [Outcome.Error], then returns `this` unchanged.
 *
 * Useful for logging at the boundary without disturbing the return type chain.
 */
inline fun <D, E : DomainError> Outcome<D, E>.onFailure(action: (E) -> Unit): Outcome<D, E> {
    if (this is Outcome.Error) action(error)
    return this
}

inline fun <D, E : DomainError, R : DomainError> Outcome<D, E>.mapError(transform: (E) -> R): Outcome<D, R> =
    when (this) {
        is Outcome.Success -> this
        is Outcome.Error -> Outcome.Error(transform(error))
    }

/**
 * Executes [action] if this is [Outcome.Success], then returns `this` unchanged.
 */
inline fun <D, E : DomainError> Outcome<D, E>.onSuccess(action: (D) -> Unit): Outcome<D, E> {
    if (this is Outcome.Success) action(data)
    return this
}

/**
 * Flat-maps a [Outcome.Success] to another [Outcome].
 * A [Outcome.Error] is passed through unchanged.
 */
inline fun <D, R, E : DomainError> Outcome<D, E>.flatMap(transform: (D) -> Outcome<R, E>): Outcome<R, E> =
    when (this) {
        is Outcome.Success -> transform(data)
        is Outcome.Error -> this
    }

/**
 * Returns the [Outcome.Success.data] value if this is [Outcome.Success],
 * or throws the [DomainError.cause] (if available) or an [IllegalStateException] with [DomainError.message].
 */
fun <D, E : DomainError> Outcome<D, E>.getOrThrow(): D =
    when (this) {
        is Outcome.Success -> data
        is Outcome.Error -> throw error.cause ?: IllegalStateException(error.message)
    }

/**
 * Returns the [DomainError.cause] if this is [Outcome.Error], or `null` if this is [Outcome.Success].
 */
fun <D, E : DomainError> Outcome<D, E>.exceptionOrNull(): Throwable? =
    when (this) {
        is Outcome.Success -> null
        is Outcome.Error -> error.cause
    }
