# Data Model: Passkey Management

## Entities

### PasskeyCredential

Represents a saved FIDO2 credential on the device.

| Field | Type | Description | Constraints |
|-------|------|-------------|-------------|
| `id` | String | Unique identifier (Credential ID) | PK, Non-null |
| `rpId` | String | Relying Party Identifier (e.g. google.com) | Non-null |
| `userName` | String | User's account name | Non-null |
| `lastUsedAt` | Long | Unix timestamp of last use | Non-null |
| `createdAt` | Long | Unix timestamp of registration | Non-null |
| `label` | String? | [DEPRECATED] Optional user-defined label | Read-only in UI |

## UI State Transitions

| Initial State | Event | Resulting State |
|---------------|-------|-----------------|
| `credentials` | `PendingDelete(id)` | `credentials` (filtered) + `pendingDeleteIds` (+id) |
| `pendingDeleteIds` | `UndoDelete(id)` | `pendingDeleteIds` (-id) |
| `pendingDeleteIds` | `CommitDelete(id)` | Database Deletion + `pendingDeleteIds` (-id) |
