# Data Model: Manage Saved Passkeys

## Entities

### PasskeyCredential
Represents a stored FIDO2 credential.

| Field | Type | Description |
|-------|------|-------------|
| `id` | `String` | Primary Key (UUID). |
| `rpId` | `String` | Domain of the Relying Party. |
| `rpName` | `String` | Human-readable name of the RP. |
| `userId` | `String` | Unique user identifier (binary/hex). |
| `userName` | `String` | User handle (e.g., email). |
| `userDisplayName` | `String` | Display name for the user. |
| `createdAt` | `Long` | Timestamp of registration. |
| `lastUsedAt` | `Long?` | Timestamp of last assertion. |
| `signCount` | `Long` | Counter for FIDO2 operations. |
| `iconUrl` | `String?` | URL for the RP icon (if available). |

## State Management (MVI)

### CredentialManagementState
| Field | Type | Description |
|-------|------|-------------|
| `credentials` | `List<PasskeyCredential>` | Full list of credentials from DB. |
| `filteredCredentials` | `List<PasskeyCredential>` | List matching the search query. |
| `searchQuery` | `String` | Current search filter text. |
| `isLoading` | `Boolean` | Loading state indicator. |
| `lastDeleted` | `PasskeyCredential?` | Temporary storage for Undo. |
| `error` | `String?` | Error message. |

## Relationships
- One **RelyingParty** can have many **PasskeyCredentials**.
- One **PasskeyCredential** can have multiple **UserConsentRecords** (audit log).
