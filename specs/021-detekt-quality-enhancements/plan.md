# Implementation Plan: Detekt Quality Enhancements

**Branch**: `021-detekt-quality-enhancements` | **Date**: 2026-04-25 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/021-detekt-quality-enhancements/spec.md`

## Phase 1 (Original Scope)

### Summary
The goal of this phase is to strengthen the project's static analysis by enforcing higher standards for logging, idiomatic Kotlin usage, and string constant management. We will enable and configure three key Detekt rules (`ForbiddenMethodCall`, `UnnecessaryLet`/`UseLet`, and `StringLiteralDuplication`) to automate code quality gates that currently rely on manual review.

### Technical Context
**Language/Version**: Kotlin 2.1.10  
**Primary Dependencies**: Detekt 1.23.8  
**Project Type**: Mobile Application Infrastructure  

### Constitution Check
1. **Principle III (Architecture & Quality)**: **PASS**.
2. **Principle VIII (Local CI/CD)**: **PASS**.

---

## Phase 6-10 Addendum: Hardened Quality Gates (2026-04-25)

### Summary
Strengthen the project's static analysis by enforcing higher standards for logging, collection correctness, and performance optimization. We will enable and configure specific Detekt rules (`ForbiddenMethodCall`, `Deprecation`, `DontDowncastCollectionTypes`, `CouldBeSequence`, `WildcardImport`, and `StringShouldBeRawString`) to automate quality gates that currently require manual review.

### Additional Constitution Check
1. **Principle IV (Performance & Reliability)**: **PASS**. Enforces `CouldBeSequence` to ensure high-volume data processing (10k+ items) adheres to performance budgets.

### Proposed Changes (Addendum)

#### [MODIFY] [detekt.yml](file:///D:/octav/source/repos/Chimali/config/detekt/detekt.yml)
- Update `ForbiddenMethodCall` to include fully-qualified names and `android.util.Log` methods.
- Enable `Deprecation` rule (excluding test source sets).
- Enable `DontDowncastCollectionTypes`.
- Enable `CouldBeSequence` with `threshold: 3`.
- Remove internal wildcard exception for `com.chimali.fido2.presentation.ui.components.*`.
- Enable `StringShouldBeRawString`.

## Verification Plan

### Automated Tests
- Run `./gradlew detekt` to verify violations are caught.
- Run `tools/local-ci.ps1` to ensure project-wide compliance.

### Manual Verification
- Deliberately introduce a `Log.d` call in a `main` source set and verify build failure.
- Verify that `Logger.d { ... }` (Kermit) remains permitted.
