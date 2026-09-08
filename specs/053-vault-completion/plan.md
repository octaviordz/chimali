# Implementation Plan: Vault Feature Completion (FR-VAULT-020)

**Branch**: `053-vault-completion` | **Date**: 2026-09-07 | **Spec**: [specs/053-vault-completion/spec.md](spec.md)

**Input**: Feature specification from `specs/053-vault-completion/spec.md`

## Summary

Bring back and complete the Vault feature in the Chimali application. While the base UI screens and database schemas exist, the Vault is currently inaccessible due to unhooked no-op callbacks in `AppNavGraph`, the absence of an internal `VaultNavGraph`, and incomplete payload encryption/decryption bridges in `VaultViewModel`. This plan defines the end-to-end integration: wiring navigation, implementing memory-safe AES-256-GCM payload serialization/deserialization, and connecting full CRUD operations for passwords, credit cards, and secure notes.

## Technical Context

**Language/Version**: Kotlin 2.1+, Kotlin Multiplatform (Android Target JVM 17)  
**Primary Dependencies**: Jetpack Compose (Material3), Navigation Compose, Koin (Annotations + ViewModel), SQLDelight, Kermit Logging  
**Storage**: SQLite with SQLite3MultipleCiphers / ChaCha20-Poly1305 (`VaultDatabase`) via `EncryptedDriverFactory`, plus Event Sourcing (`AggregateService<VaultCommand, VaultState>`)  
**Testing**: JUnit 4 / AndroidX Test Runner, MockK, Compose UI Testing  
**Target Platform**: Android (Min SDK 28)  
**Project Type**: Android Multi-Module / KMP Mobile App  
**Performance Goals**: 60 FPS list scrolling with 500+ items, <15s item creation, <2s navigation transition  
**Constraints**: AES-256-GCM payload encryption, zero plain-text in persistent memory, CharArray zeroing (`fill('0')`) in `finally` blocks, 60s clipboard clear  
**Scale/Scope**: 3 credential types (Passwords, Cards, Notes), 8 UI screens, 1 navigation graph  

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- [x] **Security First (Principle I)**: All payloads encrypted with AES-256-GCM before database insertion. Sensitive fields held in mutable `CharArray` and zeroed out immediately after use.
- [x] **Master Seed Architecture (Principle II)**: Payload encryption keys derived deterministically from master seed using `EventStoreKeyProvider`.
- [x] **Architecture & Quality (Principle III)**: MVI architecture via `VaultViewModel`, clean modular separation in `feature:vault`, Koin dependency injection, Ktlint/Detekt compliance.
- [x] **Performance & Reliability (Principle IV)**: Fast database queries, 60s clipboard auto-clear, lazy list rendering for 500+ vault items.
- [x] **Event Sourcing (Principle VIII)**: Write model commands routed through `AggregateService<VaultCommand, VaultState>` and projected to `vault_entry`.
- [x] **DO-178B Critical Code (Principle XII)**: Requirement-to-code traceability, fail-safe error handling, zero unreferenced dead code.

## Project Structure

### Documentation (this feature)

```text
specs/053-vault-completion/
├── plan.md              # This file
├── research.md          # Cryptographic and navigation architecture decisions
├── data-model.md        # Entities, payload structures, schema mapping
├── quickstart.md        # End-to-end validation scenarios
├── contracts/
│   ├── vault-navigation-contract.md # UI routes and callbacks
│   └── vault-crypto-contract.md     # Serialization & encryption interface
└── checklists/
    └── requirements.md  # Specification quality checklist
```

### Source Code (repository root)

```text
app/
└── src/main/kotlin/com/chimali/navigation/
    └── AppNavGraph.kt               # Delegate to VaultNavGraph instead of stubbed VaultListScreen

feature/vault/
├── src/main/java/com/chimali/feature/vault/
│   ├── api/
│   │   ├── VaultMvi.kt              # Updated MVI states & intents for typed payloads
│   │   └── VaultService.kt          # Vault item repository contract
│   ├── internal/
│   │   ├── VaultModule.kt           # Koin DI declarations
│   │   ├── VaultRepositoryImpl.kt   # SQLDelight + AggregateService implementation
│   │   ├── VaultViewModel.kt        # MVI ViewModel orchestrating crypto and persistence
│   │   ├── crypto/
│   │   │   ├── VaultCryptoService.kt # Interface for payload encryption/decryption
│   │   │   └── VaultCryptoServiceImpl.kt # Implementation using AesEncryptionManager
│   │   └── payload/
│   │       ├── PasswordPayload.kt
│   │       ├── CreditCardPayload.kt
│   │       ├── SecureNotePayload.kt
│   │       └── CustomField.kt
│   └── ui/
│       ├── VaultNavGraph.kt         # [NEW] Compose Navigation Graph for feature:vault
│       ├── VaultListScreen.kt       # Updated with item selection modal/callbacks
│       ├── PasswordEntryScreen.kt
│       ├── PasswordDetailScreen.kt
│       ├── CreditCardEntryScreen.kt
│       ├── CreditCardDetailScreen.kt
│       ├── SecureNoteEntryScreen.kt
│       ├── SecureNoteDetailScreen.kt
│       └── LabelManagerScreen.kt
└── src/test/java/com/chimali/feature/vault/
    └── internal/crypto/
        └── VaultCryptoServiceTest.kt # Unit tests verifying encryption/decryption & zeroing
```

---

## Planned Phases

### Phase 1: Cryptographic Service & Payload Serialization
1. Implement `VaultCryptoService` and `VaultCryptoServiceImpl` using `EncryptionManager` (`AesEncryptionManager`) and `EventStoreKeyProvider`.
2. Add comprehensive unit tests in `VaultCryptoServiceTest` verifying encryption, decryption, and `clearMemory()` zeroing for `PasswordPayload`, `CreditCardPayload`, and `SecureNotePayload`.

### Phase 2: Navigation & Feature Graph (`VaultNavGraph`)
1. Create `VaultNavGraph.kt` managing internal `NavHost` for all vault routes (`vault/list`, `vault/entry/*`, `vault/detail/*`, `vault/labels`).
2. Add type-selector dialog/bottom-sheet on FAB click in `VaultListScreen` to allow choosing Password, Card, or Note.
3. Update `AppNavGraph.kt` in the `app` module to host `VaultNavGraph(onOpenSettings = onOpenSettings)`.

### Phase 3: ViewModel & End-to-End CRUD Integration
1. Wire `VaultViewModel` to use `VaultCryptoService` for decrypting selected items upon opening detail screens.
2. Connect `onSave` handlers from entry screens through `VaultViewModel` to save items into the database.
3. Connect `onDelete` handlers in detail screens.
4. Connect `LabelManagerScreen` for creating and assigning organization labels.

### Phase 4: Verification & Polish
1. Run `./gradlew :feature:vault:testDebugUnitTest`.
2. Run `./gradlew detekt ktlintCheck`.
3. Verify zero plaintext trace in memory dumps and clipboard timeout clearing.
