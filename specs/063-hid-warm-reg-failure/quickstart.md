# Quickstart: Bluetooth HID Warm Registration Investigation

**Feature Branch**: `063-hid-warm-reg-failure` | **Date**: 2026-11-02  
**Purpose**: Guide to running the behavioral investigation test suite and reproducing/validating failure attribution.

---

## 1. Prerequisites

- PowerShell 7+
- JDK 17+ configured in environment (`JAVA_HOME`)
- Android SDK 35+ build-tools available
- Anchored worktree: `D:\octav\source\repos\.worktrees\chimali\063-hid-warm-reg-failure`

---

## 2. Test Execution Commands

### Execute Host-Side Bluetooth HID Lifecycle Tests

```powershell
cd D:\octav\source\repos\.worktrees\chimali\063-hid-warm-reg-failure
.\gradlew.bat :feature:fido2:testDebugUnitTest --tests "com.chimali.fido2.bluetooth.*"
```

### Execute Callback Coordinator Race Condition Tests

```powershell
cd D:\octav\source\repos\.worktrees\chimali\063-hid-warm-reg-failure
.\gradlew.bat :feature:fido2:testDebugUnitTest --tests "com.chimali.fido2.bluetooth.BluetoothHidCallbackCoordinatorTest"
```

### Execute Warm-N Thread Boundedness Harness (Once Created)

```powershell
cd D:\octav\source\repos\.worktrees\chimali\063-hid-warm-reg-failure
.\gradlew.bat :feature:fido2:testDebugUnitTest --tests "com.chimali.fido2.bluetooth.BluetoothHidWarmRegistrationStressTest"
```

---

## 3. Interpreting Results

| Output / Assertion | Interpretation |
|---|---|
| Captured executors are non-terminated after their cycles complete | **DEFECT SUPPORTED**: Executor leak in `BluetoothHidDeviceWrapper.registerApp()` confirmed under the modelled harness conditions (Issue 2). Does not alone establish the warm-08 device threshold or external causation. |
| Stale `callback_k.onAppStatusChanged(false)` cancels `cont_{k+1}` before the current event arrives | **DEFECT SUPPORTED**: Shared-state interference from stale deregistration event confirmed under the modelled conditions (Issues 1 & 4). Does not rule out additional mechanisms on device. |
| All captured executors terminate after each cycle AND stale callbacks do not affect `cont_{k+1}` | **Within tested conditions, neither executor leak nor callback race was observed.** This excludes those mechanisms under the modelled delivery schedule; it does not exclude device-specific Binder ordering, OS-level registration limits, or other unmodelled failure causes. |
| `cont_{k+1}` does NOT resolve with failure when genuine `false` delivered to `callback_{k+1}` (control fails) | **Harness invalid**: cannot distinguish stale from genuine events; Mechanism A results are unreliable. Fix the control scenario before interpreting other results. |
