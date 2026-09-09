> **Approved scope update (2026-09-09):** Constitutions 1.0.0 and 1.1.0 and the approved T039 proposals govern this document. App-owned sensitive storage must remain mutable and explicitly cleaned; app references must be released. Only necessary, audited platform text adapters may use immutable copies, with configured controls and documented residual risk. Cryptographic/serialization cleanup, compatibility, critical coverage and actual runtime verification remain mandatory. The sole direct-terminal-throw coverage exception is recorded in `t039-coverage-proposal.md`.

# Research: Vault Feature Completion

**Feature**: `053-vault-completion`  
**Date**: 2026-09-07  
**Status**: Codec/ownership implemented; UI erasure gate unmet

## 1. Payload Serialization & Cryptographic Architecture

### Context
`PasswordPayload`, `CreditCardPayload`, and `SecureNotePayload` exist in `feature/vault/src/main/java/com/chimali/feature/vault/internal/payload/` holding sensitive fields in `CharArray` for zero-memory leakage. However, storing them in `VaultEntry` requires encrypting into a binary `payload: ByteArray`.

### Decision
Use a lightweight, deterministic serialization format (`kotlinx.serialization` or canonical binary/UTF-8 JSON) for in-memory payload representations, encrypted using `EncryptionManager` (AES-256-GCM via Android Keystore / Master Seed derived key) through `EventStoreKeyProvider.getEventStoreKey("chimali_vault_payload_v1")` or dedicated `VaultCryptoService`.

### Rationale
- **Constitution Principle I (Security First)**: AES-256-GCM is mandatory for general payload encryption with hardware keystore offloading and unique IVs per operation.
- **Constitution Principle II & X.5 (Master Seed Architecture & Memory Security)**: Key material derived via HKDF/HDK from master seed. Plaintext in memory must be held in `CharArray`/`ByteArray` and explicitly zeroed (`fill('\u0000')`) in `finally` blocks immediately after serialization/encryption.
- `AesEncryptionManager` already implements `EncryptionManager` (`encrypt(plaintext, key)` and `decrypt(ciphertext, key)` with 12-byte random IV prepended and 128-bit GCM auth tag).

### Alternatives Considered
- *Protobuf serialization*: Adds compilation overhead for 3 simple payload models and complicates mutable CharArray handling.
- *Raw string serialization*: Leaks plaintext into immutable JVM heap Strings.

---

## 2. Vault Navigation Graph Architecture

### Context
`VaultFeatureScreen` in `app/src/main/kotlin/com/chimali/navigation/AppNavGraph.kt` currently renders `VaultListScreen` directly with no navigation controller, passing empty lambdas `{}` to `onItemClick`, `onAddClick`, `onManageLabelsClick`, and `onLabelFilterClick`.

### Decision
Introduce `VaultNavGraph` within `feature:vault` (similar to `Fido2RegistrationNavGraph` in `feature:fido2`), managing its own internal `NavHost` and `NavController`.
Supported destinations:
1. `vault/list`: `VaultListScreen` with options to open Add menu, open Detail view, or manage labels.
2. `vault/add/{type}`: Dedicated entry screens (`PasswordEntryScreen`, `CreditCardEntryScreen`, `SecureNoteEntryScreen`).
3. `vault/detail/{id}/{type}`: Detail screens (`PasswordDetailScreen`, `CreditCardDetailScreen`, `SecureNoteDetailScreen`).
4. `vault/labels`: `LabelManagerScreen`.

The FAB on `VaultListScreen` can trigger an Add Type selection bottom sheet or dialog, routing to the chosen entry screen.

### Rationale
- Keeps modular isolation: the `app` module only embeds `VaultNavGraph(onOpenSettings = onOpenSettings)` inside `MainShell`.
- Mirrors the architecture of `Fido2RegistrationNavGraph`.
- Enables seamless back-stack navigation (e.g. from Entry/Detail back to List).

---

## 3. Vault Item CRUD & State Management

### Context
`VaultViewModel` manages MVI state via `VaultState` and `VaultIntent`. Currently:
- `SaveItem` takes a `VaultItem` directly (not the typed payload).
- `DecryptItem` contains `// DEFERRED(040): Payload decryption`.

### Decision
Extend `VaultService` / `VaultViewModel` with:
- `savePassword(id: UUID?, payload: PasswordPayload, labelIds: List<UUID>)`
- `saveCreditCard(id: UUID?, payload: CreditCardPayload, labelIds: List<UUID>)`
- `saveSecureNote(id: UUID?, payload: SecureNotePayload, labelIds: List<UUID>)`
- `decryptPassword(item: VaultItem): Outcome<PasswordPayload, DomainError>`
- `decryptCreditCard(item: VaultItem): Outcome<CreditCardPayload, DomainError>`
- `decryptSecureNote(item: VaultItem): Outcome<SecureNotePayload, DomainError>`

Or encapsulate this within a `VaultCryptoService` injected into `VaultViewModel` so that UI screens receive typed states (`PasswordPayload`, etc.) while persistence handles encrypted `VaultItem` through `aggregateService` and `database.vaultQueries`.

### Rationale
- Ensures `VaultViewModel` orchestrates decryption on demand and zeroes memory on navigation.
- Keeps `VaultRepositoryImpl` focused on persistence/projections and event sourcing.

---

## 4. Summary of Technical Choices

| Problem Area | Decision | Constitution / Best Practice Alignment |
|--------------|----------|-----------------------------------------|
| Payload Encryption | AES-256-GCM via `AesEncryptionManager` + derived key from `EventStoreKeyProvider` | Constitution §I, §II |
| Memory Sanitization | `clearMemory()` on all `*Payload` classes in `finally` | Constitution §I.5, §X.5 |
| Navigation | `VaultNavGraph` with Jetpack Compose `NavHost` in `feature:vault` | Clean Architecture §III, Modularization |
| Persistence | Existing Event Sourcing `AggregateService<VaultCommand, VaultState>` + `VaultDatabase` | Constitution §VIII |
| Clipboard | `ClipboardManagerService.clearClipboard()` at 60s timeout | Constitution §IV |

## 5. T055 Input/Rendering Feasibility Gate (2026-09-08)

### Current adapter audit (2026-09-09, Constitution 1.0.0)

The following supersedes the historical universal-erasure interpretation below. None of these controls establishes forensic erasure of platform copies.

| Boundary | Fields and necessary copy | Current controls and ownership | Remaining evidence |
|---|---|---|---|
| Material3 `OutlinedTextField` / resolved Foundation 1.11.1 | Every editor field; transient `displayText()` String required by the value-based widget | Typed mutable draft owns text. Callback copies directly into mutable storage and erases replaced arrays. No plaintext saved state. `VaultInputPolicy` wraps all three forms, requests no personalized learning, suggestions, autocorrection, fullscreen/extract UI or initial surrounding text, and sets `IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS` on the Android view root for the editor lifetime before restoring the prior setting. | API 35 instrumentation verifies IME flags, empty surrounding text and the autofill opt-out for password, card and note. Value-based widget undo/history and framework rendering snapshots are uncontrollable platform copies under the approved I.5 exception. Accessibility remains enabled for the user-selected active editor, as required by Constitution VI; screen readers may receive the live widget value. IMEs or services may ignore controls. |
| Compose `BasicText` / `AnnotatedString` | Revealed password/custom-field/PAN rendering | `LegibleSecretText` borrows CharArray; masked path constructs only bullets. Reveal appends chars directly to the required rendering representation. PAN grouping no longer creates an intermediate full PAN String. Masked PAN exposes only the deliberately visible last four characters. No app rendering cache is added. | Verify reveal/hide/disposal on runtime and release app references; framework text/layout caches remain residual exposure. Other detail/title text adapters remain in scope. |
| Android `ClipData.newPlainText` through `ClipboardManagerService` | Explicit password copy only | Copy intent owns CharArray; ViewModel consumes and erases it on success, exception, cancellation and never-started execution. A String is created only at the clipboard service call. Correct `android.content.extra.IS_SENSITIVE` extra suppresses previews where supported; existing 60-second clear remains. | Host tests prove transferred-buffer cleanup and exact extra. Runtime timeout/clipboard behavior remains T046/T062. OS and clipboard consumers may retain copies. |

Titles are sensitive application data and are not allowed a metadata classification exception. Their owner must use an erasable representation throughout the item, command, event and aggregate-state lifecycle.

Update (2026-09-09): `VaultItem`, `VaultCommand`, `VaultEvent` and `VaultState` now own `CharArray` titles. The decider copies ownership between command, event and state, then aggregate/repository paths clear retired owners. Vault-entry projections now persist UTF-8 title bytes in a SQLite `BLOB`, with migration 5 converting prior `TEXT` values. The custom title serializer preserves the frozen v1 JSON string representation for event and snapshot compatibility; its framework `String` exists only inside a single encode/decode call and is never stored in a domain or UI owner. Frozen legacy metadata fixtures continue to prove exact compatibility.

Additional exact-version source finding: Foundation 1.11.1's value-based `BasicTextField.kt` retains `TextFieldValue` at lines 778–805. `CoreTextField.kt` creates an internal `UndoManager` and snapshots values at lines 286–287; `UndoManager.kt` retains linked entries with up to 100,000 stored characters. The public value-based widget does not expose that manager. This is a separate retention path from IME learning. The state-based `TextFieldState.undoState.clearHistory()` API is available in the same version, but adopting it requires an audited adapter that avoids saved-state registration and synchronizes/clears the editor without transferring immutable platform storage into drafts. No undo-control completion is claimed for the current value-based fields.

Runtime update: `VaultInputPolicyInstrumentationTest` passes for password, card and note on the managed Android API 35 AOSP ATD device. The actual focused editor's `EditorInfo` contains all requested retention flags and no initial surrounding text after entering a synthetic sensitive title. The current run also verifies the lifecycle-bound Android autofill opt-out. Log: `.gradle/t039-device-input-autofill.log`. This verifies app-configurable boundary controls; undo/history, rendering snapshots, keyboards and accessibility services remain documented external-copy residual risks.

The audited version catalog declares Kotlin `2.3.21`, Android Gradle Plugin `9.4.0`, and Compose BOM `2026.05.00`. The Vault module has no dedicated secure-input dependency. All three entry screens use Material3 `OutlinedTextField`; main draft state now uses typed mutable field owners, while widget values still require String conversions; no `TextFieldState`, `SecureTextField`, or `TextFieldValue` usage was found. CharArray payloads protect application-owned copies, not IME, accessibility, autofill, snapshot, undo, saved-state, or rendering buffers.

All FR-VAULT-026 fields are sensitive: username, password, URI/website, cardholder name, card number, expiration date, CVV, notes, titles, and custom-field names and values. Drafts, decrypted payloads, operation copies, comparison baselines, navigation references, and clipboard handoffs require independent ownership and wiping; process recreation must not restore plaintext drafts.

The reproducible probe is to enter a unique marker such as `T055-SYNTHETIC-<random UUID>` into every classified field, capture heap/reference snapshots while editing, after replacement, successful save, discard, navigation/disposal, failed save, recreation, and clipboard copy, then search reachable framework/editor objects for the marker. A pass requires no marker outside an authorized active owner and documented erasure of each mutable owner.

No compliant input/rendering component has been established, so this feasibility gate is **UNMET**. T039 and dependent UI acceptance remain open. Independent codec/crypto work can proceed under plan Phase 6; it is not a substitute for this gate.

### Resolved-version evidence and implementation (2026-09-08)

- Gradle `:feature:vault:dependencyInsight --dependency androidx.compose.foundation:foundation --configuration debugRuntimeClasspath` resolved `androidx.compose.foundation:foundation-android:1.11.1`. The BOM declaration alone was not used as the resolved version.
- Audited [the exact 1.11.1 source archive](https://dl.google.com/dl/android/maven2/androidx/compose/foundation/foundation-android/1.11.1/foundation-android-1.11.1-sources.jar), SHA-256 `D54EAC267D963B67F75CA60D834D3AC68A7FF720D3DB120409AA6A8B31B2C59E`. In `commonMain/androidx/compose/foundation/text/input/TextFieldState.kt`, lines 349/378 create immutable text through `mainBuffer.toString()`, line 674 uses `rememberSaveable`, and line 741 deletes visible text. `TextFieldBuffer.kt` lines 384/464 also materialize Strings.
- `ComposeSecretRetentionTest.resolvedComposeTextFieldStateRetainsImmutableSnapshotAfterClear` runs against the resolved dependency: append a synthetic mutable input, retain the framework's returned text snapshot, wipe the input, clear the field, then observe that the old snapshot is a String with unchanged contents. A passing characterization test demonstrates the limitation; it is **not** a passing erasure acceptance test or a whole-device heap measurement.
- API 35 now verifies actual IME and autofill controls, and the all-type real navigation suite verifies application-owned lifecycle cleanup. Moving to state-based fields or a secure-field wrapper does not eliminate the demonstrated immutable platform storage, so the smallest compliant adapter remains the value-based field with controls and documented residual risk.
- All internal payload properties now own mutable arrays, including titles, URI, expiration, and custom-field names. Deep copies include every array and diagnostics are redacted. UI/display, rendering and clipboard conversions are constrained platform boundaries; `VaultItem`, aggregate and database title owners are mutable. Application-owned main draft state uses erasable arrays through `MutableDraftField` and typed draft holders; this does not erase widget-owned String copies or relax field classification.
- `VaultPayloadCodec` implements the existing narrow JSON schema with mutable character/byte storage in both directions. Kotlin serialization String APIs cannot meet the representation requirement; changing DTO types or selecting stream APIs alone cannot prove erasure of library-owned Strings/buffers. The codec uses no new dependency or cryptographic primitive. Allocations are registered before processing and cleared in `finally`; only completed decoded payload arrays transfer. Duplicate/unknown fields and partial outputs remain registered for cleanup. Parser errors never carry input excerpts.
- Writer storage is bounded by supplied field sizes and worst-case JSON escape/UTF-8 expansion, with checked addressable-size arithmetic. Unknown nested JSON uses an iterative grammar bounded by input length, so compatibility does not impose a new nesting-depth cap or recursive stack risk. Scratch is not pooled. Actual AES, envelope, and v1 key domain are unchanged.
- Fixed old-codec fixtures were captured with the real AES implementation before replacement. Their provenance and synthetic key are in `feature/vault/src/test/resources/vault-legacy-v1/README.md`. No event-store/passkey format or database schema was changed by this implementation.


### Additional platform-copy boundary evidence

The installed Android SDK source `sources/android-36.1/android/text/TextUtils.java`, `writeToParcel` (lines 804-847), invokes `cs.toString()` before `Parcel.writeString8` for CharSequence transport. This is additional source evidence that merely passing a mutable CharSequence to a platform text/clipboard boundary does not establish mutable-only transport. It is SDK-source evidence, not a measurement of a running device. A component decision must address such platform boundaries explicitly while preserving required input/copy behavior; current application-array cleanup does not prove their erasure.
