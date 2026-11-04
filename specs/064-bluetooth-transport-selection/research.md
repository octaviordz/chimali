# Research: Bluetooth Transport Selection

**Date**: 2026-11-02. Read-only code inspection and primary documentation review; no runtime result claimed.

## Protocol and dependency

**Decision**: Use FIDO BLE as the canonical name and require its separate implementation before acceptance.

**Rationale**: Owner clarification excludes implementation from this feature. Repository inspection finds only BluetoothHidTransportImpl implementing Fido2Transport. The architecture analysis is a proposal, not implemented code. Fido2Transport currently exposes HidConnectionState and connectDevice(macAddress); the prerequisite must reconcile connection/discovery ownership, state mapping and cleanup for BLE before selection can use it.

**Alternatives considered**: HID over GATT is a different protocol rejected by clarification; hybrid transport expands scope; a fake/default placeholder fails acceptance; adding BLE here contradicts scope.

The [FIDO CTAP 2.2 specification §11.4](https://fidoalliance.org/specs/fido-v2.2-ps-20250714/fido-client-to-authenticator-protocol-v2.2-ps-20250714.html) defines BLE transport and encrypted pairing. This reference does not authorize a protocol upgrade; the prerequisite must state its supported revision and secure pairing behavior. [Microsoft WebAuthn documentation](https://learn.microsoft.com/en-us/windows/security/identity-protection/hello-for-business/webauthn-apis) describes roaming BLE authenticators mediated through Windows APIs. Chimali's remote role is a roaming authenticator, not Windows Hello's platform authenticator. These references do not prove this application on a particular Windows build/browser/adapter: require real registration and assertion in each mode.

## Storage

**Decision**: Extend UserPreferences with explicit transport code at tag 9 and timestamp at tag 10. Reuse the singleton DataStore and existing Fido2SettingsRepository.

**Rationale**: core/common UserPreferences occupies tags 1–8; its serializer uses ProtoBuf and reports corruption. Fido2SettingsRepositoryImpl already receives this store. New operations must be independent of its unrelated legacy credential-limit migration. Missing transport resolves to FIDO BLE without any old-default migration branch. Additive schema defaults preserve unrelated fields under constitutional rules.

**Alternatives considered**: Another preferences file or encrypted store adds infrastructure for nonsecret data; persisted active/pending flags become stale; whole-object replacement loses unrelated settings.

[DataStore guidance](https://developer.android.com/topic/libraries/architecture/datastore) places transactional updates in repositories and presentation behind ViewModels. The [DataStore API](https://developer.android.com/reference/androidx/datastore/core/DataStore) recommends data.first() for a snapshot. Wait for updateData completion before announcing success; corruption/I/O failure is not missing preference and must not reset the shared file.

## Startup and dependency injection

**Decision**: Resolve one asynchronous startup snapshot, gate transport use and preserve the chosen delegate for the whole process.

**Rationale**: ChimaliApplication loads Koin modules. Fido2Initializer currently initializes logging and crypto warm-up. Fido2TransportService and Fido2HomeViewModel both inject Fido2Transport. Classic's @Single auto-discovery in Fido2Module will compete with a second implementation unless binding is explicit. Start the snapshot before writes/use. Give the snapshot and each lazy activation attempt separate 5s safety deadlines measured from their respective start to completion or failure; cancellation fences late hardware start. A retry starts a new bounded attempt and retains any frozen process choice. Retain existing stricter product startup budgets.

**Alternatives considered**: Service-start reads prematurely apply pending settings; preference collection into DI hot-swaps live sessions; constructing both opens unnecessary hardware; main-thread blocking threatens startup budgets.

## Presentation

**Decision**: Extend stateless SettingsScreen and wire state/callbacks through AppNavGraph. A FIDO2 presentation ViewModel uses domain-facing repository/runtime contracts.

**Rationale**: Current settings only displays feature-selection labels and re-onboarding; the requested subsection does not exist and settingsModule is empty. Preference is data-owned, process state controller-owned, pending derived. Serialize saves and publish only after durability.

**Alternatives considered**: A new settings subsystem/module is unnecessary; direct UI storage duplicates state; automatic restart risks terminating ceremonies.

## Availability

**Decision**: Preserve selected-mode failure and expose actionable settings recovery, never automatic fallback. Distinguish unsupported capability from permission-required, Bluetooth-off and general startup failure.

**Rationale**: Owner chose explicit recovery. The [Android BluetoothAdapter reference](https://developer.android.com/reference/android/bluetooth/BluetoothAdapter) documents device-dependent advertising support and permissions. API 28 does not guarantee hardware peripheral support. The prerequisite owns GATT/advertising capability/resource details; this selector consumes typed outcomes. Permission/Bluetooth recovery can retry only the same process choice.

**Alternatives considered**: Fallback changes the explicit behavior; treating read errors as absence conceals faults; resetting shared preferences can damage unrelated configuration.

## Governance and verification

**Decision**: Retain Classic and all its existing platform obligations. Allocate stable canonical selector requirements before Tier 1 implementation; synchronize BRD/TRD/rules in the same branch.

**Rationale**: Constitution VIII and BRD FR-HID-010 name BluetoothHidDevice, and TRD hardware/journey contracts are HID-specific. This additive selector does not remove that capability or waive cross-platform duties. Replacing the mandate would need a separately approved constitutional amendment. New @Req annotations must reference canonical IDs, not feature-local FR-001–012. Windows compatibility and API-level hardware claims in earlier research remain assumptions pending device evidence.

**Alternatives considered**: Silent requirement replacement violates governance; mocked ceremonies cannot establish physical interoperability. Keep feature-058 hosted waiver and disabled runner intact.
