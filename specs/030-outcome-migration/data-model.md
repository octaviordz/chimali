# Data Model: Outcome Migration

This feature focuses on functional error handling rather than introducing new data entities.

## Entities

- **`Outcome<out D, out E : DomainError>`**: 
  - Represents the result of an operation. 
  - Transitions: Can be either `Success<D>` or `Error<E>`.
  - Operations: `map`, `flatMap`, `onSuccess`, `onFailure`.
