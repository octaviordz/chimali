# Backlog: Bluetooth HID Physical Interoperability Verification

**Status**: Open — complete physical matrix unverified

**Created**: 2026-11-13 (split from feature 057 follow-ups)

**Tracking ID**: `FUP-INT-001`

**Origin**: [Feature 057 follow-up index](../../specs/057-improve-verification-contracts/follow-ups.md) and [original combined backlog](../../specs/057-improve-verification-contracts/testability-backlog-origin.md). This file owns active scope, status and completion evidence for this work area.

## Context carried forward

- **Governing Area**: Virtual Authenticator & Bluetooth Transport (Constitution Principle VIII, BRD `FR-HID-010`, `FR-VAL-010`, TRD §4.2 / §9)
- **Mapped Feature Requirements**: FR-009, FR-010, FR-011
- **Remaining Scope**: Execution of physical Bluetooth HID pairing, connection maintenance, fragment handling, CTAP2 packet exchange, and failure recovery across real Android devices and desktop hosts (Windows, macOS, Linux).
- **Reason Excluded**: Feature 057 establishes documentary lifecycle and failure matrices; physical transport test execution is excluded.
- **Current Verification Status**: `Unverified` (Matrices specified; physical hardware runs pending)
- **Lifecycle Status**: `Assigned`
- **Preserved Authorization / Deferral Reference**: BRD `FR-VAL-010` multi-device interoperability requirement; TRD §4.2.
- **Responsible Role**: QA / Bluetooth Hardware Verification Engineer
- **Needed Completion Evidence**: Dated test execution reports covering reference Android devices (API 28+) connected to Windows/macOS/Linux hosts, with capture logs demonstrating successful ceremonies and safe termination under disconnect, timeout, and malformed input.

## Next steps

Reconcile existing evidence before running missing cases. Declare Android devices, Windows/macOS/Linux hosts, pairing state and restart strategy; execute the Classic HID journey and failure matrix with independent response verification. Preserve append-only history and identify unsupported combinations. Later BLE results do not establish Classic HID interoperability.

## Evidence and completion

Maintain a journey-to-requirement/test matrix and named run reports in the implementing feature's evidence record, linked from the TRD. Record environment, restart strategy, substitutions, and unresolved limitations.

Approved journey matrix; executed success and applicable failure cases; verified persistence/reconstruction and response results; clear host/device evidence boundaries; unresolved paths remain open.

Classic HID is the scope of this follow-up; later BLE and transport-selection requirements retain their own evidence obligations.
