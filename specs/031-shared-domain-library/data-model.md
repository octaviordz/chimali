# Data Model: Shared Domain Library

## Value Objects (Identifiers)

All identifiers are implemented as Kotlin `@JvmInline value class` and implement `@Serializable` from `kotlinx-serialization`.

### `CredentialId`
- **Fields**: `val bytes: ByteArray` (Custom serializer needed for Base64 URL encoding)
- **Functions**: `companion object { fun generate(): CredentialId }`
- **Validation**: Enforces 32-byte size for newly generated IDs (optional verification during init).

### `RpId`
- **Fields**: `val value: String`
- **Validation**: Cannot be blank. Represents Relying Party ID.

### `UserId`
- **Fields**: `val value: String`
- **Validation**: Cannot be blank. Represents user identifier.

### `PasskeyId`
- **Fields**: `val value: String`
- **Validation**: Cannot be blank. Identifier for passkey records in the vault.

## Domain Entities

All domain models are immutable data classes, KMP-compatible, and serializable.

### `RelyingParty`
- **Fields**:
  - `val id: RpId`
  - `val name: String`
  - `val icon: String?` (Replaces `java.net.URI` with raw string URL representation)
- **Validation**: `id` and `name` must not be blank.

### `CredentialSummary`
- **Fields**:
  - `val id: CredentialId`
  - `val rpId: RpId`
  - `val userName: String`
  - `val lastUsed: kotlinx.datetime.Instant?`
- **Purpose**: Lightweight projection for UI lists.

### `UserConsentRecord`
- **Fields**:
  - `val id: String` (or UUID value class)
  - `val rpId: RpId`
  - `val timestamp: kotlinx.datetime.Instant`
  - `val granted: Boolean`
