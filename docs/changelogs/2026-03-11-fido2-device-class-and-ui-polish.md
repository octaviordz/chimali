# Changelog: FIDO2 Device Class Identification & UI Polish - 2026-03-11

## Overview
This update focuses on enhancing the user experience for managing paired devices by introducing device-specific icons and refining the application's overall UI hierarchy for a more premium, responsive feel.

## Changes

### 1. Device Class Identification
- **Automatic Class Capture**: Modified the Bluetooth transport layer to extract the "Major Device Class" from connecting hosts.
- **Icon Mapping System**: Implemented a native mapping from Bluetooth Class-of-Device bits to Material Design icons:
    - `0x0100` (Computer) → `Icons.Default.Computer` (Laptop icon)
    - `0x0200` (Phone) → `Icons.Default.Smartphone` (Phone icon)
    - `0x0700` (Wearable) → `Icons.Default.Watch` (Watch icon)
- **Persistent Metadata**: Updated the `PairedDevice` data model and database schema (via Migration 3) to store this metadata, ensuring icons display immediately upon app launch.

### 2. UI/UX Refinement
- **Status Hierarchy Flip**: Redesigned the `StatusIndicator` card to prioritize the connected device name as the primary headline (large bold font) while the "Connected to PC" label is now secondary.
- **Bottom Navigation**: Introduced a `BottomNavigationBar` to cleanly separate the core Authenticator dashboard from developer-centric tools.
- **Optimized Typography**: Reduced the visual weight of top-level screen titles (e.g., "Chimali Authenticator") for a more modern, balanced layout.
- **Proactive Swipe-to-Delete**: 
    - Swiped items are now instantly removed from the UI state for immediate feedback.
    - Extended the "Undo" window to 10 seconds to reduce accidental deletions.
    - Improved visual aesthetics with background color and icon animations appearing only during the swipe gesture.

### 3. Stability & Architecture
- **SQLDelight Hardening**: Fixed a critical `NullPointerException` caused by a mismatch between physical SQLite column order (appened by `ALTER TABLE`) and the SQLDelight schema definition.
- **Smart-Merge Repository**: Implemented logic in `PairedDeviceRepository` to ignore generic "Uncategorized" class reports that often occur during fast reconnections, preventing them from overwriting previously captured high-fidelity device icons.
- **Documentation**: Added comprehensive architectural and lifecycle KDocs to `BluetoothHidAuthenticatorImpl.kt`.

## Impact
- **Clarity**: Users can now distinguish between their laptop, desktop, and mobile devices at a glance.
- **Reliability**: Fixed edge cases in database migrations and Bluetooth property reporting.
- **Premium Feel**: Improved animations, transitions, and typography align with the project's design goals.
