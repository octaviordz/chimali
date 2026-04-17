# Contracts: Error & Logging Context

## 1. Error Mapping Contract (CTAP2 -> UI)
Any FIDO2 protocol failure MUST be mapped to a specific `ErrorUi` presentation state that prevents the app from crashing and guides the user. The contract requires:
- `Fido2Exception.BluetoothException` -> Suggest moving closer, `isRetryable = true`
- `Fido2Exception.KeyStoreFull` -> Suggest removing unused credentials, `isRetryable = false`
- `Fido2Exception.UserVerificationFailed` -> Inform biometric failure, `isRetryable = true`

## 2. Privacy-Safe Logging Contract
Any structured local sink (e.g., rotating file appender via Kermit) MUST implement a scrubbing middleware that intercepts the log string and replaces values matching these keys with `[REDACTED]`:
- Mnemonic arrays (`CharArray`, `ByteArray`)
- Private scalar keys
- Password/PIN values
- Biometric template identifiers

No direct exceptions should be piped to standard `Log.e` if they contain nested payloads from `BiometricPrompt` that could leak auth states.
