# Detekt MaxLineLength Enforcement (120 Characters)
Date: 2026-04-24

## Overview
As part of our ongoing commitment to code quality and consistency across the Kotlin Multiplatform (KMP) project, we have strictly enforced a global `MaxLineLength` of 120 characters in Detekt.

## Key Changes
- **Global Enforcement**: Removed previous global exclusions for `MaxLineLength`. The 120-character limit now strictly applies across all modules (`app`, `core`, `feature`), impacting both production code and test suites.
- **Generated Code Ignored**: Added specific exclusions `['**/build/generated/**']` in `detekt.yml` to prevent failures on machine-generated code.
- **String Concatenation Preferred**: Established a standard for handling long strings. Simple string concatenation (`+`) is preferred for non-multiline string data to optimize compile-time merging, while `trimIndent()` is restricted to strings that actually require multiline structural formatting (like hex dumps or cryptographic keys).
- **Comment Preservation**: Strict rules have been set to wrap rather than remove or truncate code documentation, guaranteeing no loss of context while adhering to length constraints.
- **Test Suite Refactoring**: Remediated all `MaxLineLength` violations across test suites, particularly in `fido2`, `security`, and `vault`. Handled extensive mock block wrapping (`coEvery`) and long assert statements.
- **Production Code Refactoring**: Resolved isolated `MaxLineLength` violations in production code (e.g. `VaultListScreen.kt`, `VaultRepositoryImpl.kt`, `BluetoothHidAuthenticatorImpl.kt`).
- **Core UI Polish**: Renamed `Typography.kt` to `LegibilityType.kt` to comply with naming conventions without requiring suppressions, ensuring architectural components follow strict declaration-matching rules.

## Impact
- Unified formatting rules mean less friction during code reviews.
- Ensures a standard 120-column readability margin, catering to standard split-screen development layouts.
- Pre-commit local CI validation is significantly hardened.
