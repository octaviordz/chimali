# Implementation Plan: Restore credential crypto in optimized builds

**Branch**: `bug/056-credential-registration` | **Date**: 2026-10-15 | **Spec**: [spec.md](spec.md)

## Summary

Reproduce missing credential crypto services in the optimized app. Preserve the Bouncy Castle
mapping classes and service constructors needed by credential algorithms. Verify conversion and
signatures from the target APK without test-only crypto retention rules.

## Technical Context

**Language/Version**: Kotlin, AGP 9.4.1, Gradle wrapper, Android Studio JDK.
**Primary Dependencies**: bcprov-jdk18on 1.80, AndroidX Test, R8.
**Storage**: No schema or stored-key changes.
**Testing**: AndroidJUnit4 against shrunkDebug; existing JUnit 5 host tests.
**Target Platform**: Android API 28+, ASUS AI2302/API 35 available.
**Project Type**: Android app with KMP libraries.
**Performance Goals**: Keep shrinking enabled and release bundle below 50 MB.
**Constraints**: No whole-library crypto keeps, secret diagnostics, or derivation changes.
**Scale/Scope**: S; one consumer-rules file and one installed-app regression class.

## Constitution Check

- Security: synthetic provider tests only; no wallet data, seed, or credential changes.
- Architecture: rules belong to the library owning the crypto dependency.
- Verification: load optimized target classes, not a test copy of BC.
- Simplicity: preserve reflection entry points rather than changing cryptography.
- Binary budget: compare matched release AAB before/after; seek architecture approval before
  integration if growth exceeds 500 KB. Initial and post-design checks pass, pending measurement.

## Project Structure

Documentation: `specs/056-credential-registration/{spec,plan,research,data-model,quickstart,tasks}.md`.
Source: `feature/fido2/proguard-rules.pro`.
Regression: `app/src/androidTest/kotlin/com/chimali/CredentialCryptoProviderTest.kt`.

**Structure Decision**: Use the existing consumer rules and optimized instrumentation variant.

## Verification Plan

1. Run target-provider checks before changing rules and record exact failures.
2. Add targeted mappings and service-constructor retention rules.
3. Repeat the same tests on ASUS; run existing FIDO2 host tests and static checks.
4. Build release AAB; compare matched size and inspect retained classes.
5. Record browser validation limits and migration applicability without applying its stash.
