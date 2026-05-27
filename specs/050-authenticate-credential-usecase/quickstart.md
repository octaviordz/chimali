# Quickstart: Authenticate Credential Use Case

**Branch**: `050-authenticate-credential-usecase` | **Date**: 2026-05-26

## Overview

This refactoring extracts the `authenticateCredential` delegation from `Fido2ServiceImpl` into a dedicated `AuthenticateCredentialUseCase` class, following the established use case pattern.

## Changes at a Glance

| Action | File | Description |
|--------|------|-------------|
| **CREATE** | `domain/usecase/AuthenticateCredentialUseCase.kt` | New use case: `@Factory`, injects `Fido2Repository`, `operator fun invoke(rpId)` |
| **MODIFY** | `domain/service/impl/Fido2ServiceImpl.kt` | Inject use case, delegate `authenticateWithCredential` through it |

## How It Works

### Before (current)

```
Fido2ServiceImpl.authenticateWithCredential(rpId)
    └── fido2Repository.authenticateCredential(rpId)   // direct call
```

### After (refactored)

```
Fido2ServiceImpl.authenticateWithCredential(rpId)
    └── authenticateCredentialUseCase(rpId)             // use case delegation
            └── fido2Repository.authenticateCredential(rpId)
```

## Verification

```powershell
# Compile
.\gradlew :feature:fido2:compileDebugKotlin

# Run tests
.\gradlew :feature:fido2:testDebugUnitTest

# Lint checks
.\gradlew :feature:fido2:detekt :feature:fido2:ktlintCheck
```

## Next Steps

After implementation, proceed to `/speckit-tasks` for task breakdown, or implement directly given the small scope (S complexity).
