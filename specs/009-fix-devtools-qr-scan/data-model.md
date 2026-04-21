# Data Model: Dev Tools QR Scanning

## Entities

### DevToolsUiState
The primary state container for the Development Tools screen.

| Field | Type | Description |
|-------|------|-------------|
| `mnemonicWords` | `List<String>?` | The current 24-word recovery phrase (null if hidden/not loaded). |
| `isMnemonicVisible` | `Boolean` | Whether the word grid is currently displayed. |
| `isLoading` | `Boolean` | Global loading state for biometric or seed operations. |
| `error` | `String?` | Error message to display (e.g., "Invalid Mnemonic"). |
| `recoverSuccess` | `Boolean` | Transient flag indicating a successful import. |

### RecoveryPhrase
A domain representation of the BIP39 mnemonic.

- **Format**: 24 space-separated words.
- **Validation**: Must match BIP39 word list and checksum.
- **Security**: Must be handled as a `CharArray` when passing to persistence layers to facilitate memory zeroing.

## State Transitions

1. **Initial**: `showScanner = false`, `mnemonicWords = null`.
2. **Scan Request**: User clicks button -> Permission Check -> `showScanner = true`.
3. **Scanning**: Camera active, frame analysis running.
4. **Validation Error**: ML Kit detects non-BIP39 data -> `onError` (Snackbar) -> Scanner remains active.
5. **Success**: ML Kit detects 24 words -> `onScanned` -> `showScanner = false`, `recoverFromSeed` intent sent.
6. **Persistence**: ViewModel calls `MasterSeedProvider` -> State updated -> Success snackbar shown.
