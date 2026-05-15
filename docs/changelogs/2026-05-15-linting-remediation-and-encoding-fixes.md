# Linting Baseline Audit Remediation & Encoding Fixes

This changelog summarizes the finalization of the linting baseline audit and the remediation of encoding issues across the codebase.

## [Unreleased] - 2026-05-15

### Fixed
- **Encoding Issues & UI Polish**: Resolved a widespread encoding corruption where the replacement character (U+FFFD) appeared in several files.
    - Fixed corrupted comments in `PairedDevicesSection.kt` related to swipe-to-dismiss logic.
    - Restored `iosMain/README.md` headers across 10 modules that were corrupted by improper encoding.
    - Fixed a spelling corruption (`actual` vs `ctual`) in `core/security/src/iosMain/README.md`.
- **Bluetooth HID API Modernization (US4)**: Addressed deprecated API usage and hardened cross-version compatibility.
    - Implemented a `getParcelableExtraCompat` helper for Intent data extraction (API 33).
    - Added SDK-gated checks for Bluetooth HID profile support (API 28+) and device property access (API 31+).

### Changed
- **Linting Baseline Remediation (US1-US3)**: Achieved a ~90% reduction in inline linting suppressions, bringing the codebase into alignment with the project's architectural constitution.
    - **Compose Cleanup (US1)**: Removed 35 redundant `@Suppress("FunctionNaming")` annotations from UI screens.
    - **Technical Debt Conversion (US2)**: Converted all remaining `TODO:` and `FIXME:` markers into documented `DEFERRED(040):` tracking format.
    - **Generic Exception Refactoring (US3)**: Replaced broad `catch(e: Exception)` blocks with granular, type-specific error handling (`SQLException`, `GeneralSecurityException`, `IllegalStateException`) mapped to `DomainError` types.
- **Platform Integrity**: Updated `PlatformBluetoothHid.kt` to include explicit API level 28 checks for hardware feature discovery.

### Governance & Alignment
- **Final Audit Compliance**: Completed the "Linting Baseline Audit" task in `specs/039-linting-baseline-audit/audit.md`, confirming that all "Invalid" suppressions have been remediated.
- **CI/CD Validation**: Verified the entire remediation pass via the `local-ci.ps1` pipeline, achieving a clean, suppression-free build state (excluding documented architectural allow-lists).
