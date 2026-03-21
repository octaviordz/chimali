# Changelog - 2026-03-20

## Logging Refactor and Maintenance

### Overview
This update focused on standardizing the logging infrastructure and performing general codebase maintenance to resolve IDE warnings and improve readability.

### [feature:fido2] Refactored Logging
- **Standardized Timber Usage**: Removed redundant `private const val TAG` declarations across the module.
- **Automatic Class Tagging**: Replaced explicit `Timber.tag(TAG).d(...)` calls with direct `Timber.d(...)` calls. The `DebugTree` now automatically resolves the calling class name as the tag, reducing boilerplate and ensuring consistency.
- **Improved Log Formatting**: Updated several log statements to use proper Timber string formatting (e.g., `Timber.d("msg %s", arg)`) instead of string templates for better performance and consistency.

### Maintenance & Fixes
- **Package Verification**: Investigated a "Package directive does not match file location" warning for `BluetoothHidDeviceWrapper.kt`. Confirmed that the package `com.chimali.fido2.bluetooth` matches the directory structure exactly. The warning was identified as an IDE/LSP false positive.
- **Spelling Correction**: Fixed a typo in `BluetoothHidDeviceWrapper.kt` ("initialising" -> "initializing").
- **Code Formatting**: Applied consistent indenting and line breaks to better align with the project's styling guidelines, particularly around long Timber log statements and Bluetooth callback overrides.
- **Cleanup**: Commented out unused constants (`KEY_CREDENTIAL`, `KEY_AUTH_DATA`, etc.) in `Ctap2GetAssertionHandler.kt` to reduce compiler warnings while preserving them for future reference during spec implementation.
