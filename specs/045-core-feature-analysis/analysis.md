# Architectural Analysis: Core vs. Feature Modules

**Date**: 2026-05-18
**Status**: Draft
**Topic**: Refactoring and Module Responsibility Alignment

## 1. Executive Summary
This document analyzes the current module structure of the Chimali project, specifically focusing on the distinction between `core` and `feature` modules. It identifies several components currently trapped in feature modules that represent cross-cutting concerns and proposes their migration to core modules to improve reusability, testability, and architectural integrity.

## 2. Definitions and Dependency Rules

### 2.1 Feature Modules (`:feature:*`)
- **Purpose**: Implement a complete vertical slice of user-facing functionality (e.g., FIDO2 Authenticator, Password Vault).
- **Content**: Pure business logic, UI/UX (Compose), ViewModels, and feature-specific data models.
- **Dependency Rule**: Can depend on multiple `core` modules. **Must never** depend on other `feature` modules.

### 2.2 Core Modules (`:core:*`)
- **Purpose**: Provide shared infrastructure, foundational building blocks, and platform abstractions.
- **Content**: Cryptography, Database engines, Design systems (UI components), Networking, and generic Domain models.
- **Dependency Rule**: Can depend on other `core` modules (following a hierarchy). **Must never** depend on any `feature` module.

---

## 3. Current State Analysis

### 3.1 The FIDO2 Module Ambiguity
- **Observation**: `:feature:fido2` is currently the primary home for almost all FIDO2 logic, including transport (Bluetooth HID) and security (Biometrics).
- **Issue**: An empty `:core:fido2` directory exists but is not included in the project.
- **Finding**: Some global domain models (e.g., `Passkey`) exist in `:core:domain`, while protocol-specific models (e.g., `PasskeyCredential`) are in `:feature:fido2`. This is correct, but the "service" logic for verification is misaligned.

---

## 4. Proposed Migrations

Based on industry best practices and the "Three-Use Rule" (Constitution §212), the following components are candidates for migration from `feature` to `core`:

### 4.1 User Verification Service
- **Current Location**: `:feature:fido2`
- **Proposed Location**: `:core:security` (or `:core:biometrics`)
- **Justification**: Both the Vault and FIDO2 features require biometric/PIN verification. Centralizing this allows for a unified security policy and simplifies KMP platform abstraction (`expect`/`actual`).

### 4.2 Bluetooth HID Device Wrapper
- **Current Location**: `:feature:fido2`
- **Proposed Location**: `:core:bluetooth`
- **Justification**: This is a low-level platform driver. Separating it from the FIDO2 business logic allows the Bluetooth stack to be tested and evolved independently.

### 4.3 Clipboard Manager Wrapper
- **Current Location**: `:feature:vault`
- **Proposed Location**: `:core:common`
- **Justification**: Secure, auto-clearing clipboard access is a utility needed by any module handling sensitive strings (TOTP codes, recovery keys, passwords).

### 4.4 SQLCipher Wrapper
- **Current Location**: `:feature:fido2`
- **Proposed Location**: `:core:database`
- **Justification**: Managing encrypted database factories and integrity checks is a database engine concern. Moving it to `:core:database` enables consistent encryption standards across all feature-specific databases.

### 4.5 Performance Warm-Up Utilities
- **Current Location**: `:feature:fido2`
- **Proposed Location**: `:core:common`
- **Justification**: Crypto provider registration and KeyStore HAL warming are global application lifecycle tasks. They should be triggered during app startup, not tied to a specific feature's entry point.

---

## 5. Implementation Strategy
1. **Phase 1**: Move interfaces to `:core:domain` or `:core:security` (commonMain).
2. **Phase 2**: Relocate platform-specific implementations to the respective core module's `androidMain`/`iosMain`.
3. **Phase 3**: Update Koin DI modules to provide these services from their new core homes.
4. **Phase 4**: Refactor feature modules to inject these core services, removing duplicate logic.

## 6. Conclusion
Aligning these components with the `Core-to-Feature` dependency flow will transform Chimali into a more robust "Internal Platform" architecture. This reduces build times, eliminates circular dependency risks, and ensures that security-critical code is audited and maintained in a single, centralized location.
