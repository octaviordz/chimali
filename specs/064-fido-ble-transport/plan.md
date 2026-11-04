# Implementation Plan: FIDO BLE Transport

**Branch**: 064-bluetooth-transport-selection (actual shared branch) | **Date**: 2026-11-02 | **Spec**: [spec.md](spec.md)
**Status**: Phase 0 research and Phase 1 design complete. Runtime verification: Unverified.

**Delivery coordination**: Follow the parent [integration.md](../064-bluetooth-transport-selection/integration.md). This component retains independent implementation and acceptance; shared parent gate/composition precedes its physical acceptance, and this component closes before the complete parent selection feature.

**Input**: This is an independently specified part of [064-bluetooth-transport-selection](../064-bluetooth-transport-selection/spec.md). It supplies that feature's real BLE prerequisite; it does not create another selector or branch. The setup script's BRANCH field reflects the selected feature directory, not the actual Git branch.

## Summary

Implement FIDO2 over BLE GATT service 0xFFFD, with authenticated system numeric-comparison pairing, one active host, bounded framing and notification ownership, continuous enabled advertising, a three-second fragment receive deadline, and correct CANCEL/keepalive behavior. Reuse existing credential, authorization, verification and event semantics through an unframed CTAP operation boundary shared with Classic.

Nordic Kotlin BLE Library is selected. Pin its released 1.3.1 advertiser as the initial integration baseline. Its high-level server automatically acknowledges writes and uses dropping event buffers, so it cannot own the protocol-critical receive path. Use a narrow public Android BluetoothGattServer callback layer for that path, without a library fork or hidden APIs. [research.md](research.md) documents exact source evidence and the unavailable v2 server.

## Technical Context

**Language/Version**: Existing Kotlin 2.3.21, JVM 17, Gradle 9.6.0, AGP 9.4.1, compile SDK 37, minimum Android API 28. No toolchain upgrade.
**Primary Dependencies**: Nordic no.nordicsemi.android.kotlin.ble:advertiser:1.3.1; existing Android Bluetooth APIs, coroutines, Koin, CBOR and test infrastructure. Resolve published artifact metadata and transitive licenses before implementation integration; source inspection alone is not a successful build. Do not add Nordic's high-level server or mock dependency to production by default.
**Storage**: Existing credentials/events/preferences. Only transient transport state is new; Android owns bond records. No new database or migration.
**Testing**: Independent protocol vectors, deterministic host clock/callback fakes, integration with real CTAP handlers, Android security/lifecycle tests and physical Windows 11 ceremonies. See [quickstart.md](quickstart.md).
**Target Platform**: Android API 28+ with actual peripheral advertising and authenticated pairing support; Windows 11 22H2+ as a physical acceptance target. Neither API level nor documentation proves device interoperability.
**Project Type**: Existing Android/KMP application; Android implementation only.
**Performance Goals**: Every internal FIDO operation <200ms in the constitutional host-simulated assessment, excluding measured UV work and Bluetooth response handoff. Physical ceremony timing is diagnostic. Advertising start <500ms and Stop <1s remain feature criteria, within the parent's separate five-second activation safety deadline.
**Constraints**: No automatic Classic fallback; app-only concrete bindings; mutable owned sensitive buffers erased on every terminal path; append-only events; bounded queues/retries; no logging credentials or host identifiers. Maintain AAB <=50MB and dependency delta <=500KB unless architecture review authorizes otherwise.
**Scale/Scope**: One active BLE host and one transaction. Existing feature:fido2 and app modules; no new module, generic BLE framework, legacy format compatibility, CTAP1 engine, iOS implementation or hybrid tunnel.

## Constitution Check

Design assessments below do not certify runtime acceptance.

| Principle | Initial review after clarification | Post-design assessment |
|---|---|---|
| I / II: security and derivation | Pairing policy and existing crypto obligations retained | Satisfied design: MITM permissions, generation/transaction ownership, erase-after-join discipline; no crypto/derivation replacement |
| III: architecture | Existing transport and handlers are HID-coupled | Satisfied design: shared unframed CTAP boundary; platform code in Android data layer; concrete bindings only in app |
| IV: event history | BLE is another delivery mechanism | Satisfied design: use existing deciders/stores; cancellation respects existing commit points; no destructive credential repair |
| V: rigor and evidence | Local FR IDs are not canonical @Req authorities | Satisfied design: task generation must include canonical BRD/TRD mapping and register synchronization before Tier 1 code; independent vectors, exact coverage and nonregression required |
| VI: latency/resources | Physical three-second gate removed | Satisfied design: host simulation <200ms, bounded buffers/notifications, measured advertising and memory/build budgets |
| VII: scope/docs | Spec retains explicit user-approved Android/platform constraints and protocol details | Satisfied design: implementation design lives here and in contracts; no speculative abstraction; checklist's three editorial items remain visible for later spec cleanup |
| VIII: Classic/accessibility | BLE augments the existing cross-platform obligation | Satisfied design: preserve Classic Windows/macOS/Linux behavior; accessible recovery/status and system pairing UI |

No constitutional exception is introduced. Hardware isolation and the existing shared authenticator boundary justify the focused CTAP interface under the constitutional mandate; do not build a generic transport framework. Feature-058's disabled hosted runner and operational waiver remain in force. Coverage floors remain Tier 1 >=60% instruction and applicable branch, Tier 2 >=20% instruction, repository >=50% instruction, plus nonregression and completeness. All implementation and physical evidence is Unverified.

## Project Structure

### Documentation

- plan.md: this plan
- research.md: selected dependency, exact source evidence and decisions
- data-model.md: connection, transaction, buffers and transitions
- contracts/fido-ble.md: wire, lifecycle and parent integration contracts
- quickstart.md: executable validation procedures
- tasks.md: generated subsequently by /speckit-tasks

### Source Code

Existing files affected:
- feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/Fido2Transport.kt
- feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/BluetoothHidTransportImpl.kt
- feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/CtapHidCommandDispatcher.kt
- feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/CtapHidRequestOwner.kt
- feature/fido2/src/androidMain/kotlin/com/chimali/fido2/ctap2/ handlers and Ctap2ResponseBuilder.kt
- feature/fido2/src/androidMain/kotlin/com/chimali/fido2/service/Fido2TransportService.kt
- feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/viewmodel/Fido2HomeViewModel.kt
- app/src/main/kotlin/com/chimali/ChimaliApplication.kt
- gradle/libs.versions.toml and feature/fido2/build.gradle.kts

Planned additions stay under the existing Android data/transport package: BleGattTransport, FidoBleFraming, BleSessionOwner, an Android GATT callback adapter, and the focused CTAP operation boundary. Tests belong in existing androidHostTest and androidDeviceTest source sets. Final filenames may follow adjacent conventions; contracts govern behavior.

## Design and Integration Sequence

1. Pin and resolve Nordic advertiser 1.3.1, inspect actual AAR/POM/minSdk and licenses, and compile under the unchanged toolchain. Probe authenticated GATT access and cancellation cleanup on a capable device. Failed verification is a recorded failure, not a silent toolchain/minSdk change.
2. Extract raw CTAP request/response handling from BluetoothHidTransportImpl and HID-framed handlers. Classic wraps the same raw responses in HID; BLE wraps them in MSG. Preserve operation-level behavior and existing latency instrumentation. Never synthesize a HID CID for BLE.
3. Implement bounded frame/session ownership and public GATT callback admission. Use Nordic advertising only after GATT service registration succeeds. Use one ordered event owner and one outstanding notification.
4. Evolve Fido2Transport's HID-specific state into neutral session state for both consumers. Keep connect/disconnect start-stop semantics; report BLE outbound connectDevice as unsupported rather than initiating a central connection.
5. Integrate through the parent's frozen process handle and lazy five-second activation contract. Parent work owns preferences, selection UI and final qualified factories. This part supplies the BLE factory, raw operation seam, neutral state and lifecycle behavior; coordinate shared file edits once.
6. Validate independent wire vectors, real handlers and Classic regression, then run the physical guide and constitutional assessments. Map all local requirements to canonical BRD/TRD IDs and @Req before implementation; generate tasks for that mapping rather than inventing unregistered IDs here.

## Complexity Tracking

No new module or constitutional violation. The native GATT supplement is deliberately limited to functionality the selected released library does not expose safely: admission/ATT responses, non-dropping receive ownership, security permissions and deterministic teardown. Nordic remains selected for advertising. A fork, v2 server dependency, duplicated authenticator engine or replacement library is not part of this design.
