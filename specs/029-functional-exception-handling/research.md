# Phase 0: Research & Technical Context Resolution

## 1. Functional Exception Handling Pattern (`DataResult<D, E>`)

**Decision**: Implement a custom sealed interface `DataResult<out D, out E : DomainError>` with `Success` and `Error` subclasses.
**Rationale**: Option B was selected during clarification. Kotlin's built-in `Result<T>` forces the error type to be `Throwable`, which breaks domain isolation. A custom `DataResult` ensures that domain and presentation layers only ever deal with our categorized `DomainError` sealed hierarchy, forcing exhaustive `when` statements on error handling.
**Alternatives considered**: Arrow's `Either` (rejected due to added dependency overhead and learning curve), Kotlin's `Result` (rejected due to untyped exceptions).

## 2. Managing Detekt `TooGenericExceptionCaught`

**Decision**: To satisfy FR-001 and FR-002, we cannot use a generic `catch (e: Exception)` block even in our wrapper functions unless it is thoroughly suppressed, but FR-001 explicitly bans `@Suppress("TooGenericExceptionCaught")`. Therefore, data sources MUST catch explicit exceptions (e.g., `IOException`, `HttpException`, `SecurityException`, `SQLiteException`) thrown by underlying APIs. 
**Rationale**: Catching specific exceptions forces developers to understand failure modes of the libraries they use, aligning with the project's strict reliability and safety constitution principles.
**Alternatives considered**: Suppressing the rule on a single generic wrapper function (rejected as it violates FR-001).

## 3. Boundary Logging (Observability)

**Decision**: Expose an extension function `onFailure(action: (E) -> Unit): DataResult<D, E>` or map exceptions exactly at the repository boundary, where `Kermit` logging is invoked before returning the `DataResult.Error`.
**Rationale**: Meets FR-007 which mandates logging at the module/feature boundary where the error is mapped, rather than at the presentation layer. The original `Throwable` will be passed as a `cause` property within `DomainError` to preserve the stack trace for Crashlytics.

## 4. Coroutine Cancellation Safety

**Decision**: Any explicit catching of `Exception` (if absolutely needed via a `RunCatching` style inline utility that is specifically typed for known exceptions) MUST explicitly rethrow `CancellationException` to avoid breaking Kotlin's structured concurrency.
**Rationale**: Fulfills FR-005. Catching `CancellationException` causes coroutines to hang and leak memory.
