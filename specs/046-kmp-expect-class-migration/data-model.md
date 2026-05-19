# Data Model: KMP Expect/Actual Class Migration

**Feature**: Remove KMP Expect/Actual Class Suppressions + Green CI
**Date**: 2026-05-18
**Branch**: `046-kmp-expect-class-migration`

## Entities

### 1. PlatformBluetoothHid [MIGRATED]

| Operation | Contract |
|-----------|----------|
| `isSupported()` | Device/OS supports BT HID Device profile |
| `isAdapterEnabled()` | Bluetooth adapter powered on |

| Implementation | Source set | Status |
|----------------|------------|--------|
| `AndroidPlatformBluetoothHid(context)` | androidMain | Done |
| `IosPlatformBluetoothHid()` | iosMain | Done (placeholder `false`) |

**Callers**: None in production yet.

---

### 2. PlatformLock [MIGRATED]

| Operation | Contract |
|-----------|----------|
| `lock()` / `unlock()` | Mutual exclusion |
| `withLock { }` | commonMain extension |

| Implementation | Source set | Status |
|----------------|------------|--------|
| `AndroidPlatformLock` | androidMain | Done (`ReentrantLock`) |
| `IosPlatformLock` | iosMain | Done (`NSRecursiveLock`) |

**Callers**: `LocalCrashReportingLogWriter` → `AndroidPlatformLock()` (androidMain).

---

### 3. Local CI gate (process entity)

| Attribute | Value |
|-----------|-------|
| Script | `tools/local-ci.ps1` |
| Success | Exit code 0 |
| Phases | ktlint, detekt, lintRelease, compile*, test |

**Validation**: FR-007, SC-003, SC-005.

---

## Relationships

```text
PlatformBluetoothHid ──implemented by──> AndroidPlatformBluetoothHid | IosPlatformBluetoothHid
PlatformLock ──implemented by──> AndroidPlatformLock | IosPlatformLock
AndroidPlatformLock ──used by──> LocalCrashReportingLogWriter
local-ci.ps1 ──validates──> entire Gradle tree (any failing module in scope per FR-008)
```

## Validation rules

- FR-001–FR-006: KMP migration invariants (see [contracts/platform-abstractions.md](contracts/platform-abstractions.md))
- FR-007–FR-010: CI and test/static-analysis policy per clarify session
- Tests: update or delete obsolete; never disable
