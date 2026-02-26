# Changelog: Secure Credentials Vault Implementation (FR-VAULT-010)

- **Date**: 2026-02-23
- **Feature**: Secure Credentials Vault
- **Status**: Completed

## Overview
Implemented the first version of the Secure Credentials Vault (FR-VAULT-010), providing a local-first, hardware-encrypted storage system for passwords, credit cards, and secure notes. The system uses a hybrid storage architecture for both efficiency and synchronization capability.

## Technical Details

### 1. Hybrid Storage Engine
- **SQLCipher**: Used for storing relational metadata, vault entry headers, and encrypted search indexes.
- **Loro.dev CRDT**: Each vault item is represented as a Loro document (encoded as bytes in SQLite), enabling granular version history and high-quality merging for future multi-device synchronization.

### 2. Hardware-Backed Security
- **Android Keystore**: Integrated for generating and storing the Master Seed encryption keys.
- **AES-256-GCM**: Standard encryption for all vault payloads (Password, Cards, Notes).
- **Memory Safety**: Strict use of `CharArray` in payload entities with explicit zeroing (`fill('0')`) on disposal to prevent plain-text leakage in heap dumps.

### 3. User Stories Delivered
- **[US1] Passwords**: Core password management with title, username, password, URI, and notes.
- **[US2] Credit Cards**: Specialized card storage (number, cardholder, expiration, CVV).
- **[US3] Secure Notes**: Freeform encrypted notes.
- **[US4] Labels & Filtering**: System for organizing vault items into labels and filtering the vault list by category.
- **Dynamic Custom Fields**: Support for arbitrary key-value pairs (concealed or plain) on any vault item.

### 4. Cross-Cutting Concerns
- **Clipboard Management**: Secure wrapper to clear sensitive data from the system clipboard after use.
- **MVI Architecture**: Unified UI state management for consistent vault behavior.

## Files Created/Modified
- `feature/vault/*`: Core feature implementation.
- `core/database/src/main/sqldelight/.../Vault.sq`: Database schema.
- `core/crdt/*`: Rust bridge for Loro integration.
