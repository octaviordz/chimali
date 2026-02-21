# Vault Service Contract

## Overview
This document defines the generic interface boundaries and data structures for the local-only `VaultService`. This core service handles the actual cryptographic operations, CRDT document management, and SQLite persistence. It serves as the bridge between the UI-layer ViewModels and the underlying Rust/JNI backend.

## Data Structures

### VaultItem
The unified data object transferred across the boundary.

| Property | Type | Description |
|---|---|---|
| `id` | `UUID` | Unique identifier for the item. |
| `type` | `VaultType` | Enum categorization: `PASSWORD`, `CREDIT_CARD`, `NOTE`. |
| `title` | `String` | Descriptively indexed title. |
| `payload` | `ByteArray` | The flat, currently active AES-256-GCM encrypted content snapshot. |
| `crdt_state` | `ByteArray` | The fully tracked, encrypted CRDT document state from Loro.dev. |
| `date_created` | `String` | ISO-8601 format timestamp of creation (UTC). |
| `date_modified` | `String` | ISO-8601 format timestamp of the last local or merged modification (UTC). |
| `last_backed_up_at` | `String?` | Optional ISO-8601 format timestamp of the last successful backup (UTC). |
| `identity_id` | `UUID` | The owner identity key used for this specific vault entry. |

## Operations

### `VaultService(db_path: String, encryption_key: ByteArray)`
- **Description**: Initializes the service with the database required and the symmetric master key derived via HDK-ECDH-P256.

### `getItems(label_id: UUID?): List<VaultItem>`
- **Description**: Retrieves a list of vault items. If `label_id` is provided, the list is filtered. The implementation handles fast SQLite queries without fully decrypting the inner payloads.

### `saveItem(item: VaultItem)`
- **Description**: Inserts or updates an item in the vault. 
- **Underlying Logic**: The service is responsible for advancing the CRDT merge state, packaging the current UI snapshot into `payload`, encrypting both using `encryption_key`, and persisting to SQLite.

### `deleteItem(id: UUID)`
- **Description**: Hard-removes the specified item completely from the database and any associated historical records.
