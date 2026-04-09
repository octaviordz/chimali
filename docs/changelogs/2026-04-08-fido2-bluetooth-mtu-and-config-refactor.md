# FIDO2 Bluetooth MTU Fix and Configuration Refactoring

## Date: 2026-04-08

## Overview

This update resolves a critical Bluetooth Classic HID connection issue on Windows 11 caused by L2CAP MTU limitations, and refactors the scattered Bluetooth configuration parameters into a centralized, OEM-aware provider.

## Key Changes

### Stabilizing FIDO2 Bluetooth HID on Windows 11
*   **Root Cause Addressed:** Identified that Windows 11 `bthid.sys` driver strictly enforces a 64-byte L2CAP MTU limit for HID interrupt channels and refuses to fragment output reports. The previous 64-byte payload size, combined with the 2-byte HID header (1-byte header + 1-byte Report ID), resulted in a 66-byte L2CAP packet, triggering an `ERROR_NOT_SUPPORTED (0x32)` rejection.
*   **MTU Alignment Fix:** Reconfigured the HID report descriptor and packet framing to use a **62-byte payload**. This ensures the total packet size (64 bytes) fits exactly within the L2CAP MTU limit, preventing driver-level rejection.
*   **Implementation Details:**
    *   Modified `FIDO_HID_REPORT_DESCRIPTOR` in `BluetoothHidDeviceWrapper.kt` to set the `Report Count` to 62 (`0x3E`) bytes.
    *   Updated `FIDO_HID_REPORT_SIZE` to 62.
    *   Re-calibrated the parser in `HidReportParser.kt` to account for the new 62-byte packet size.
*   **Result:** The FIDO2 authenticator now successfully completes the full `CTAPHID` handshake (`INIT`, `GetInfo`, `GetAssertion`, `MakeCredential`) over Bluetooth HID on Windows 11 without connection drops.

### Centralized Bluetooth Configuration (`BluetoothHidConfigProvider`)
*   **Problem:** The Bluetooth connection workflow had 12 hardcoded "dials" (timeouts, retries, delays, report sizes) scattered across multiple files (`BluetoothHidDeviceWrapper.kt`, `BluetoothHidTransportImpl.kt`, `HidReportParser.kt`), making device-specific tuning difficult.
*   **Solution:** Refactored `BluetoothQuirks` into a centralized `BluetoothHidConfigProvider`.
*   **Implementation Details:**
    *   Created a new data class `BluetoothHidConfig` to encapsulate proxy acquisition, app registration, packet pacing, and connection behavior settings.
    *   Renamed `BluetoothQuirks` to `BluetoothHidConfigProvider` to reflect its new role as a configuration provider.
    *   Updated all transport and wrapper classes to retrieve their operating parameters (e.g., `REPORT_PACE_DELAY_MS`, timeouts, retries) from the lazily resolved `BluetoothHidConfig` instance.
    *   Removed obsolete quirk functions, such as the explicit QOS workaround, as the underlying connection drop issue was solved by the MTU alignment fix.
    *   This architecture allows for easy extension and injection of OEM-specific settings if needed in the future, while maintaining universally safe defaults.

## Documentation Updates
*   Updated `docs/issues/windows-11-ctaphid-bthid-error-0x32.md` to document the 64-byte MTU limit and the applied 62-byte Report count solution.
*   Added a section detailing the Transport Constraints and L2CAP MTU bug to `docs/FIDO2_Windows_Bluetooth_Analysis.md`.
*   Generated a comprehensive feasibility analysis document for the configuration refactoring (`bluetooth_quirks_analysis.md`).
