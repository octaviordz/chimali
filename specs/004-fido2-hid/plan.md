# Implementation Plan: FIDO2 Virtual Authenticator via BluetoothHidDevice

**Branch**: `004-fido2-hid` | **Date**: 2026-03-17 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/004-fido2-hid/spec.md`

## Summary

Implement localized Error Handling & Logging for the FIDO2 Virtual Authenticator. To comply with the strict "no cloud processing" and privacy mandates of the project's Constitution, logging and crash reporting will be entirely local-only (on-device). Sensitive data (e.g., cryptographic material, biometric events) will be explicitly excluded from all logs. The system will provide comprehensive, user-friendly error messages during Bluetooth HID disruptions, FIDO2 protocol failures, or validation errors.

## Technical Context

**Language/Version**: Kotlin 1.9+ (Android Native)  
**Primary Dependencies**: AndroidX, Jetpack Compose, Timber (for structured local logging)
**Storage**: Local App Data directory for crash logs (custom rotating file sink via Timber tree, NEVER shipped to cloud).  
**Testing**: JUnit 5, MockK (for verifying logger exclusions).  
**Target Platform**: Android 9.0+ (API 28+)  
**Project Type**: Mobile Application  
**Performance Goals**: Local logging overhead < 5ms per event.   
**Constraints**: Absolute privacy (no remote crash reporting tools like Crashlytics). Sensitive parameters must be masked.  
**Scale/Scope**: Limit local log files to 5MB rotating buffer to prevent disk exhaustion.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### I. Security First (Zero-Trust Local-First)
- ✅ Local-only crash reporting ensures no sensitive data leaves the device.
- ✅ Timber trees will be configured to mask or exclude sensitive values from logs.

### III. Uncompromising Architecture & Quality 
- ✅ Error handling mapped cleanly via MVI state flows.
- ✅ Custom Timber tree implementation for localized capture.

### IV. Performance & Reliability Excellence 
- ✅ Log rotation (e.g., 5MB cap) ensures no disk/memory leaks from logging.

### VI. Inclusion & Universal Accessibility
- ✅ User-friendly error messages mapped from technical CTAP2 codes.

### Technical Constraints & Privacy Focus
- ✅ No cloud crash reporting meets the "On-device AI only / no cloud processing" mandate.

## Project Structure

### Documentation (this feature)

```text
specs/004-fido2-hid/
├── plan.md              # This file
├── research.md          
├── data-model.md        
├── contracts/           
└── tasks.md             
```

### Source Code (repository root)

```text
feature/fido2/
├── src/main/kotlin/
│   ├── presentation/
│   │   ├── error/          # User-friendly error mapping
│   ├── util/
│   │   ├── logging/        # Timber trees and local crash reporting sinks
├── src/test/kotlin/        # Unit tests verifying no sensitive data is logged
```

**Structure Decision**: Extending the existing Clean Architecture within `feature:fido2` module, adding utilities for local logging and refined presentation mappers for error states.
