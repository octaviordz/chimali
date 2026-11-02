# Backlog: Preserve Biometric UX During In-Transit Registration and Device Rotation

**Status**: Open — UX and lifecycle investigation
**Created**: 2026-11-01
**Origin**: User-reported registration and device-rotation experience

## Problem

When a user starts registration while in transit and the device rotates during the flow, the biometric UI stops appearing. The registration journey has no clear recovery or explanation, leaving the user unable to continue or unsure whether the request is still active.

## Objective

Provide a resilient registration experience when device configuration changes during biometric authentication. Preserve or deliberately restart the biometric prompt, keep registration state coherent, and tell the user what action is needed.

## Investigation and acceptance criteria

- Reproduce registration with a device rotation while the biometric prompt is active or about to appear, including the reported in-transit conditions where available.
- Identify whether rotation recreates or detaches the UI, loses prompt state, cancels the platform authentication request, or leaves registration waiting on a stale callback.
- Define expected behavior for rotation before, during, and after the biometric prompt, including cancellation, retry, and process recreation where applicable.
- Ensure the prompt is restored or the user receives a clear, actionable recovery path; never leave registration silently waiting with no biometric UI.
- Verify completion, cancellation, and retry do not submit duplicate registration requests or corrupt registration state.
- Add focused regression coverage for the reproduced lifecycle/configuration-change path.

## Notes

“In transit” is currently the reported usage context; its exact environmental meaning and relationship to the failure should be confirmed during reproduction. No root cause is established yet.
