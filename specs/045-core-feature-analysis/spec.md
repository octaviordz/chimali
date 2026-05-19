# Feature Specification: Core Feature Migration

**Feature Branch**: `045-core-feature-analysis`
**Created**: 2026-05-18
**Status**: Draft
**Input**: Migrate agreed-upon cross-cutting concerns from feature modules to their correct core module homes, based on the architectural critique in `critique.md`.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Unified Encrypted Database Storage (Priority: P1)

As a developer building any feature module (Vault, FIDO2, Editor), I need encrypted database storage to be provided by a single, centralized service so that every feature module enforces identical encryption standards without duplicating encryption configuration logic.

**Why this priority**: The project constitution (§I.3) mandates encrypted storage for all feature databases. Today, the encrypted database factory logic exists as an incomplete stub inside the FIDO2 feature module, while the core database module already declares the encryption dependency but has no implementation. This is the highest-impact migration because it directly enables two feature modules (Vault and FIDO2) to share a single encryption policy, eliminating duplicated security-critical code.

**Independent Test**: Can be fully validated by creating a database through the centralized service and verifying that the resulting database file is encrypted, that integrity checks pass, and that the encryption key is properly derived — all without involving any feature-specific business logic.

**Acceptance Scenarios**:

1. **Given** a feature module requests a new encrypted database, **When** it creates the database through the centralized service, **Then** the resulting database file is encrypted at rest and can only be read with the correct derived key.
2. **Given** a feature module opens an existing encrypted database, **When** it supplies the correct credentials, **Then** the data is decrypted and accessible transparently.
3. **Given** an encrypted database exists, **When** an integrity verification is requested, **Then** the system reports whether the database is intact or corrupted.
4. **Given** a feature module attempts to create a database with an empty or invalid key, **When** the centralized service processes the request, **Then** the operation fails with a clear, descriptive error — never silently creating an unencrypted database.

---

### User Story 2 - Secure Clipboard with Auto-Clear (Priority: P2)

As a user copying sensitive data (passwords, TOTP codes, recovery keys, credential IDs), I need the clipboard to be automatically cleared within a defined timeout so that sensitive information does not persist on my device longer than necessary.

**Why this priority**: The constitution (§IV) mandates that sensitive clipboard data must be cleared within 60 seconds. Today, the clipboard wrapper is a non-functional stub in the Vault module, and the FIDO2 DevTools screen uses a completely independent clipboard mechanism. Centralizing this ensures consistent security policy enforcement across all features that handle sensitive strings.

**Independent Test**: Can be tested by copying a sensitive value, waiting for the configured timeout, and verifying that the clipboard no longer contains the original value — regardless of which feature initiated the copy.

**Acceptance Scenarios**:

1. **Given** a user copies a sensitive value from any feature, **When** the configured timeout elapses (default: 60 seconds), **Then** the clipboard is automatically cleared.
2. **Given** a user copies a sensitive value and then copies a different non-sensitive value, **When** the auto-clear timer fires, **Then** only the original sensitive clipboard entry is affected (the newer unrelated content is preserved).
3. **Given** the user manually clears the clipboard before the timeout, **When** the auto-clear timer fires, **Then** no error occurs and the system handles the already-empty clipboard gracefully.
4. **Given** the application is backgrounded or killed after a sensitive copy, **When** the timeout elapses, **Then** the clipboard is still cleared (within platform constraints).

---

### User Story 3 - Centralized Crypto Provider Initialization (Priority: P3)

As a developer, I need the cryptographic security provider to be registered once at application startup so that any feature module that requires cryptographic operations can rely on the provider being available, without each module independently registering it.

**Why this priority**: Currently, the cryptographic provider is lazily registered inside the FIDO2 module initializer. If any other feature (e.g., Vault post-quantum operations) attempts to use the same provider before FIDO2 initializes, it will fail silently or crash. Moving the registration to application startup is a small, low-risk change with a defensive benefit.

**Independent Test**: Can be validated by verifying that cryptographic operations succeed immediately at application startup — before any feature module has initialized.

**Acceptance Scenarios**:

1. **Given** the application has just launched and no feature module has initialized, **When** a cryptographic operation is requested, **Then** the security provider is already available and the operation succeeds.
2. **Given** the security provider is registered at startup, **When** the FIDO2 module subsequently initializes, **Then** it does not redundantly re-register the provider and no conflict occurs.
3. **Given** the provider registration fails at startup (e.g., due to a device-level restriction), **When** a feature module attempts a crypto operation, **Then** a clear error is surfaced rather than a silent failure.

---

### User Story 4 - Shared Biometric Capability Check (Priority: P4)

As a developer building any feature module that may conditionally enable biometric-gated functionality, I need a shared, platform-independent way to query whether biometric hardware is present and enrolled, so that each module does not re-implement its own platform detection logic.

**Why this priority**: The biometric capability check (`PlatformUserVerification` expect class) is currently inside the FIDO2 feature module but contains no FIDO2-specific logic — it simply queries device hardware. Extracting this small, self-contained class to a core module enables future features (e.g., Vault biometric unlock) to check biometric readiness without depending on the FIDO2 module. This is the lowest priority because no second consumer exists today; this migration is proactive and small.

**Independent Test**: Can be validated by calling the capability check from a test that has no FIDO2 module dependency and verifying correct hardware detection results.

**Acceptance Scenarios**:

1. **Given** a device with enrolled biometrics, **When** any module queries biometric availability, **Then** the check returns true — without importing or depending on any feature module.
2. **Given** a device without biometric hardware, **When** any module queries biometric availability, **Then** the check returns false.
3. **Given** a device with biometric hardware but no enrolled biometrics, **When** any module queries availability, **Then** the check distinguishes between "hardware present but not enrolled" and "no hardware."

---

### Edge Cases

- What happens if the encrypted database key derivation produces an invalid key (e.g., empty byte array)? The system must reject it and surface an error.
- What happens if the clipboard auto-clear fires but the app process has been killed? Platform-level clipboard behavior takes precedence; the system should not crash on restart.
- What happens if the security provider is already registered by the OS or a third-party library? The initialization must be idempotent — checking before adding.
- What happens if the biometric capability check is called on a platform target that has no implementation yet (e.g., iOS stub)? The check must return a safe default (false) rather than throwing.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST provide a centralized encrypted database factory service accessible to all feature modules.
- **FR-002**: The centralized database factory MUST derive encryption keys from the application's master key following the project's established key derivation standards.
- **FR-003**: The centralized database factory MUST provide a database integrity verification mechanism that reports pass/fail status without exposing implementation details.
- **FR-004**: The system MUST provide a secure clipboard service that automatically clears sensitive data from the system clipboard after a configurable timeout (default: 60 seconds).
- **FR-005**: The clipboard auto-clear MUST be content-aware — it must not erase clipboard content that was replaced by the user or another application after the sensitive copy.
- **FR-006**: The system MUST register its cryptographic security provider exactly once during application startup, before any feature module initializes.
- **FR-007**: Cryptographic provider registration MUST be idempotent — calling it when the provider is already registered must not cause errors or duplicate registrations.
- **FR-008**: The system MUST provide a platform-independent biometric capability check that reports whether biometric hardware is present, whether biometrics are enrolled, and whether the device has at least a screen lock.
- **FR-009**: The biometric capability check MUST be side-effect free — no UI prompts, no coroutines, no blocking I/O.
- **FR-010**: All migrated services MUST be injectable via the project's dependency injection framework without requiring feature module dependencies.
- **FR-011**: After migration, the stub implementations remaining in feature modules (SqlCipherWrapper, ClipboardManagerWrapper) MUST be deleted — no dead code.

### Key Entities

- **Encrypted Database Factory**: A service that produces encrypted, integrity-verified database instances for any feature module. Key attributes: encryption key derivation, integrity verification, error reporting.
- **Secure Clipboard Manager**: A service that wraps system clipboard access with automatic expiry of sensitive content. Key attributes: configurable timeout, content-aware clearing, platform-specific behavior.
- **Biometric Capability Descriptor**: A value describing the device's biometric readiness. Key attributes: hardware present, biometrics enrolled, device lock configured.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All feature modules that require encrypted database storage obtain it from a single centralized service — zero duplicate encryption configuration code exists across the codebase.
- **SC-002**: Sensitive data copied to the clipboard from any feature is automatically cleared within 60 seconds, with zero user intervention required.
- **SC-003**: Cryptographic operations succeed immediately at application startup, with zero feature-module initialization dependencies.
- **SC-004**: The biometric capability check is callable from any module without depending on any feature module — verified by a test that uses only core module dependencies.
- **SC-005**: After migration, the existing test suite passes with zero regressions — no existing test is deleted, only relocated or updated.
- **SC-006**: The project's local CI pipeline passes end-to-end with zero new warnings or errors.

## Assumptions

- The existing core database module's encryption dependency is compatible with the feature modules' database usage patterns and requires no version upgrade.
- The clipboard auto-clear timeout of 60 seconds, as mandated by the project constitution (§IV), is the correct default and does not require runtime configurability by end users (developer-configurable only).
- The cryptographic security provider used by the FIDO2 module is the same provider that future feature modules will require — no second provider registration is anticipated.
- The biometric capability check's existing behavioral contract (three boolean queries: `isAvailable`, `canAuthenticate`, `isDeviceSecure`) is sufficient for future consumers and does not require interface changes.
- Platform stubs (e.g., iOS) for the biometric capability check will return safe defaults (false) and are acceptable as placeholder implementations until iOS development begins.
- The existing dependency injection configuration can accommodate the relocated services without architectural changes to the DI framework setup.
