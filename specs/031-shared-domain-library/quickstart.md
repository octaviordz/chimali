# Quickstart: Using the Shared Domain Library

This guide demonstrates how to use the newly unified domain types in feature modules.

## Generating a new CredentialId

Instead of managing SecureRandom and Base64 in your feature module, use the built-in generator from `:core:domain`:

```kotlin
import com.chimali.core.domain.valueobject.CredentialId

// Generates a cryptographically secure 32-byte CredentialId
val newId = CredentialId.generate()
```

## Creating Strongly Typed Identifiers

When receiving data from external sources (e.g., UI, network, database), wrap strings in their respective value classes immediately:

```kotlin
import com.chimali.core.domain.valueobject.RpId
import com.chimali.core.domain.valueobject.UserId

val rpId = RpId("example.com")
val userId = UserId("user_12345")
```

## Using in Repositories

Update your repository interfaces to use the typed identifiers instead of raw `String`s.

**Before:**
```kotlin
fun getCredential(id: String): Outcome<PasskeyCredential, DomainError>
```

**After:**
```kotlin
fun getCredential(id: CredentialId): Outcome<PasskeyCredential, DomainError>
```

By ensuring that the boundary of your module expects strongly-typed IDs, you completely eliminate the bug class where an `RpId` is accidentally passed to a function expecting a `CredentialId`.
