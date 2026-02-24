# Feature: Secure Credentials Vault (FR1)

## Overview
The Secure Credentials Vault provides a secure, local-first storage for sensitive information including passwords, credit cards, and secure notes. It uses a hybrid storage model combining SQLite for indexing and Loro.dev CRDTs for individual item history and synchronization.

## Architecture
- **MVI (Model-View-Intent)**: Unidirectional Data Flow using `VaultViewModel`, `VaultState`, and `VaultIntent`.
- **Hybrid Granularity**: 
  - **Relational (SQLite/SQLCipher)**: Stores top-level metadata and encrypted search indexes.
  - **Document (Loro.dev)**: Each item has its own CRDT document for versioning and merging.
- **Security**:
  - **AES-256-GCM**: Encryption for all sensitive fields.
  - **Memory Safety**: Uses `CharArray` and `ByteArray` for sensitive data with explicit zeroing (`clearMemory()`).
  - **Local-Only**: No plain-text data ever leaves the device.

## Components
- `api/`: Contracts and MVI models (`VaultService`, `VaultState`).
- `internal/`: Business logic, repository, and payload parsers.
  - `payload/`: Entities for specific credential types (`PasswordPayload`, etc.).
- `ui/`: Compose-based screens for listing, adding, and viewing credentials.

## Security Policies
- Avoid `String` for sensitive data whenever possible.
- Use `payload.clearMemory()` after sensitive operations (e.g., after save or when the item is no longer needed in memory).
- Clipboard contents are cleared automatically via `ClipboardManagerWrapper`.

## Development
- **Tests**: 
  - `test/`: Unit tests for crypto and payload logic.
  - `androidTest/`: Integration tests for database and repository.
