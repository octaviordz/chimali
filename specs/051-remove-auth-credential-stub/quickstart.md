# Quickstart: Remove AuthenticateCredentialUseCase Stub and Consolidate into GetAssertionUseCase

**Branch**: `051-remove-auth-credential-stub` | **Date**: 2026-05-28

## Overview

This implementation plan removes the dead-code stub `AuthenticateCredentialUseCase` and its associated methods, consolidating all authentication workflow logic in the production ready `GetAssertionUseCase`. It also refactors `RegistrationPromptViewModel` to directly inject `RegisterCredentialUseCase`.

## Verification Steps

### Compile

Run the Kotlin compile task to ensure no broken references:

```powershell
.\gradlew :feature:fido2:compileDebugKotlin
```

### Run Tests

Verify all unit and integration tests pass successfully:

```powershell
.\gradlew :feature:fido2:testDebugUnitTest
```

### Run Static Analysis

Run Detekt and Ktlint:

```powershell
.\gradlew :feature:fido2:detekt :feature:fido2:ktlintCheck
```
