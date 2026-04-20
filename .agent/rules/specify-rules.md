# Chimali Development Guidelines

Auto-generated from all feature plans. Last updated: 2026-04-20

## Active Technologies
- [e.g., Python 3.11, Swift 5.9, Rust 1.75 or NEEDS CLARIFICATION] + [e.g., FastAPI, UIKit, LLVM or NEEDS CLARIFICATION] (001-store-credentials)
- [if applicable, e.g., PostgreSQL, CoreData, files or N/A] (001-store-credentials)
- Kotlin 1.9.20+, Rust 1.75+ (for CRDT) + Jetpack Compose, Hilt, SQLDelight, SQLCipher, Android Keystore, Loro.dev (Rust), UniFFI (Rust-Kotlin Bridge) (001-store-credentials)
- Encrypted SQLite (SQLCipher) (001-store-credentials)
- Kotlin 1.9+, Jetpack Compose + Google Fonts (for Atkinson Hyperlegible), Material 3 (002-password-legibility)
- N/A (UI layer transformation) (002-password-legibility)
- Kotlin 1.9+ (Android Native) + AndroidX BiometricPrompt, Android KeyStore, BluetoothHidDevice, SQLCipher, SQLDelight, Hilt, Jetpack Compose, Bouncy Castle (PQC), ML-KEM/Kyber library (004-fido2-hid)
- SQLCipher + SQLDelight for encrypted credential metadata, Android KeyStore for private keys (004-fido2-hid)
- Kotlin 1.9+ (Android Native) + AndroidX, Jetpack Compose, Timber (for structured local logging) (004-fido2-hid)
- Local App Data directory for crash logs (custom rotating file sink via Timber tree, NEVER shipped to cloud). (004-fido2-hid)
- Kotlin + BouncyCastle 1.80 (004-fido2-hid)
- EncryptedSharedPreferences (for Master Seed) (004-fido2-hid)
- Kotlin 2.1+ + Kermit 2.x, Okio, Kotlinx-Datetime (005-kermit-logging-migration)
- Local file system (Okio `FileSystem.SYSTEM`) (005-kermit-logging-migration)
- Kotlin 2.1+ + `co.touchlab:kermit:2.x`, `com.squareup.okio:okio`, `org.jetbrains.kotlinx:kotlinx-datetime` (006-kmp-logging-writer)

- Kotlin 1.9.20+ + Jetpack Compose, Hilt, SQLDelight, SQLCipher, Android Keystore (001-store-credentials)

## Project Structure

```text
src/
tests/
```

## Commands

# Add commands for Kotlin 1.9.20+

## Code Style

Kotlin 1.9.20+: Follow standard conventions

## Recent Changes
- 006-kmp-logging-writer: Added Kotlin 2.1+ + `co.touchlab:kermit:2.x`, `com.squareup.okio:okio`, `org.jetbrains.kotlinx:kotlinx-datetime`
- 005-kermit-logging-migration: Added Kotlin 2.1+ + Kermit 2.x, Okio, Kotlinx-Datetime


<!-- MANUAL ADDITIONS START -->
<!-- MANUAL ADDITIONS END -->
