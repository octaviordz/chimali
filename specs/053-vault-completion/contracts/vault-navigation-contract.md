# Contract: Vault Navigation & UI Flow

**Component**: `feature:vault`  
**Date**: 2026-09-07  
**Status**: Approved

## 1. Public Feature Entry Point

```kotlin
package com.chimali.feature.vault.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Main Vault feature composable managing its own internal navigation graph.
 *
 * Hosted by AppNavGraph within the main application shell.
 *
 * @param onOpenSettings Invoked when the user selects settings from the vault top bar.
 * @param modifier Compose modifier.
 */
@Composable
fun VaultNavGraph(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
)
```

## 2. Internal Screen Route Definitions

| Route | Composable | Purpose |
|---|---|---|
| `vault/list` | `VaultListScreen` | Primary list displaying items and label filter tabs. |
| `vault/entry/password` | `PasswordEntryScreen` | Add new Password item form. |
| `vault/entry/card` | `CreditCardEntryScreen` | Add new Credit Card item form. |
| `vault/entry/note` | `SecureNoteEntryScreen` | Add new Secure Note item form. |
| `vault/detail/password/{id}` | `PasswordDetailScreen` | View/copy decrypted password details. |
| `vault/detail/card/{id}` | `CreditCardDetailScreen` | View/copy decrypted card details. |
| `vault/detail/note/{id}` | `SecureNoteDetailScreen` | View decrypted secure note details. |
| `vault/labels` | `LabelManagerScreen` | Create and delete organization labels. |

## 3. UI Action Contracts

### 3.1 VaultListScreen
- `onItemClick: (VaultItem) -> Unit` -> Navigates to corresponding `vault/detail/{type}/{id}`
- `onAddClick: () -> Unit` -> Opens Add Type selection modal/sheet, routing to `vault/entry/{type}`
- `onLabelFilterClick: (UUID?) -> Unit` -> Dispatches `VaultIntent.LoadItems(labelId)`
- `onManageLabelsClick: () -> Unit` -> Navigates to `vault/labels`
- `onOpenSettings: () -> Unit` -> Delegates to parent shell `onOpenSettings`

### 3.2 Entry Screens (Password, CreditCard, SecureNote)
- `onSave: (Payload) -> Unit` -> Dispatches save intent, encrypts payload, returns to `vault/list`
- `onCancel: () -> Unit` -> Pops back stack to `vault/list`

### 3.3 Detail Screens (Password, CreditCard, SecureNote)
- `onEdit: () -> Unit` -> Opens edit flow or modifies entry
- `onDelete: () -> Unit` -> Dispatches delete intent, pops back stack to `vault/list`
- `onBack: () -> Unit` -> Clears payload memory, pops back stack to `vault/list`
