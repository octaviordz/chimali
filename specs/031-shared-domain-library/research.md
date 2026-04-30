# Research: Shared Domain Library

## Decisions

### 1. KMP Randomness Generation
- **Decision**: Use `org.kotlincrypto.random.CryptoRand` (provided transitively by the existing `signum` dependency).
- **Rationale**: `signum` is already an established part of the project's cryptographic stack. `CryptoRand.Default.nextBytes` uses the platform's native secure random generator (`SecureRandom` on JVM, `SecRandomCopyBytes` on Apple, etc.), making it perfectly suited for `CredentialId.generate()`.
- **Alternatives considered**: 
  - Writing custom `expect/actual` wrappers around `java.security.SecureRandom` and iOS equivalents (rejected due to reinventing the wheel and maintenance overhead).
  - Using `kotlin.random.Random` (rejected because it is not cryptographically secure by default).

### 2. Identifier Type Safety
- **Decision**: Use `@JvmInline value class` for all new ID types (`CredentialId`, `RpId`, `UserId`, `PasskeyId`).
- **Rationale**: Value classes provide strong compile-time type safety while avoiding the runtime overhead of object allocation on the JVM, completely resolving the "primitive obsession" issue.
- **Alternatives considered**: 
  - Standard `data class` (rejected due to unnecessary heap allocations).
  - `typealias` (rejected because it does not enforce compile-time type safety).

### 3. Date & Time Types
- **Decision**: Use `kotlinx.datetime.Instant`.
- **Rationale**: It is the standard KMP solution for time and allows domain models like `RelyingParty` and `CredentialSummary` to be fully multiplatform without relying on `java.time.Instant`.
- **Alternatives considered**: None. `kotlinx-datetime` is the defacto standard for Kotlin Multiplatform.
