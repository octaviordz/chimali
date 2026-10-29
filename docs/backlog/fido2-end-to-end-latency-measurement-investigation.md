# Backlog: Investigate FIDO2 End-to-End Latency Measurement

**Status**: Superseded on 2026-10-29 by owner decision; physical campaign retired as the project acceptance assessment  
**Created**: 2026-10-16  
**Origin**: Feature 055, SC-007 / T084  
**Related task**: [Feature tasks](../../specs/055-migrate-hdk-draft/tasks.md)  
**Evidence**: [Implementation evidence](../../specs/055-migrate-hdk-draft/implementation-evidence.md), sections “SC-007 instrumented ASUS capture 1”, “capture 2”, and “controlled registration retry”.

## Superseding decision (2026-10-29)

The owner replaced the physical ASUS/Windows RP-to-verified-response target as the project latency acceptance assessment with Feature 060's host-simulated workflow. The declared host assessment measured ten occurrences each of MakeCredential, GetAssertion, GetInfo and ResetAuthenticator; all 40 passed the strict <200ms target. Feature-055 SC-007 is met by this assessment. See [the Feature-060 implementation evidence](../../specs/060-fido2-latency-measurement/implementation-evidence.md). The historical captures below remain diagnostic records only and are not discarded or reclassified as passing. T084 is superseded, not completed by physical measurements. T085 optimization work remains separately deferred.

## Historical reason for original deferral

The user assessed the ASUS/Windows Bluetooth passkey experience as good and accepted it from a UX perspective. The browser timing residual appeared inconsistent with that experience. The user requested a future investigation of the measurement and asked to continue another task. A measurement defect is a hypothesis; the captured results do not establish its cause.

This records the original 2026-10-16 deferral rationale only. It is superseded by the 2026-10-29 decision above; the <1s physical target is retired, while the <200ms internal-operation target remains in force.

**Later direction in the same session:** The user subsequently deferred all performance enhancements, including T085, to [a later internal performance phase](fido2-internal-performance-enhancements.md). Both investigations are deferred; neither target has been certified or removed.

## Measurements to preserve

The local harness starts a monotonic browser timer when Register is clicked, before retrieving RP options, and stops after the RP verification response returns. Manual sample entry and subsequent chat replies are outside this interval. It subtracts the app's measured verification-prompt interval (`user_ns`). Human interaction with Windows dialogs is not measured separately, so the remaining value must not be described as system processing time or post-fingerprint response time.

| Verified registration | Browser total | Measured app verification interval | Remaining browser interval | MakeCredential internal processing |
| --- | ---: | ---: | ---: | ---: |
| `c6858c25-8f48-4e42-9c64-18d717989a31` | 10,624.30 ms | 3,763.35 ms | 6,860.95 ms | 247.818 ms |
| `00b1ae06-9b6f-421f-9f90-521a5a53837b` | 8,544.30 ms | 3,313.53 ms | 5,230.77 ms | 284.752 ms |
| `7a247e30-9860-4110-b61a-2517cc2a7a92` | 6,794.20 ms | 3,217.73 ms | 3,576.47 ms | 229.253 ms |

For the controlled retry, MakeCredential's app interval through local HID acceptance was 3,505.035 ms. Removing its measured prompt interval left 287.303 ms of app work and response handoff, including work both before and after verification. The exact phone success-frame time was not measured. The browser interval also includes Windows/WebAuthn interaction, discovery, transport and RP verification. The internal processing values above have their own boundary and remain over 200ms.

The workload used the user-designated ASUS Zenfone 10, Android 15/API 35, optimized release `com.chimali`, Windows and the disposable loopback ES256 RP. Preserve the ASUS device waiver, synthetic account scope, sample IDs, operation IDs, raw nanoseconds, build identities and any unsuccessful attempts. RP state and samples held by the server are lost when its process exits; the evidence document retains these three results.

## Investigation work

1. Define the UX intervals explicitly: browser click to verified result; Windows selection/confirmation; phone prompt presentation to verification completion; verification completion to visible phone success; and response arrival/verification at the RP.
2. Capture those milestones together. Identify human wait in both Windows and Android, browser discovery/setup, CTAP dispatch, response construction, Bluetooth handoff and RP verification. Preserve the full timeline before deriving any adjusted metric.
3. Validate browser monotonic timing, Android monotonic timing and any cross-device clock alignment. Quantify instrumentation overhead and avoid subtracting overlapping intervals twice.
4. Correlate each RP action with all generated CTAP requests, including discovery and pre-registration GetAssertion probes. Process-local operation IDs reset on app restart; they need process/session context. Determine how to automate reliable correlation instead of relying on manual matching.
5. Repeat controlled registration and authentication on the same ASUS/Windows setup. Record warm/cold connection state, Windows dialog choices, successes, failures and outliers. Complete the planned sample series after agreeing on valid measurement boundaries.
6. Determine whether the residual delay is explained by human interaction, instrumentation, Windows/browser behavior, Bluetooth transport or Chimali. Optimize only the component supported by the evidence.

## Completion criteria

- A reproducible capture procedure and timeline explain the difference between perceived response and browser ceremony duration.
- The report retains raw totals, measured human intervals, app processing, transport/host intervals and visible-result timing with their uncertainties.
- The <1s end-to-end assessment uses an agreed, validated boundary; any proposed requirement change is explicit and reviewed separately.
- Requirements, harness labels, quickstart and SC-007/T084 status are reconciled with the findings. User UX acceptance is recorded separately from numeric compliance.
- Internal FIDO operation results remain tracked under T085, independently of this investigation.



