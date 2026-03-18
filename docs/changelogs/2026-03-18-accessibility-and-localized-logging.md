# Changelog: Accessibility Enhancements & Localized Logging

**Date:** 2026-03-18  
**Scope:** `feature:fido2`, `core:ui`, `core:common`  
**Tasks:** T136, T139, T142, T143, T149–T153

## Overview
This update focuses on two critical pillars of the Chimali Constitution: **Stability (Localized Error Handling)** and **Inclusivity (Accessibility)**. We have implemented a privacy-preserving local logging system, integrated more legible typography, and completed a comprehensive TalkBack audit/fix cycle for the FIDO2 authenticator.

---

## [T142] Atkinson Hyperlegible Font Integration
To comply with **Constitution §VI (Accessibility)**, we have integrated the **Atkinson Hyperlegible** font into the common UI layer.
- **Shared Type System**: Centralized font assets in `core:ui` and introduced `LegibilityType` for easy consumption across features.
- **Improved Scannability**: Applied Atkinson Hyperlegible to high-density data views, specifically FIDO2 mnemonic recovery and credential lists, ensuring that similar characters (like `I`, `l`, `1`) are easily distinguishable.

## [T136] Background Crypto Processing
Optimized the performance and UI responsiveness of cryptographic operations.
- **Dispatchers Distilled**: Introduced `@DefaultDispatcher` and `@IoDispatcher` qualifiers.
- **Non-Blocking Signing**: Offloaded `sign()` and `generateCredentialKeyPair()` in `Fido2CryptoService` to the default background dispatcher, preventing frames drops during heavy P-256 or PQC computations.

## [T139 & T143] TalkBack Support & UI Testing
Enhanced the screen reader experience for users with visual impairments.
- **Heading Hierarchy**: Explicitly marked screen titles (Registration, Authentication, Dev Tools) with the `Heading` semantic role.
- **Semantic Merging**: Optimized RP and User cards to be read as single, coherent nodes by TalkBack, reducing navigation fatigue.
- **Live Regions**: Implemented `LiveRegionMode.Polite` for success and error containers to ensure that status changes are announced immediately.
- **Automated Verification**: Added a suite of Compose UI tests to verify that these accessibility semantics remain intact during future refactors.

## [T149–T153] Localized Error Handling & Logging
Implemented a robust, privacy-first diagnostic system that adheres to **Constitution §VIII (Local-only Logic)**.
- **Privacy Scrubber**: Created a highly sensitive `PrivacyLogScrubber` that uses optimized regex to redact BIP39 mnemonics, private keys, and PINs from logs before they reach the disk.
- **Local Crash Reports**: Introduced `LocalCrashReportingTree` for Timber, writing vital diagnostics to a rotating 5MB file on the device. **Zero cloud sync** is enforced; logs can only be retrieved by the user via physical or developer access.
- **FIDO2 Error Mapping**: Refactored error handling to map technical CTAP2 and Bluetooth exceptions into user-friendly UI messages, providing clear "Retry" guidance for connection failures.

---

## Technical Details
- **New Dependencies**: `com.jakewharton.timber:timber`
- **Modules Affected**: `feature:fido2`, `core:ui`, `core:common`, `core:security`
- **Verification**: 
  - 10+ new unit tests for log scrubbing and error mapping.
  - New instrumentation tests for UI accessibility semantics.
  - Verified local log rotation and file size capping.
