# Data Model: Secure Credentials Vault (FR-VAULT-010)

## Overview
This proposal uses a **Hybrid Granularity** approach:
1.  **SQLite (Relational)**: Manages high-level organization, search indexing (encrypted titles), and fast list rendering.
2.  **Loro.dev (CRDT-per-Item)**: Each `VaultEntry` stores its own independent `LoroDoc`. This document manages the history and fields (username, password, etc.) for that specific item.

**Rationale**:
- **Efficiency**: Only the active item's history is loaded/decrypted into memory.
- **Isolation**: A merge conflict or corruption in one note cannot affect the rest of the vault.
- **Granular Sync**: We can track and sync individual items via `last_backed_up_at` rather than re-uploading the entire vault for a single edit.
- **Redundancy**: Stores both `encrypted_payload` (current state snapshot) and `crdt_state` (full history/merge state) to ensure data recovery even if the CRDT engine fails or is bypassed.

## Entities

### VaultEntry (Table)
| Field | Type | Description |
|-------|------|-------------|
| id | UUID | Primary Key |
| doc_id | String | CRDT Document ID (Loro) |
| type | Enum | PASSWORD, CREDIT_CARD, NOTE |
| title | String | Searchable title (stored in encrypted column) |
| encrypted_payload | BLOB | AES-256-GCM encrypted snapshot of current properties (Redundancy) |
| crdt_state | BLOB | Loro document snapshot including version history (encrypted) |
| date_created | String | ISO-8601 Timestamp of creation (UTC) |
| date_modified | String | ISO-8601 Timestamp of last local or merged modification (UTC) |
| last_backed_up_at | String? | Optional ISO-8601 Timestamp of the last successful backup (UTC) |
| identity_id | UUID | Foreign key to Identity (Owner of this entry) |

### VaultEntryLabel (Join Table)
| Field | Type | Description |
|-------|------|-------------|
| entry_id | UUID | Foreign key to VaultEntry |
| label_id | UUID | Foreign key to Label |

### Identity (Table)
| Field | Type | Description |
|-------|------|-------------|
| id | UUID | Primary Key |
| alias | String | Friendly name for this identity (e.g., "Personal", "Work") |

### IdentityBackup (Table)
| Field | Type | Description |
|-------|------|-------------|
| id | UUID | Primary Key |
| identity_id | UUID | Foreign key to Identity |
| last_backed_up_at | String | ISO-8601 Timestamp of the last successful backup (UTC) |
| backup_method | Enum | MNEMONIC, SSS_SHARDS |
| configuration_json | String | Method-specific config (e.g., shard count, threshold, cloud provider) |


### Label (Table)
| Field | Type | Description |
|-------|------|-------------|
| id | UUID | Primary Key |
| name | String | User-defined category name |
| color_hex | String | UI display color |

## Sub-Type Payloads (Encrypted)

### CustomField
- name: `String`
- value: `CharArray`
- is_concealed: `Boolean` (e.g., true for security question answers, false for regular text)

### PasswordPayload
- username: `CharArray`
- password: `CharArray`
- uri: `String`
- notes: `CharArray?`
- custom_fields: `List<CustomField>?`

### CreditCardPayload
- cardholder_name: `CharArray`
- card_number: `CharArray`
- expiration_date: `String` (MM/YY)
- cvv: `CharArray`
- notes: `CharArray?`
- custom_fields: `List<CustomField>?`

### SecureNotePayload
- content: `CharArray`
- custom_fields: `List<CustomField>?`

## State Transitions (MVI)

### VaultState
- items: `List<VaultItem>`
- isLoading: `Boolean`
- errorMessage: `String?`
- selectedItem: `VaultItem?`

### VaultIntent
- LoadItems (filter: Label?)
- SaveItem (item: NewItem)
- DeleteItem (id: UUID)
- DecryptItem (id: UUID)
- ClearClipboard
