# Data Model & Entities

## Entities

### `DomainError` (Sealed Interface)
Represents categorized application errors.
- **Fields**:
  - `cause: Throwable?` (The original exception for boundary logging, optional)
  - `message: String` (A developer-friendly message)
- **Sub-types** (Examples):
  - `NetworkError(cause: IOException)`
  - `DatabaseError(cause: SQLiteException)`
  - `SecurityError(cause: SecurityException)`
  - `UnknownError(cause: Throwable)` (Only used as a fallback when specific APIs throw undocumented runtime exceptions)

### `DataResult<out D, out E : DomainError>` (Sealed Interface)
Wrapper for functional success/failure.
- **Sub-types**:
  - `Success<D>(val data: D) : DataResult<D, Nothing>`
  - `Error<E : DomainError>(val error: E) : DataResult<Nothing, E>`

## State Transitions
When an operation is executed in the repository layer:
1. Try block executes specific API.
2. If successful, wraps response in `DataResult.Success`.
3. If specific exception is caught, it is logged immediately via Kermit (boundary logging).
4. The exception is mapped to a specific `DomainError` subtype.
5. The `DomainError` is wrapped in `DataResult.Error` and returned to the ViewModel.
6. The ViewModel uses an exhaustive `when` to update UI state based on `Success` or `Error`.
