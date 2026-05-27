# Authenticate Credential Use Case Refactoring

**Date**: 2026-05-26

## Summary

Extracted the existing `authenticateCredential` delegation from `Fido2ServiceImpl` into a dedicated `AuthenticateCredentialUseCase` class. This is a pure structural refactoring (zero behavioral change) that aligns the authentication flow with the project's established Clean Architecture use case patterns.

## Detailed Changes

- **Created `AuthenticateCredentialUseCase`**: Added a new use case in `:feature:fido2` following the `@Factory` + `operator fun invoke()` pattern. It injects the `Fido2Repository` and delegates the authentication call identically to the previous service-level implementation.
- **Rewired `Fido2ServiceImpl`**: Updated the primary FIDO2 service to inject the new `AuthenticateCredentialUseCase` and delegate the `authenticateWithCredential` method through it.
- **Test-Driven Development (TDD)**: Authored `AuthenticateCredentialUseCaseTest` in `androidHostTest` prior to implementation, ensuring that the new use case strictly delegates to the repository for both success and error outcomes.
- **Quality Gates**: Verified that the refactored module fully complies with project standards by passing the existing `testAndroidHostTest` suite and executing with zero violations in `detekt` and `ktlintCheck`.

## Related Files

- `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/domain/usecase/AuthenticateCredentialUseCase.kt`
- `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/domain/usecase/AuthenticateCredentialUseCaseTest.kt`
- `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/domain/service/impl/Fido2ServiceImpl.kt`
