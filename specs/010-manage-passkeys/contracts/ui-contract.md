# UI Contract: Manage Saved Passkeys

## Screen: CredentialListScreen

### Exposed Intents (Inputs)
| Intent | Payload | Description |
|--------|---------|-------------|
| `UpdateSearchQuery` | `query: String` | Filters the list by RP name or Username. |
| `InitiateDelete` | `id: String` | Starts deletion flow (triggers Biometric Auth). |
| `ConfirmDelete` | `id: String` | Finalizes deletion after Auth success. |
| `UndoDelete` | - | Restores the `lastDeleted` credential. |
| `SelectCredential` | `id: String` | Shows details modal. |

### Visual Components
1. **Search Bar**: Sticky top bar with clear icon.
2. **Passkey List**:
    - Item: RP Icon, RP Name (Primary), Username (Secondary), Creation Date.
    - Action: Swipe-to-delete or explicit Delete icon.
3. **Empty State**: Illustration and "No passkeys found" text.
4. **Biometric Prompt**: System-standard overlay.
5. **Undo Snackbar**: Material 3 Snackbar with "Undo" action.

### Navigation
- **Route**: `manage_passkeys`
- **Arguments**: None.
- **Parent**: Settings or Side Drawer.
