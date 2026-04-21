# Quickstart: Manage Saved Passkeys

## Development Setup
1. Ensure `Fido2Database` is initialized.
2. Navigate to `Settings` -> `Manage Passkeys`.

## Manual Verification Steps
1. **Search**:
    - Add 2 passkeys (e.g., "google.com" and "github.com").
    - Search for "goo". Verify only "google.com" is shown.
2. **Deletion**:
    - Click "Delete" icon on a passkey.
    - Verify Biometric Prompt appears.
    - Fail authentication. Verify passkey is NOT deleted.
    - Succeed authentication. Verify passkey disappears from list.
3. **Undo**:
    - Delete a passkey successfully.
    - Click "Undo" on the snackbar.
    - Verify passkey reappears in the list.
4. **Empty State**:
    - Delete all passkeys.
    - Verify "No passkeys found" message is displayed.

## Key Files
- UI: `CredentialListScreen.kt`
- Logic: `CredentialManagementViewModel.kt`
- Data: `PasskeyCredentialRepository.kt`
