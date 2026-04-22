# Quickstart: Passkey UI Enhancements

## Developer Setup

1. **Environment**: Ensure Android Studio Iguana+ and Gradle 8.2+.
2. **Branch**: `git checkout 011-passkey-ui-enhancements`.
3. **Database**: Use `tools/setup-test-data.ps1` (if available) to populate the FIDO2 database with mock passkeys.

## Manual Verification Flow

### 1. Swipe-to-Delete
- Navigate to **Authenticator** -> **Manage Passkeys**.
- Select a passkey and swipe it to the left or right.
- **Expected**: The item disappears instantly. A snackbar appears with the text "Credential deleted" and an "UNDO" button.
- Wait 10 seconds.
- **Expected**: Snackbar disappears, item is permanently removed from the database.

### 2. Undo Deletion
- Perform swipe-to-delete again.
- Tap **UNDO** on the snackbar.
- **Expected**: The item reappears in its original position (sorted by `lastUsedAt`).

### 3. Simplified Details
- Tap on any passkey in the list.
- **Expected**: The details modal opens.
- **Verify**: Only RP ID, User Name, and technical metadata are visible. The "Custom Label / Note" input field is GONE.

## Automated Testing
Run the following command to verify the UI changes:
```powershell
./gradlew :feature:fido2:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.chimali.fido2.presentation.management.CredentialListScreenTest
```
