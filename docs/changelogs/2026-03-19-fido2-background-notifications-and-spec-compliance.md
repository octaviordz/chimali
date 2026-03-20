# FIDO2 Background Notifications & Spec Compliance

**Date:** 2026-03-19

## Overview
Improved FIDO2 reliability on Windows by implementing high-priority background notifications and aligning the CTAP2 protocol response with the FIDO specification. These changes address the `0x5B4` (Timeout) and `0x8007000d` (Invalid Data) errors encountered during registration and authentication.

## Detailed Changes

### 1. High-Priority Background Notifications (FAILED VERIFICATION)
- **Problem**: Android prevents the `BiometricPrompt` from launching if the app is in the background. When Windows sends a WebAuthn request, the app would receive it but hang silently, eventually causing a Windows timeout (`0x5B4`).
- **Initial Fix (Non-functional)**: Enhanced `Fido2TransportService.kt` to monitor the `Fido2UiEventBus`. If a request arrives while the app is in the background:
  - A High-Priority (Heads-Up) notification is triggered.
  - **Result**: Manual testing by the USER confirmed that no notification was received. The feature requires further debugging of Notification Channel importance, `setFullScreenIntent` permissions, or Service-to-UI event timing.

### 2. GetInfo Feature Advertisement Fix (0x8007000d)
- **Problem**: The `GetInfo` response was incorrectly advertising `clientPin` and `pinUvAuthProtocols`. This caused Windows to attempt a `authenticatorClientPIN` (0x06) command, which is not supported by our authenticator (we use internal UV/Biometrics only).
- **Fix**: Removed PIN-related fields from `Ctap2ResponseBuilder.kt`. This forces Windows to use the standard Internal UV path.

### 3. CTAPHID_INIT Capabilities Correction
- **Problem**: Our `CTAPHID_INIT` response was setting the `CAPABILITY_NMSG` (0x08) bit. Per spec, this bit indicates that the `MSG` command is **NOT** supported. Some Windows drivers interpret this strictly and may fail the device state validation on first contact.
- **Fix**: Aligned `HidReportParser.kt` with `rauth-android` to only advertise `CAPABILITY_CBOR` (0x04).

### 4. CTAP Error Code Standardization
- **Problem**: Unsupported CTAP2 commands were returning `0x3E` (`CTAP2_ERR_OPERATION_DENIED`) or other non-standard codes.
- **Fix**: Standardized on `0x01` (`CTAP1_ERR_INVALID_COMMAND`) as the default error for unsupported commands in `BluetoothHidTransportImpl.kt`, as required by the CTAP specification.

### 5. Latency Profiling Regression Fix (NFR-PERF-030)
- **Problem**: Recent ViewModel changes were overwriting the system latency timer, incorrectly including user biometric interaction time in "system latency" reports.
- **Fix**: Properly wrapped `deferred.await()` in `Ctap2MakeCredentialHandler.kt` with `LatencyProfiler` start/end markers and removed redundant triggers from the ViewModels.
