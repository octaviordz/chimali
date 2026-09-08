# Quickstart: Vault Feature Validation Guide

**Feature**: `053-vault-completion`  
**Date**: 2026-09-07  
**Status**: Ready for Verification

## 1. Prerequisites

1. App builds and compiles successfully:
   ```bash
   ./gradlew :feature:vault:assembleDebug
   ```
2. Unit tests pass:
   ```bash
   ./gradlew :feature:vault:testDebugUnitTest
   ```

---

## 2. End-to-End Validation Scenarios

### Scenario 1: Password Creation and Decryption Flow
1. Launch Chimali app and ensure "Vault" is enabled in Onboarding or Settings.
2. Navigate to the **Vault** tab via bottom bar.
3. Click the Floating Action Button (`+`).
4. Select **Password** from the item type selection.
5. Fill in:
   - **Title**: `Work Email`
   - **Username**: `alice@corp.com`
   - **Password**: `P@ssw0rd!123`
   - **Website**: `https://mail.corp.com`
6. Click **Save**.
7. **Verification**:
   - The user returns to `VaultListScreen`.
   - `Work Email` is listed under "All" items.
8. Tap on `Work Email`.
9. **Verification**:
   - Opens `PasswordDetailScreen`.
   - Website `https://mail.corp.com` and Username `alice@corp.com` are displayed.
   - Password is masked by default with bullets (`••••••••`).
   - Tapping the visibility toggle renders `P@ssw0rd!123` in Atkinson Hyperlegible font.
10. Click **Copy** next to password.
    - Status message shows "Copied to clipboard".
    - After 60 seconds, clipboard is cleared.

### Scenario 2: Credit Card and Note Storage Flow
1. From `VaultListScreen`, tap `+` -> **Credit Card**.
2. Enter Cardholder Name, Number, Expiration (`12/28`), CVV (`456`), and Title `Personal Visa`.
3. Save and confirm entry in list.
4. Tap entry to verify details view.
5. Repeat for **Secure Note** (`Server Recovery Key`, body `xyz-123-recovery`).

### Scenario 3: Item Deletion Flow
1. Open `Work Email` detail screen.
2. Tap the **Delete** icon.
3. Confirm deletion dialog.
4. User returns to `VaultListScreen`.
5. Entry `Work Email` is no longer present.

---

## 3. Automated Verification Commands

```powershell
# Run Vault unit and crypto tests
./gradlew :feature:vault:testDebugUnitTest

# Run Detekt and Ktlint quality enforcement
./gradlew detekt ktlintCheck
```
