# Implementation Plan: Bluetooth Transport Selection

**Branch**: `064-bluetooth-transport-selection` | **Date**: 2026-11-02 | **Spec**: [spec.md](spec.md)

**Input**: Specification and its four accepted clarifications.

**Delivery coordination**: Follow [integration.md](integration.md) for the shared contract, task ownership and milestone order. Implement the parent gate/composition as a prerequisite, accept the independent BLE component first, and close this parent feature after its full selection acceptance.

**Status (2026-11-08)**: Implementation is integrated with the independent FIDO BLE part. ASUS/API35 has completed current-candidate BLE and Classic registration/authentication, and encrypted credential-store contents were byte-identical across transport-switch restarts. Mi/API29 has verified BLE default selection, explicit advertising start, reboot persistence and Bluetooth-off retry; Classic HID initialization is unavailable on this Mi because Android could not bind IBluetoothHidDevice (parent Records 222–225). SC-001/003/004/006 have passing evidence; SC-002 exact credential/event mapping and SC-005 physical ceremony coverage remain partial. All four physical SC-005 combinations are now verified across Records 190, 220, 228 and 229: BLE registration and authentication, and Classic registration and authentication, each succeeded with the alternate transport saved pending. Parent T035 still has lifecycle/unavailable-mode cases open, including natural OS resource reclamation. The latest non-coverage local CI passed (Record 224); a selected ASUS BLE device-suite subset passed 9/9 (Record 208), and the full FIDO2 connected run had 56/63 passes with seven external setup prerequisites unmet (Record 219). Parent T039 and BLE T038 coverage/nonregression are authorized for execution but have not yet passed. See [integration.md](integration.md) and the evidence ledger.

## Summary

Add Classic HID and FIDO BLE choices under Settings > Passkey Authenticator > Bluetooth HID. Default absent preferences to FIDO BLE, including development installations. Reuse Proto DataStore and freeze one process choice before Bluetooth initialization. Saved changes remain pending until the whole process restarts and never interrupt a ceremony. Startup failure leaves the selected mode unavailable, with explicit user recovery and no automatic fallback.

The independent BLE part supplies the functional FIDO Bluetooth service and lifecycle compatibility with existing consumers. This feature supplies selection and wiring, not the BLE protocol implementation, a hybrid transport or a general transport framework. Chimali has no releases; no old-default migration is needed.

## Technical Context

**Language/Version**: Kotlin 2.3.21 / JVM 17, Gradle 9.6.0, AGP 9.4.1; retain the checked-in toolchain.

**Primary Dependencies**: Existing Compose/Material 3, Koin 4.2.1 with generated modules, coroutines 1.11.0, kotlinx.serialization ProtoBuf and AndroidX DataStore 1.1.1. No new Bluetooth library in this feature.

**Storage**: Existing single UserPreferences DataStore at `filesDir/datastore/user_preferences.pb`; add transport code and selection timestamp at unused ProtoNumber 9 and 10. Persist preference only; derive pending status from the process snapshot.

**Testing**: Shared serialization tests in core/common commonTest; Android host tests for the current Android-only FIDO2 repository/controller; settings JVM and Compose device tests; real Android/Windows ceremony validation. Independent fake transports verify lifecycle side effects. Preserve Classic lifecycle/cleanup regression tests.

**Target Platform**: Android API 28+, compile SDK 37 / target SDK 35 as checked in; Windows 11 physical feature acceptance. API availability alone does not guarantee peripheral capability. iOS implementation is outside scope; existing Classic cross-platform obligations remain.

**Project Type**: Existing Android/KMP application, no new Gradle module or external API.

**Performance Goals**: No preference access in packet processing or main-thread blocking. Preserve cold <2s, warm <1s, hot <500ms start, 60 FPS and internal FIDO operation <200ms excluding verification waits and Bluetooth handoff. Physical ceremony latency remains diagnostic under Constitution VI.

**Constraints**: Exactly one selected implementation per process; unselected hardware resources unopened. Service/Activity recreation and reconnect never apply pending preference. Preserve credential history, authorization, erasure and FIFO delivery. Keep hosted runner disabled under the feature-058 waiver.

**Scale/Scope**: M, conditional on separate BLE delivery: one settings subsection, two choices, two durable fields, a process gate and focused UI/repository/composition tests.

## Constitution Check

*Initial and post-design documentary assessment; no execution pass is claimed.*

| Principle | Initial finding | Post-design disposition |
| --- | --- | --- |
| I–II: security | Transport preference is not secret; ceremonies still handle secrets. | Satisfied in design: no changes to key derivation, authorization or erasure; synthetic evidence; static mode/error diagnostics only. |
| III: architecture/UDF | Settings is stateless; Classic is automatically bound by Koin. | Satisfied in design: data repository owns preferences; controller owns runtime state; UI sends intents and observes domain state. App alone composes concrete implementations. |
| IV: auditability | Selection is configuration, not a credential aggregate. | Satisfied in design: no event-store migration, rollback or credential modification. |
| V: traceability/evidence | Existing HID IDs do not define selector/default/restart semantics. | Implementation prerequisite: allocate canonical BRD/TRD requirements before Tier 1 changes. Map FR-001–012 to those IDs, never annotate using feature-local IDs. Enforce existing 60% Tier 1 instruction/applicable branch, 20% Tier 2 instruction, 50% aggregate floors and changed-Tier-1 nonregression. |
| VI: resource budgets | Startup read must precede transport initialization. | Satisfied in design: asynchronous startup snapshot and selected-transport activation gates, each with a 5s safety deadline and cancellation fence. This is a failure bound, not permission to exceed cold-start budgets. |
| VII: scope/docs | Research proposes broader Bluetooth modernization. | Satisfied in design: reuse modules/store/lifecycle boundaries. Synchronize BRD/TRD/rules/user guidance during implementation. No registry, dependency upgrade or migration framework. |
| VIII: Classic/accessibility | Constitution names BluetoothHidDevice across Windows/macOS/Linux. | Additive design retains that capability and all existing obligations. Windows-only feature validation does not waive them. Replacement of the constitutional API mandate would require a separately approved amendment. Radio controls expose active/pending/error semantics; validate TalkBack, contrast and 200% scaling. |

**Gate outcome (2026-11-08)**: Canonical requirement synchronization, the parent selector and the independent BLE integration are implemented. ASUS/API35 physical ceremonies verify BLE and Classic operation; the transport-switch database comparison preserves encrypted credential/event data, and the current system-Back authentication retry succeeded. The task-specific physical acceptance is still partial: two of four physical combinations that change transport preference during registration/authentication remain unverified, while the covered BLE registration/Classic authentication directions succeeded (Records 190 and 220). The rest of parent T035 includes lifecycle and unavailable-mode cases, with natural OS process reclamation still unverified. Parent T039/BLE T038 coverage checks remain owner-deferred and are not passes. Do not treat the partial matrix as full acceptance.

## Project Structure

### Documentation (this feature)

```text
specs/064-bluetooth-transport-selection/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
└── contracts/transport-selection.md
```

`tasks.md` belongs to `/speckit-tasks`, not this planning phase.

### Source Code (repository root)

```text
core/common/src/commonMain/kotlin/com/chimali/core/common/datastore/UserPreferences.kt
core/common/src/commonTest/                            # serialization/default tests
feature/fido2/src/androidMain/kotlin/com/chimali/fido2/
  domain/repository/Fido2SettingsRepository.kt          # preference operations
  domain/                                             # selection/runtime contracts
  data/repository/Fido2SettingsRepositoryImpl.kt
  data/transport/                                     # process gate/selected handle
  presentation/viewmodel/                             # settings state and intents
  service/Fido2TransportService.kt                    # shared selected handle
  di/Fido2Module.kt                                   # avoid competing bindings
feature/fido2/src/androidHostTest/                     # lifecycle/selection tests
feature/settings/src/main/kotlin/com/chimali/feature/settings/ui/SettingsScreen.kt
feature/settings/src/test/
feature/settings/src/androidTest/
app/src/main/kotlin/com/chimali/ChimaliApplication.kt
app/src/main/kotlin/com/chimali/navigation/AppNavGraph.kt
docs/brd.md, docs/trd.md, .agents/rules/specify-rules.md
```

**Structure Decision**: Extend existing modules. Keep settings stateless with app-supplied state/callbacks. Extend the existing FIDO2 settings repository without invoking its unrelated max-credential legacy migration. Current Android-only APIs use configured host tests; do not migrate source sets for this selector.

## Phase 0: Research

[research.md](research.md) records repository findings, primary references, decisions and alternatives. Design unknowns have explicit dispositions. Missing BLE code is a known dependency; exact physical compatibility is future acceptance evidence.

## Phase 1: Design

1. Add stable transport code and timestamp fields, defaulting a missing choice to FIDO BLE. Unknown explicit codes or read/corruption failures produce unavailable configuration state without resetting the shared file. After freezing the choice, perform a non-initializing check for reliably detectable unsupported, permission-required or Bluetooth-off conditions; if support cannot be determined without activation, expose SelectedNotStarted until a start request.
2. Extend Fido2SettingsRepository observation/save operations. Use atomic `updateData` copies preserving unrelated fields. Acknowledge only durable writes; serialize saves and retain prior selection on failure.
3. From ChimaliApplication after DI setup, start one asynchronous process-scoped snapshot before settings writes or Bluetooth use. Its 5s deadline starts at snapshot launch and ends when a valid choice is frozen or the operation fails. Timeout cancels and fences that attempt; before any choice is frozen, an explicit retry starts a fresh 5s snapshot attempt. Once frozen, the choice is immutable for the process. Never block the main thread with runBlocking.
4. Allow Android to create the service shell during snapshot resolution, but defer all selected transport construction, Bluetooth initialization and use until the snapshot is frozen. Give service and home screen one gated Fido2Transport handle. Lazily start activation of only the frozen choice on the first transport-start request; a separate 5s activation deadline starts with that attempt and ends at ready or failure. Concurrent callers share the same attempt. On timeout or cancellation the controller fences late callbacks and releases attempt-owned resources before retry. Explicit retry after correcting permission or Bluetooth starts a fresh bounded activation attempt for the same frozen choice; never retry the alternative automatically. Service restart/reconnect reuses the process choice; only full process restart reads pending selection.
5. Expose controller-owned runtime state through a read-only domain boundary to a FIDO2 presentation ViewModel. Derive pending status against the repository's saved choice. Unavailable is never shown as active. Save intents cannot rebind DI or alter service/ceremony lifecycle.
6. Compose the selected singleton explicitly in :app. Remove concrete implementation bindings from feature module scanning; define any qualified concrete factories and the single unqualified Fido2Transport binding only at the :app composition root. The BLE prerequisite must reconcile HidConnectionState and connectDevice semantics before integration; do not open both implementations to probe capability.
7. Add the currently missing settings subsection, radio choices, active/pending text, loading/save-error feedback and full-process restart instructions. Returning to the current mode clears pending. No automatic restart action is introduced.
8. Apply [data-model.md](data-model.md) and [contracts/transport-selection.md](contracts/transport-selection.md); validate through [quickstart.md](quickstart.md). Record the ten TRD evidence fields and distinguish logic tests from physical ceremonies.

## Complexity Tracking

No architectural exception or new module is introduced. The process gate and read-only runtime boundary serve Principle III ownership and shared UI/service lifecycle requirements. Broader transport changes belong to the BLE prerequisite and require their own review.
