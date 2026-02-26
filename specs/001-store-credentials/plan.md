# Implementation Plan: Secure Credentials Vault (FR-VAULT-010)

**Branch**: `001-store-credentials` | **Date**: 2026-02-23 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `specs/001-store-credentials/spec.md`

## Summary

Implement a secure local vault for storing passwords, credit cards, and secure notes (including custom fields). This feature follows the "Security First" and "Master Seed" architecture principles, using AES-256-GCM for encryption and SQLDelight/SQLCipher for persistence. The design includes future-proofing for shared-secret backups (Shamir's Secret Sharing for the Master Seed and Loro.dev for item synchronization) by tracking the backup state of the seed and individual vault items. The UI will be built with Jetpack Compose following Material Design 3.

## Technical Context

**Language/Version**: Kotlin 1.9.20+, Rust 1.75+ (for CRDT)  
**Primary Dependencies**: Jetpack Compose, Hilt, SQLDelight, SQLCipher, Android Keystore, Loro.dev (Rust), UniFFI (Rust-Kotlin Bridge)  
**Storage**: Encrypted SQLite (SQLCipher)  
**Testing**: JUnit 5, MockK, Compose UI Testing  
**Target Platform**: Android (Min SDK 28)
**Project Type**: Mobile (Kotlin Multiplatform ready structure)  
**Performance Goals**: 60 FPS Rendering, startup < 2s, clipboard clear < 60s  
**Constraints**: AES-256-GCM encryption, Zero plaintext in memory, No cloud storage  
**Scale/Scope**: Support 10,000+ vault items efficiently

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- [x] **Security First**: design avoids plaintext memory storage and uses AES-256-GCM.
- [x] **Master Seed Architecture**: aligns with HDK-ECDH-P256 and BIP39 (Seed provided by core module).
- [x] **Architecture & Quality**: follows MVI, uses Hilt, and respects feature-module boundaries.
- [x] **Performance & Reliability**: design targets Android Vitals (startup, smoothness).
- [x] **UX & Cross-Platform**: follows Material Design 3 patterns.

## Project Structure

### Documentation (this feature)

```text
specs/001-store-credentials/
├── plan.md              # This file
├── research.md          # Phase 0 results
├── data-model.md        # Phase 1 design
├── quickstart.md        # Phase 1 usage
├── contracts/           # Phase 1 output
└── tasks.md             # Phase 2 tasks (future)
```

### Source Code (repository root)

```text
core/
├── security/            # Encryption sub-keys & Master Seed logic
├── database/            # Shared SQLDelight schema with SQLCipher support
└── crdt/                # Rust-based Loro.dev integration (UniFFI)
    ├── rust/            # Cargo project with UniFFI UDL/proc-macros
    └── kotlin/          # Generated bindings from UniFFI

feature/
└── vault/               # New module for storing credentials
    ├── src/
    │   ├── api/         # MVI Intent and State definitions
    │   ├── internal/    # Implementation, ViewModels, Repositories
    │   └── ui/          # Compose screens and components
    └── tests/
```

**Structure Decision**: Multi-module architecture with a dedicated `feature:vault` module and shared `core` modules for security and database persistence.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| None | N/A | N/A |
