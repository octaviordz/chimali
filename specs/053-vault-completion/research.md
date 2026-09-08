# Research: Vault Feature Completion

**Feature**: `053-vault-completion`  
**Date**: 2026-09-07  
**Status**: Completed

## 1. Payload Serialization & Cryptographic Architecture

### Context
`PasswordPayload`, `CreditCardPayload`, and `SecureNotePayload` exist in `feature/vault/src/main/java/com/chimali/feature/vault/internal/payload/` holding sensitive fields in `CharArray` for zero-memory leakage. However, storing them in `VaultEntry` requires encrypting into a binary `payload: ByteArray`.

### Decision
Use a lightweight, deterministic serialization format (`kotlinx.serialization` or canonical binary/UTF-8 JSON) for in-memory payload representations, encrypted using `EncryptionManager` (AES-256-GCM via Android Keystore / Master Seed derived key) through `EventStoreKeyProvider.getEventStoreKey("chimali_vault_payload_v1")` or dedicated `VaultCryptoService`.

### Rationale
- **Constitution Principle I (Security First)**: AES-256-GCM is mandatory for general payload encryption with hardware keystore offloading and unique IVs per operation.
- **Constitution Principle II & X.5 (Master Seed Architecture & Memory Security)**: Key material derived via HKDF/HDK from master seed. Plaintext in memory must be held in `CharArray`/`ByteArray` and explicitly zeroed (`fill('0')`) in `finally` blocks immediately after serialization/encryption.
- `AesEncryptionManager` already implements `EncryptionManager` (`encrypt(plaintext, key)` and `decrypt(ciphertext, key)` with 12-byte random IV prepended and 128-bit GCM auth tag).

### Alternatives Considered
- *Protobuf serialization*: Adds compilation overhead for 3 simple payload models and complicates mutable CharArray handling.
- *Raw string serialization*: Leaks plaintext into immutable JVM `String` pool.

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
