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
**Constraints**: AES-256-GCM payload encryption, zero plain-text in persistent memory, CharArray zeroing (`fill('\u0000')`) in `finally` blocks, 60s clipboard clear
**Scale/Scope**: 3 credential types (Passwords, Cards, Notes), existing entry/detail/list screens, 1 navigation graph, mutable ownership and payload codec; Phase 6 adds a UI feasibility gate and verification increments without a wholesale redesign.  

## Preserved implementation constraints from specification review

The 2026-09-07 specification-quality correction expresses behavior in `spec.md` and retains the following implementation obligations here; it does not relax security or expand scope:

- **FR-VAULT-021**: Reuse the existing Compose list, password/card/note entry and detail screens, and `LabelManagerScreen`; connect them through `VaultNavGraph` and `AppNavGraph` as described below. Refine the existing screens rather than rebuilding them from scratch.
- **FR-VAULT-022–023**: Serialize typed `PasswordPayload`, `CreditCardPayload`, and `SecureNotePayload` into AES-256-GCM encrypted binary payloads before persistence, and decrypt/deserialize them for display. AES-256-GCM remains mandatory under Constitution I. Preserve field values and additive schema compatibility.
- **FR-VAULT-026**: Use mutable `CharArray`/`ByteArray` for all sensitive in-memory representations and explicitly zero them immediately after use, including failure paths, using `finally` as required by Constitution I and X.5.
- **Entity mapping**: The specification's Vault entry maps to `VaultItem`, including encrypted payload, CRDT state, timestamps, backup status, and owner identity. Password, credit card, and secure note contents map to their existing typed payload classes. `VaultNavGraph` is a navigation implementation component, not a business entity.
- **Existing dependencies**: Reuse `EncryptedDriverFactory`, `VaultDatabase`, the existing master-seed/key-derivation infrastructure, and `ClipboardManagerService`. Production startup and storage integration still require the Phase 5 verification below.

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

### Phase 5: Runtime Save Regression (2026-09-07)

**Traceability**: FR-VAULT-024, FR-VAULT-029, FR-VAULT-032 through FR-VAULT-034; SC-VAULT-006, SC-VAULT-009, SC-VAULT-010; US1/AC5–AC6. This amendment precedes convergence; convergence only appends remaining tasks.

#### Evidence and design decisions

- The reported exception is `VaultCommand$Create cannot be cast to PasskeyCommand`, at `VaultRepositoryImpl.kt:81` entering the bridge method on `PasskeyAggregateServiceImpl`. The failed command does not reach event append or the Vault projection insert.
- `app/src/main/kotlin/com/chimali/ChimaliApplication.kt` loads `Fido2Module().module` and `vaultModule`, but omits `coreDataModule`. The latter contains the Vault aggregate and storage providers.
- Generic arguments do not disambiguate Koin registrations: generated FIDO2 wiring binds the raw `AggregateService` type, while `VaultModule.kt` requests the same unqualified type. Both event-store providers likewise expose unqualified `EventStoreRepository`. Merely loading the missing module would introduce collisions and could break passkeys depending on registration order.
- Use the existing named-qualifier approach: qualify aggregate services and event stores consistently as `vault` and `passkey` at every registration and injection point. Retain the already qualified snapshot repositories. Use the DSL `coreDataModule` as the authoritative Vault persistence registration, audit overlapping annotations to avoid alternate unqualified bindings, and do not edit generated KSP output. No new interfaces or modules are necessary.
- Update `core/data/src/main/kotlin/com/chimali/core/data/di/DataModule.kt`, `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/di/Fido2Module.kt`, `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/repository/CredentialRepositoryImpl.kt`, and `feature/vault/src/main/java/com/chimali/feature/vault/internal/VaultModule.kt`. Audit constructors in both aggregate implementations and all consumers of these interfaces. Then load `coreDataModule` in `ChimaliApplication.kt`.
- Preserve database schemas, event formats, encryption, and existing records. Verify Vault events/snapshots use Vault storage and passkey events/snapshots use FIDO2 storage in both directions.
- In `VaultRepositoryImpl.kt` and the crypto service, translate specific expected technical failures at service boundaries into `Outcome.Error` with safe user messages. Preserve coroutine cancellation and zero buffers in `finally`, including key-acquisition failures. Do not use broad catches or catching `ClassCastException` as a substitute for fixing dependency wiring.
- Add explicit mutation completion state/effects to `VaultMvi.kt` and `VaultViewModel.kt`; update `ui/navigation/VaultNavGraph.kt` and entry screens to navigate only on save success, retain draft input on failure, and disable duplicate submission while pending. Keep retry identity stable. Clear the editor draft on successful completion, confirmed discard, or disposal; clear each operation's independent sensitive buffers on every terminal path, including failure, as detailed in Phase 6.

#### Regression verification

1. Add `app/src/androidTest/kotlin/com/chimali/di/VaultPasskeyWiringTest.kt` using the production module composition and generated FIDO2 registrations. Resolve actual aggregate and event-store implementations for both domains and assert their types, qualifiers, and storage destinations. Test both relative registration orders. Do not replace the aggregate under test with a mock or construct it directly; isolate only platform prerequisites where needed.
2. Replace the placeholder `feature/vault/src/androidTest/java/com/chimali/feature/vault/internal/VaultServicePasswordTest.kt` with real storage assertions and extend coverage to all three types. Exercise create, read/reopen, update, and delete with real event services; verify events and projections and unchanged pre-existing passkey records. Include a passkey save/authentication regression using existing FIDO2 test infrastructure.
3. Add save-failure, pending-save, cancellation, and successful-retry tests in `feature/vault/src/test/java/com/chimali/feature/vault/internal/VaultViewModelTest.kt` and navigation instrumentation tests. Assert the form remains on failure and leaves exactly once on success.
4. Run `./gradlew :app:assembleDebug :feature:vault:testDebugUnitTest :core:data:testDebugUnitTest`; discover the current FIDO2 host-test task rather than assuming its legacy name. Run relevant connected Android tests (`:app:connectedDebugAndroidTest`, `:feature:vault:connectedDebugAndroidTest`) on an available device and `./gradlew detekt ktlintCheck`. Record actual results and environment blockers; do not mark device scenarios passed without execution.
5. Extend `quickstart.md` with the SC-VAULT-009/010 matrix, using synthetic secrets: fresh/existing installation, cold restart, all three entry types, unchanged passkeys, pending/failed save, and retry. Never clear real user data to validate the correction.

**Constitution reassessment**: The existing checkmarks above describe design intent, not current compliance. Observed uncaught service failures and plaintext String retention require remediation under I, X.2, X.5, X.7, and XII.5. Existing incomplete UI/label flows remain within the original spec and will be recorded separately by convergence. No schema migration or cryptographic redesign is part of the routing correction.

### Phase 6: T039 Secret Ownership, Compatible Codec, and UI Erasure (2026-09-08)

**Traceability**: FR-VAULT-022–027, FR-VAULT-029, FR-VAULT-031, FR-VAULT-033–035; SC-VAULT-002, SC-VAULT-004–006, SC-VAULT-010–012; US1/AC6–AC7, US2/AC4, US3/AC4–AC5; Constitution I, X.2, X.5, XII.3, XII.5. Approved Constitutions 1.0.0/1.1.0 govern the completed T039 implementation. The terminal-throw evidence exception is limited to the one documented codec EOF branch; prior completion markers alone remain insufficient evidence.

#### 6.1 Field policy and audited platform text boundaries (approved 2026-09-08)

- Record a field-by-field classification and lifetime matrix in `research.md`, with matching representations in `data-model.md`. Cover usernames, passwords, URI, cardholder name, PAN, expiration, CVV, note bodies, notes, titles (including secure-note titles), and custom-field names/values regardless of concealment. Treat entry contents as sensitive by default. Any display-metadata classification must cite its authorized use and the governing Constitution; a current `String` property or public visibility is not an exemption. List titles remain available for their authorized list lifetime, with their representation and cleanup audited through `VaultItem`, list UI, and repository boundaries where necessary. Do not extend title display permission to hidden payload contents.
- Audit the **resolved project versions** of Compose input, snapshots, rendering, undo, saved state, accessibility, autofill, IME, and clipboard integration. Record exact versions/source references and reproducible synthetic-secret evidence. Upstream source is a lead, not proof of the installed version's behavior. `CharArray` behind a String-valued text field, `TextFieldValue`, `TextFieldState`, `SecureTextField`, and `clearText()` are not presumed to guarantee erasure. Do not place secret drafts in `rememberSaveable`, `SavedStateHandle`, navigation arguments, or automatic plaintext restoration.
- Select the smallest input/rendering component whose application-owned cleanup and audited platform controls meet Constitution 1.0.0 I.5, demonstrating editing, multiline notes, reveal/masking, selection, and existing copy behavior. Audit any framework buffer copies, caches, replaced storage, or undo history; clearing only live visible text is insufficient. Preserve existing screen layouts. A custom component or dependency is justified only by concrete evidence that simpler available components cannot comply.
- **Gate**: Apply the separately approved Constitution 1.0.0 I.5 exception. For each platform input/rendering/accessibility/clipboard boundary, record exact dependency/API versions, classified fields, purpose, lifetime, ownership, configured retention/disclosure controls and residual exposure. Permit only necessary adapter Strings; never retain them in app models, drafts, baselines, caches, saved state or serialization. Disable unnecessary app-controllable restoration/history/learning/autofill/caching/sharing, or explicitly justify and test it. Current widgets are candidates, not automatically compliant. Verify owned-buffer erasure, owned-reference release and platform controls separately; do not claim erasure of external copies. Constitution 1.1.0's direct-terminal-throw evidence exception applies only as recorded in `t039-coverage-proposal.md`.
- The existing passkey `CredentialListScreen` and `CredentialDetailsScreen` in `feature/fido2/.../presentation/management/` are presentation references only: the details component is a metadata `AlertDialog`, and `PasskeyCredential` holds a public key and private-key alias rather than private-key bytes. The registration UI's explicit state rendering is useful precedent. No passkey UI rewrite, feature-module coupling, or passkey serialization change is required.

#### 6.2 Ownership and lifetime contract

| Representation | Owner | Required cleanup / transfer |
|---------------|-------|-----------------------------|
| Mutable draft and any comparison baseline | One editor session | Retain only during editing/pending/recoverable retry; erase on confirmed success, discard, or disposal. Wipe replaced/removed field buffers immediately. Any baseline is independently owned and erased with the session. |
| Submission payload, including custom fields | Save operation after explicit acceptance | Deep-copy all mutable contents from the draft. Erase on success, failure, rejection, cancellation, or abandoned dispatch. Never share arrays with a draft/detail payload. |
| Key, plaintext/UTF-8 and codec scratch buffers | One crypto invocation | Enclose allocation/acquisition and processing in guarded ownership; erase in `finally`, including partial encode/decode and key-provider failure. Wipe retired backing arrays on growth or replacement. |
| Successfully decoded detail payload | Detail session after delivery | Transfer explicitly on successful delivery; otherwise erase. Clear on departure/replacement/disposal and reacquire from storage when returning from an editor. |

- Use small typed mutable draft holders in the existing Vault module, with idempotent cleanup and redacted diagnostic output. Avoid generic framework abstractions or a new Gradle module. Update `internal/payload/*`, `internal/crypto/VaultCryptoService.kt`, `api/VaultMvi.kt`, `internal/VaultViewModel.kt`, and `ui/navigation/VaultNavGraph.kt` as necessary. Declare the consume/borrow/transfer contract at each boundary.
- Guard submission ownership outside `withContext`, and handle a cancelled `viewModelScope` or job that never starts. An in-coroutine `finally` alone cannot cover rejected or never-started work. On decryption, erase a produced payload if dispatcher cancellation prevents delivery to its caller. After successful delivery, the receiver owns cleanup; it must clear superseded selections and late results for abandoned sessions.
- Keep draft mutation on its owner thread and pass only independent submission copies to background crypto. Reject duplicate saves without leaks. Keep retry identity stable. Define cancel/dispose during pending work, suppress late navigation for another session, and do not claim rollback of an already committed storage operation. Disable editing while a submission is pending so successful completion cannot discard newer unsaved edits.
- Clear the editor explicitly at success/discard and use disposal as a fallback, rather than relying solely on delayed navigation-animation disposal. Dirty detection must compare mutable contents without unowned temporary secret arrays/Strings and include label changes alongside all editable fields.
- When editing begins, establish the independent draft before clearing detail ownership. On cancel, freshly decrypt the original persisted entry; after successful edit, reopening details decrypts the updated entry. Clear ViewModel selections and navigation references as well as screen-owned values; never reuse an object that another lifecycle owner erased.

#### 6.3 Compatible mutable-buffer codec

- Replace String DTOs, `encodeToString`, full-plaintext JSON Strings, and `decodeFromString` in `VaultCryptoServiceImpl.kt` with a bounded codec in `internal/crypto/` operating on owned mutable character/byte buffers in **both** directions. Evaluate existing library APIs first, including their internal buffers and pooling. A streaming API or `CharArray` serializer alone is not evidence of no immutable copies. Document the choice and buffer bounds in `research.md`; this is serialization work, not new cryptography.
- Preserve existing JSON property names, JSON string value types, required fields, null/default semantics, `encodeDefaults = true` behavior, optional custom fields, and `ignoreUnknownKeys = true` compatibility. Preserve UTF-8 and JSON escaping, Unicode/surrogate handling for previously valid contents, AES-256-GCM envelope, key derivation, and the `chimali_vault_payload_v1` label. Preserve the distinction between absent, null, and empty where the old contract distinguishes them. No database reset, migration, or passkey/event-store format change.
- Obtain the key before encoding where practical to avoid creating plaintext on key failure, while still protecting all acquisition/allocation paths with `finally`. Clear encoder/decoder scratch, partial decoded fields, and plaintext on every exit; retain only the delivered payload or ciphertext. Avoid pooled secret buffers unless their full backing storage is demonstrably erased before return to the pool.
- Preserve cancellation and map expected technical failures to safe `Outcome.Error` values. Do not retain/log parser excerpts, secret-bearing DTO diagnostics, or exception causes containing plaintext. The existing exception-capture path must be audited as part of codec replacement.

#### 6.4 Verification and completion gates

1. Add tests in `src/test/java/com/chimali/feature/vault/internal/crypto/` that retain references to the actual input, key, plaintext, scratch, retired buffers, and partial decoded fields and assert they are overwritten. Cover all three types, custom fields, success, key failure/cancellation, codec/encryption failure, malformed decode, and cancellation before dispatch and during return delivery. Use nonempty synthetic secrets; an empty array or a copy retained by the test cannot prove cleanup of the production buffer.
2. Add draft tests under `src/test/java/com/chimali/feature/vault/ui/` and extend `internal/VaultViewModelTest.kt` for replacement/removal, deep-copy independence, rejected duplicate payloads, never-started jobs, failure with intact retry draft, cancellation/disposal, and successful completion. Test ownership races deterministically with controlled dispatchers, not timing sleeps. Critical logic must satisfy Constitution XII.3 coverage requirements.
3. Capture fixed legacy JSON/encrypted fixtures **before replacing the current codec**, using only synthetic data and the real existing AES implementation. Test old fixture → new reader and new writer → frozen legacy format oracle for all types, Unicode/escapes, required/optional/default/null/empty values, custom fields, and tolerated unknown fields. A new-writer/new-reader round trip alone is insufficient; ciphertext equality is not expected with randomized nonces.
4. Extend `src/androidTest/java/com/chimali/feature/vault/ui/navigation/VaultNavigationInstrumentationTest.kt` to exercise the actual Vault graph and entry/detail screens, not placeholder route labels. Cover detail → edit → cancel, failed save → retry → success, system back/confirmed discard, pending disposal, configuration recreation, custom fields, and fresh details after cleanup for all three types. Verify no secret saved-state restoration and no late completion affecting another session.
5. Execute relevant host tests, Android build, connected UI checks, and static checks following Phase 5, plus the dependency-specific platform adapter/retention-control audit, documenting external-copy limitations separately from app-owned memory checks. Use isolated synthetic fixtures and record exact commands, versions, measured results, and environment limitations in `quickstart.md`. Preserve clipboard timeout and passkey-isolation checks through T046/T044. Evidence from this amendment is new evidence; earlier checkmarks and source review alone do not satisfy it.
6. **Completion** requires ownership, codec compatibility, UI boundary, lifecycle, and test evidence to pass. Keep T039 open if any gate is unresolved; do not weaken FR-VAULT-026/SC-VAULT-005 or claim absolute JVM/IME erasure from array tests. Apply the approved I.5 boundary exception without weakening classified-field ownership or the unchanged coverage/runtime gates. Existing retained application String models, including VaultItem.title, still require remediation or separately justified classification.
